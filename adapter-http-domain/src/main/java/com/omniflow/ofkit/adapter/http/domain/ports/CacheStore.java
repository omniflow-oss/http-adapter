package com.omniflow.ofkit.adapter.http.domain.ports;

import com.omniflow.ofkit.adapter.http.domain.cache.CacheKey;
import com.omniflow.ofkit.adapter.http.domain.cache.CachedEntry;

import java.util.Optional;

public interface CacheStore {
    Optional<CachedEntry> get(CacheKey key);
    void put(CacheKey key, CachedEntry entry);
    void invalidate(CacheKey key);
}

