package com.omniflow.ofkit.adapter.http.infra.http;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestClientReactiveAdapterHelpersTest {
    @Test
    void parse_headers_utilities() {
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-Flag", List.of("true"));
        headers.put("X-Num", List.of("123"));

        boolean flag = HttpHeaderUtils.hasHeaderTrue(headers, "x-flag");
        long num = HttpHeaderUtils.parseLongHeader(headers, "X-Num", 5L);
        long dflt = HttpHeaderUtils.parseLongHeader(headers, "X-Missing", 7L);

        assertTrue(flag);
        assertEquals(123L, num);
        assertEquals(7L, dflt);
    }
}
