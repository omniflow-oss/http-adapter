package com.omniflow.ofkit.adapter.http.infra.http;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpHeaderUtilsTest {
    @Test
    void hasHeaderTrue_handles_null_and_case_insensitive() {
        assertFalse(HttpHeaderUtils.hasHeaderTrue(null, "X-Flag"));
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("x-flag", List.of("true"));
        assertTrue(HttpHeaderUtils.hasHeaderTrue(headers, "X-FLAG"));
    }

    @Test
    void parseLongHeader_returns_default_for_missing_and_invalid() {
        Map<String, List<String>> headers = Map.of("X-Num", List.of("abc"));
        assertEquals(7L, HttpHeaderUtils.parseLongHeader(headers, "X-Other", 7L));
        assertEquals(5L, HttpHeaderUtils.parseLongHeader(headers, "X-Num", 5L));
    }
}

