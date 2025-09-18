package com.omniflow.ofkit.adapter.http.infra.cache;

import com.omniflow.ofkit.adapter.http.domain.cache.CacheKey;
import com.omniflow.ofkit.adapter.http.domain.cache.CachedEntry;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.ports.MetricsPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCacheStoreTest {
    static class CountingMetrics implements MetricsPort {
        int evictions;
        @Override public void incrementCacheHit(String profileId) {}
        @Override public void incrementCacheMiss(String profileId) {}
        @Override public void incrementCacheSwr(String profileId) {}
        @Override public void incrementCacheSie(String profileId) {}
        @Override public void incrementCacheRevalidate(String profileId) {}
        @Override public void incrementCacheNegative(String profileId) {}
        @Override public void incrementCacheEviction(String profileId) { evictions++; }
    }
    @Test
    void lru_map_evicts_old_entries_and_counts() {
        CountingMetrics m = new CountingMetrics();
        InMemoryCacheStore.LruMap map = new InMemoryCacheStore.LruMap(3, m);
        for (int i = 0; i < 4; i++) {
            CacheKey k = new CacheKey("p", "GET", "/"+i, Map.of(), null);
            HttpResponse r = new HttpResponse(200, Map.of(), new byte[0]);
            map.put(k, new CachedEntry(r, Instant.now(), Instant.now().plusSeconds(1), null, null));
        }
        assertEquals(3, map.size());
        assertEquals(1, m.evictions);
    }
}

