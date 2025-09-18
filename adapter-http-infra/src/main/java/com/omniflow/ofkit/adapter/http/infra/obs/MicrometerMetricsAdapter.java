package com.omniflow.ofkit.adapter.http.infra.obs;

import com.omniflow.ofkit.adapter.http.domain.ports.MetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MicrometerMetricsAdapter implements MetricsPort {
    private static final Logger LOG = Logger.getLogger(MicrometerMetricsAdapter.class);

    @Inject
    MeterRegistry registry;

    private void inc(String name, String profile) {
        registry.counter(name, "profile", profile).increment();
        LOG.debugf("metric=%s profile=%s", name, profile);
    }

    @Override
    public void incrementCacheHit(String profileId) { inc("omniflow_adapter_cache_hits_total", profileId); }
    @Override
    public void incrementCacheMiss(String profileId) { inc("omniflow_adapter_cache_misses_total", profileId); }
    @Override
    public void incrementCacheSwr(String profileId) { inc("omniflow_adapter_cache_swr_total", profileId); }
    @Override
    public void incrementCacheSie(String profileId) { inc("omniflow_adapter_cache_sie_total", profileId); }
    @Override
    public void incrementCacheRevalidate(String profileId) { inc("omniflow_adapter_cache_revalidate_total", profileId); }
    @Override
    public void incrementCacheNegative(String profileId) { inc("omniflow_adapter_cache_negative_total", profileId); }
    @Override
    public void incrementCacheEviction(String profileId) { inc("omniflow_adapter_cache_evictions_total", profileId); }
}
