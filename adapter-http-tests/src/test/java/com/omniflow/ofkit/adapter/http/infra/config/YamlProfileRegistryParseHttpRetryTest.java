package com.omniflow.ofkit.adapter.http.infra.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec;
import com.omniflow.ofkit.adapter.http.domain.model.RetrySpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileRegistryParseHttpRetryTest {
    @Test
    void parse_http_and_retry_nodes() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode timeouts = om.readTree("{\"connect_ms\":123,\"read_ms\":456}");
        JsonNode ssl = om.readTree("{\"insecure\":true}");
        JsonNode pool = om.readTree("{\"max_pool_size\":7,\"max_wait_queue\":3,\"keep_alive\":false,\"keep_alive_timeout_s\":9}");
        JsonNode retry = om.readTree("{\"enabled\":true,\"max_retries\":2,\"initial_delay_ms\":50,\"max_delay_ms\":200,\"jitter\":false,\"respect_retry_after\":true,\"idempotent_only\":false}");

        Method parseHttp = YamlProfileRegistry.class.getDeclaredMethod("parseHttp", JsonNode.class, JsonNode.class, JsonNode.class);
        parseHttp.setAccessible(true);
        HttpClientSpec http = (HttpClientSpec) parseHttp.invoke(null, timeouts, ssl, pool);
        assertEquals(123, http.connectTimeoutMs());
        assertEquals(456, http.readTimeoutMs());
        assertTrue(http.ssl().insecure());
        assertEquals(7, http.maxPoolSize());
        assertEquals(3, http.maxWaitQueueSize());
        assertFalse(http.keepAlive());
        assertEquals(9, http.keepAliveTimeoutSeconds());

        Method parseRetry = YamlProfileRegistry.class.getDeclaredMethod("parseRetry", JsonNode.class);
        parseRetry.setAccessible(true);
        RetrySpec rs = (RetrySpec) parseRetry.invoke(null, retry);
        assertTrue(rs.enabled());
        assertEquals(2, rs.maxRetries());
        assertEquals(50, rs.initialDelayMs());
        assertEquals(200, rs.maxDelayMs());
        assertFalse(rs.jitter());
        assertTrue(rs.respectRetryAfter());
        assertFalse(rs.idempotentOnly());
    }
}

