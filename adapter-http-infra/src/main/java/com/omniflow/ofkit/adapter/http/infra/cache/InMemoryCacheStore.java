package com.omniflow.ofkit.adapter.http.infra.cache;

import com.omniflow.ofkit.adapter.http.domain.cache.CacheKey;
import com.omniflow.ofkit.adapter.http.domain.cache.CachedEntry;
import com.omniflow.ofkit.adapter.http.domain.ports.CacheStore;
import com.omniflow.ofkit.adapter.http.domain.ports.MetricsPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public final class InMemoryCacheStore implements CacheStore {
    private final LruMap map;

    public InMemoryCacheStore() {
        this.map = new LruMap(1000, null);
    }

    @Inject
    public InMemoryCacheStore(MetricsPort metrics) {
        this.map = new LruMap(1000, metrics);
    }

    @Override
    public Optional<CachedEntry> get(CacheKey key) {
        synchronized (map) {
            return Optional.ofNullable(map.get(key));
        }
    }

    @Override
    public void put(CacheKey key, CachedEntry entry) {
        synchronized (map) {
            map.put(key, entry);
        }
    }

    @Override
    public void invalidate(CacheKey key) {
        synchronized (map) {
            map.remove(key);
        }
    }

    static final class LruMap extends LinkedHashMap<CacheKey, CachedEntry> {
        private final int capacity;
        private final MetricsPort metrics;
        LruMap(int capacity, MetricsPort metrics) {
            super(16, 0.75f, true);
            this.capacity = capacity;
            this.metrics = metrics;
        }
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, CachedEntry> eldest) {
            boolean evict = size() > capacity;
            if (evict && metrics != null) metrics.incrementCacheEviction(eldest.getKey().profile());
            return evict;
        }
    }
}
