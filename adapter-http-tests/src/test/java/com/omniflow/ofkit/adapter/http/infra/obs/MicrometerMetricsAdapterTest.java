package com.omniflow.ofkit.adapter.http.infra.obs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class MicrometerMetricsAdapterTest {
    @Test
    void increments_counters_with_profile_tag() throws Exception {
        MeterRegistry reg = new SimpleMeterRegistry();
        MicrometerMetricsAdapter adapter = new MicrometerMetricsAdapter();
        Field f = MicrometerMetricsAdapter.class.getDeclaredField("registry");
        f.setAccessible(true);
        f.set(adapter, reg);

        adapter.incrementCacheHit("p1");
        adapter.incrementCacheMiss("p1");
        adapter.incrementCacheSwr("p1");
        adapter.incrementCacheSie("p1");
        adapter.incrementCacheRevalidate("p1");
        adapter.incrementCacheNegative("p1");
        adapter.incrementCacheEviction("p1");

        assertTrue(reg.counter("omniflow_adapter_cache_hits_total", "profile", "p1").count() > 0.0);
        assertTrue(reg.counter("omniflow_adapter_cache_misses_total", "profile", "p1").count() > 0.0);
        assertTrue(reg.counter("omniflow_adapter_cache_swr_total", "profile", "p1").count() > 0.0);
        assertTrue(reg.counter("omniflow_adapter_cache_sie_total", "profile", "p1").count() > 0.0);
        assertTrue(reg.counter("omniflow_adapter_cache_revalidate_total", "profile", "p1").count() > 0.0);
        assertTrue(reg.counter("omniflow_adapter_cache_negative_total", "profile", "p1").count() > 0.0);
        assertTrue(reg.counter("omniflow_adapter_cache_evictions_total", "profile", "p1").count() > 0.0);
    }
}

