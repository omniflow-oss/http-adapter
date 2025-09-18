package com.omniflow.ofkit.adapter.http.domain.ports;

public interface MetricsPort {
    void incrementCacheHit(String profileId);
    void incrementCacheMiss(String profileId);
    void incrementCacheSwr(String profileId);
    void incrementCacheSie(String profileId);
    void incrementCacheRevalidate(String profileId);
    void incrementCacheNegative(String profileId);
    void incrementCacheEviction(String profileId);
}
