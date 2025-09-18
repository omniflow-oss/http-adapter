package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RetryGatewayBackoffTest {

    @Test
    void compute_delay_exponential_and_jitter_bounds() throws Exception {
        Method m = RetryGateway.class.getDeclaredMethod("computeDelayMs", com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.class, int.class, com.omniflow.ofkit.adapter.http.domain.model.HttpResponse.class);
        m.setAccessible(true);
        var specNoJitter = new com.omniflow.ofkit.adapter.http.domain.model.RetrySpec(true, 3, 100, 1000, false, false, true);
        long d1 = (Long) m.invoke(null, specNoJitter, 1, null);
        long d2 = (Long) m.invoke(null, specNoJitter, 2, null);
        long d3 = (Long) m.invoke(null, specNoJitter, 3, null);
        assertEquals(100, d1);
        assertEquals(200, d2);
        assertEquals(400, d3);

        var specJitter = new com.omniflow.ofkit.adapter.http.domain.model.RetrySpec(true, 3, 100, 300, true, false, true);
        for (int i = 1; i <= 3; i++) {
            long d = (Long) m.invoke(null, specJitter, i, null);
            long base = Math.min(100L * (1L << Math.max(0, i - 1)), 300L);
            assertTrue(d >= 0 && d <= base, "jitter delay within [0, base]");
        }
    }

    @Test
    void compute_delay_respects_retry_after_min() throws Exception {
        Method m = RetryGateway.class.getDeclaredMethod("computeDelayMs", com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.class, int.class, com.omniflow.ofkit.adapter.http.domain.model.HttpResponse.class);
        m.setAccessible(true);
        var spec = new com.omniflow.ofkit.adapter.http.domain.model.RetrySpec(true, 1, 1000, 5000, false, true, true);
        var resp = new HttpResponse(503, Map.of("Retry-After", List.of("1")), new byte[0]);
        long d = (Long) m.invoke(null, spec, 1, resp);
        assertTrue(d <= 5000 && d >= 1000, "uses retry-after but bounded by maxDelay");
    }

    @Test
    void compute_delay_ignores_retry_after_when_disabled() throws Exception {
        Method m = RetryGateway.class.getDeclaredMethod("computeDelayMs", com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.class, int.class, com.omniflow.ofkit.adapter.http.domain.model.HttpResponse.class);
        m.setAccessible(true);
        var spec = new com.omniflow.ofkit.adapter.http.domain.model.RetrySpec(true, 1, 100, 1000, false, false, true);
        var resp = new HttpResponse(503, Map.of("Retry-After", List.of("5")), new byte[0]);
        long d = (Long) m.invoke(null, spec, 1, resp);
        assertEquals(100, d, "should use base backoff when respect_retry_after=false");
    }
}
