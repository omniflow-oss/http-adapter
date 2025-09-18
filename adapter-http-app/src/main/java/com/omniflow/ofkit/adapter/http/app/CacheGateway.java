package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.cache.CacheKey;
import com.omniflow.ofkit.adapter.http.domain.cache.CachedEntry;
import com.omniflow.ofkit.adapter.http.domain.model.AdapterProfile;
import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.ports.CacheStore;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class CacheGateway {

    private final CacheStore cache;
    private final com.omniflow.ofkit.adapter.http.domain.ports.MetricsPort metrics;

    @Inject
    public CacheGateway(CacheStore cache, com.omniflow.ofkit.adapter.http.domain.ports.MetricsPort metrics) {
        this.cache = cache;
        this.metrics = metrics;
    }

    // Test convenience constructor when MetricsPort is not available
    public CacheGateway(CacheStore cache) {
        this.cache = cache;
        this.metrics = null;
    }

    public HttpResponse execute(AdapterProfile profile, HttpRequest request, HttpPort http) throws Exception {
        if (!"GET".equalsIgnoreCase(request.method())) {
            return http.execute(request);
        }

        var policy = profile.cachePolicy();
        if (policy == null || !policy.enabled()) {
            return http.execute(request);
        }

        CacheKey key = toKey(profile.id(), request, policy);
        Optional<CachedEntry> existing = cache.get(key);

        Map<String, List<String>> headers = new HashMap<>();
        if (request.headers() != null) {
            request.headers().forEach((k, vs) -> headers.put(k, List.copyOf(vs)));
        }

        if (existing.isPresent()) {
            var entry = existing.get();
            var now = Instant.now();
            var ttl = Math.max(0, policy.defaultTtlSeconds());
            var exp = entry.expiresAt() != null ? entry.expiresAt() : entry.storedAt().plusSeconds(ttl);
            boolean fresh = exp != null && now.isBefore(exp);
            if (fresh) {
                if (metrics != null) {
                    if (entry.response().statusCode() >= 400) metrics.incrementCacheNegative(profile.id());
                    else metrics.incrementCacheHit(profile.id());
                }
                return withHeader(entry.response(), "X-OF-Cache", "hit");
            }

            boolean withinSwr = policy.swrTtlSeconds() > 0 && exp != null && now.isBefore(exp.plusSeconds(policy.swrTtlSeconds()));
            if (withinSwr) {
                if (metrics != null) metrics.incrementCacheSwr(profile.id());
                // background revalidation (fire-and-forget)
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, List<String>> hdrs = new HashMap<>();
                        if (request.headers() != null) request.headers().forEach((k, vs) -> hdrs.put(k, List.copyOf(vs)));
                        if (policy.useEtag() && entry.etag() != null) hdrs.put("If-None-Match", List.of(entry.etag()));
                        if (policy.useLastModified() && entry.lastModified() != null) hdrs.put("If-Modified-Since", List.of(entry.lastModified()));
                        HttpRequest r2 = new HttpRequest(request.method(), request.uri(), hdrs, request.body());
                        HttpResponse re;
                        try {
                            re = http.execute(r2);
                        } catch (Exception e) {
                            return; // give up silently
                        }
                        var now2 = Instant.now();
                        var ttl2 = Math.max(0, policy.defaultTtlSeconds());
                        if (re.statusCode() == 304) {
                            CachedEntry renewed = new CachedEntry(entry.response(), now2, now2.plusSeconds(ttl2), entry.etag(), entry.lastModified());
                            cache.put(key, renewed);
                            if (metrics != null) metrics.incrementCacheRevalidate(profile.id());
                        } else if (re.statusCode() >= 200 && re.statusCode() <= 299) {
                            String etag2 = firstHeader(re.headers(), "ETag");
                            String lastMod2 = firstHeader(re.headers(), "Last-Modified");
                            CachedEntry updated = new CachedEntry(re, now2, now2.plusSeconds(ttl2), etag2, lastMod2);
                            cache.put(key, updated);
                        }
                    } catch (Throwable ignored) {}
                });
                if (metrics != null) {
                    if (entry.response().statusCode() >= 400) metrics.incrementCacheNegative(profile.id());
                    else metrics.incrementCacheSwr(profile.id());
                }
                return withHeader(entry.response(), "X-OF-Cache", "swr");
            }
            String etag = existing.get().etag();
            String lastMod = existing.get().lastModified();
            if (policy.useEtag() && etag != null) headers.put("If-None-Match", List.of(etag));
            if (policy.useLastModified() && lastMod != null) headers.put("If-Modified-Since", List.of(lastMod));
        }

        HttpRequest req2 = new HttpRequest(request.method(), request.uri(), headers, request.body());
        HttpResponse resp = http.execute(req2);

        if (existing.isPresent()) {
            var entry = existing.get();
            var now = Instant.now();
            var ttl = Math.max(0, policy.defaultTtlSeconds());
            if (resp.statusCode() == 304) {
                CachedEntry renewed = new CachedEntry(entry.response(), now, now.plusSeconds(ttl), entry.etag(), entry.lastModified());
                cache.put(key, renewed);
                if (metrics != null) metrics.incrementCacheRevalidate(profile.id());
                return withHeader(renewed.response(), "X-OF-Cache", "revalidate");
            }
            if (resp.statusCode() >= 500 && resp.statusCode() <= 599) {
                boolean withinSie = policy.sieTtlSeconds() > 0 && entry.expiresAt() != null && now.isBefore(entry.expiresAt().plusSeconds(policy.sieTtlSeconds()));
                if (withinSie) {
                    if (metrics != null) metrics.incrementCacheSie(profile.id());
                    return withHeader(entry.response(), "X-OF-Cache", "sie");
                }
            }
        }

        if (resp.statusCode() >= 200 && resp.statusCode() <= 299) {
            String etag = firstHeader(resp.headers(), "ETag");
            String lastMod = firstHeader(resp.headers(), "Last-Modified");
            var now = Instant.now();
            var ttl = Math.max(0, policy.defaultTtlSeconds());
            // respect max body size
            if (policy.maxBodyKb() <= 0 || resp.body() == null || (resp.body().length / 1024) <= policy.maxBodyKb()) {
                CachedEntry entry = new CachedEntry(resp, now, now.plusSeconds(ttl), etag, lastMod);
                cache.put(key, entry);
            }
        } else if (policy.negativeTtlSeconds() > 0 && resp.statusCode() >= 400 && resp.statusCode() != 304) {
            var now = Instant.now();
            if (policy.maxBodyKb() <= 0 || resp.body() == null || (resp.body().length / 1024) <= policy.maxBodyKb()) {
                CachedEntry entry = new CachedEntry(resp, now, now.plusSeconds(policy.negativeTtlSeconds()), null, null);
                cache.put(key, entry);
            }
        }
        if (metrics != null && existing.isEmpty()) metrics.incrementCacheMiss(profile.id());
        return resp;
    }

    private static CacheKey toKey(String profileId, HttpRequest req, com.omniflow.ofkit.adapter.http.domain.model.CachePolicy policy) {
        String path = req.uri().getPath();
        String query = req.uri().getRawQuery();
        String fullPath = (query == null || query.isEmpty()) ? path : path + "?" + query;
        Map<String, String> vary = new HashMap<>();
        if (policy != null && policy.varyHeaders() != null) {
            for (String h : policy.varyHeaders()) {
                String v = firstHeader(req.headers(), h);
                if (v != null) vary.put(h.toLowerCase(Locale.ROOT), v);
            }
        }
        return new CacheKey(profileId, req.method().toUpperCase(Locale.ROOT), fullPath, vary, null);
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        List<String> values = headers.get(name);
        if (values == null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    values = e.getValue();
                    break;
                }
            }
        }
        if (values == null || values.isEmpty()) return null;
        return values.get(0);
    }

    private static HttpResponse withHeader(HttpResponse resp, String name, String value) {
        Map<String, List<String>> h = new HashMap<>();
        if (resp.headers() != null) resp.headers().forEach((k, v) -> h.put(k, List.copyOf(v)));
        h.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(value);
        return new HttpResponse(resp.statusCode(), h, resp.body());
    }
}
