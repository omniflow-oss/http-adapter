package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.cache.CacheKey;
import com.omniflow.ofkit.adapter.http.domain.cache.CachedEntry;
import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.CacheStore;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.ports.MetricsPort;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CacheGatewayMetricsTest {
    static class MemStore implements CacheStore {
        Map<CacheKey, CachedEntry> m = new HashMap<>();
        @Override public Optional<CachedEntry> get(CacheKey key) { return Optional.ofNullable(m.get(key)); }
        @Override public void put(CacheKey key, CachedEntry entry) { m.put(key, entry); }
        @Override public void invalidate(CacheKey key) { m.remove(key); }
    }
    static class MemMetrics implements MetricsPort {
        int hits, misses, swr, sie, reval, neg, evict;
        public void incrementCacheHit(String p){hits++;}
        public void incrementCacheMiss(String p){misses++;}
        public void incrementCacheSwr(String p){swr++;}
        public void incrementCacheSie(String p){sie++;}
        public void incrementCacheRevalidate(String p){reval++;}
        public void incrementCacheNegative(String p){neg++;}
        public void incrementCacheEviction(String p){evict++;}
    }

    @Test
    void metrics_are_incremented() throws Exception {
        CachePolicy pol = new CachePolicy(true, 60, 0, 0, true, true, List.of("accept"), 1024, 30);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None());
        MemStore store = new MemStore();
        MemMetrics metrics = new MemMetrics();
        CacheGateway gw = new CacheGateway(store, metrics);

        final int[] calls = {0};
        HttpPort http = req -> {
            calls[0]++;
            if (calls[0] == 1) return new HttpResponse(404, Map.of(), new byte[0]);
            return new HttpResponse(200, Map.of("ETag", List.of("\"x\"")), new byte[0]);
        };

        HttpRequest r = new HttpRequest("GET", URI.create("http://x/r"), Map.of(), null);
        gw.execute(profile, r, http); // negative cached
        gw.execute(profile, r, http); // negative hit
        assertEquals(1, metrics.neg);

        // now prime a positive cache entry
        // use different vary header to bypass negative cache key
        HttpRequest r2 = new HttpRequest("GET", URI.create("http://x/r"), Map.of("accept", List.of("application/json")), null);
        HttpResponse ok = gw.execute(profile, r2, http);
        assertEquals(200, ok.statusCode());
        HttpResponse hit = gw.execute(profile, r2, http);
        assertEquals(200, hit.statusCode());
        assertTrue(metrics.hits >= 1);
        assertTrue(metrics.misses >= 1);
    }
}
