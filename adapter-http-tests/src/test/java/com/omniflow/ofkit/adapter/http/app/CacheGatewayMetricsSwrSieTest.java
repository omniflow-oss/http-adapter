package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.CacheStore;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.ports.MetricsPort;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CacheGatewayMetricsSwrSieTest {
    static class MemStore implements CacheStore {
        Map<com.omniflow.ofkit.adapter.http.domain.cache.CacheKey, com.omniflow.ofkit.adapter.http.domain.cache.CachedEntry> m = new HashMap<>();
        @Override public Optional<com.omniflow.ofkit.adapter.http.domain.cache.CachedEntry> get(com.omniflow.ofkit.adapter.http.domain.cache.CacheKey key) { return Optional.ofNullable(m.get(key)); }
        @Override public void put(com.omniflow.ofkit.adapter.http.domain.cache.CacheKey key, com.omniflow.ofkit.adapter.http.domain.cache.CachedEntry entry) { m.put(key, entry); }
        @Override public void invalidate(com.omniflow.ofkit.adapter.http.domain.cache.CacheKey key) { m.remove(key); }
    }
    static class MemMetrics implements MetricsPort {
        int swr, sie, reval;
        public void incrementCacheHit(String p){}
        public void incrementCacheMiss(String p){}
        public void incrementCacheSwr(String p){swr++;}
        public void incrementCacheSie(String p){sie++;}
        public void incrementCacheRevalidate(String p){reval++;}
        public void incrementCacheNegative(String p){}
        public void incrementCacheEviction(String p){}
    }

    @Test
    void swr_and_revalidate_metrics_increment() throws Exception {
        CachePolicy pol = new CachePolicy(true, 0, 60, 0, true, true, List.of("accept"), 1024, 0);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None());
        MemStore store = new MemStore();
        MemMetrics metrics = new MemMetrics();
        CacheGateway gw = new CacheGateway(store, metrics);

        final int[] calls = {0};
        HttpPort http = req -> {
            calls[0]++;
            if (calls[0] == 1) return new HttpResponse(200, Map.of("ETag", List.of("\"abc\"")), new byte[0]);
            return new HttpResponse(304, Map.of(), new byte[0]);
        };

        HttpRequest r = new HttpRequest("GET", URI.create("http://x/swr"), Map.of("accept", List.of("application/json")), null);
        gw.execute(profile, r, http); // prime cache (expired immediately)
        HttpResponse stale = gw.execute(profile, r, http); // SWR path
        assertEquals("swr", first(stale.headers(), "X-OF-Cache"));
        assertTrue(metrics.swr >= 1);
        // allow background revalidation to run
        Thread.sleep(150);
        assertTrue(metrics.reval >= 1);
    }

    @Test
    void sie_metric_incremented_on_5xx_after_expiry() throws Exception {
        CachePolicy pol = new CachePolicy(true, 0, 0, 60, true, true, List.of("accept"), 1024, 0);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None());
        MemStore store = new MemStore();
        MemMetrics metrics = new MemMetrics();
        CacheGateway gw = new CacheGateway(store, metrics);
        final int[] calls = {0};
        HttpPort http = req -> {
            calls[0]++;
            if (calls[0] == 1) return new HttpResponse(200, Map.of(), new byte[0]);
            return new HttpResponse(500, Map.of(), new byte[0]);
        };
        HttpRequest r = new HttpRequest("GET", URI.create("http://x/sie"), Map.of("accept", List.of("application/json")), null);
        gw.execute(profile, r, http);
        HttpResponse sie = gw.execute(profile, r, http);
        assertEquals("sie", first(sie.headers(), "X-OF-Cache"));
        assertTrue(metrics.sie >= 1);
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}

