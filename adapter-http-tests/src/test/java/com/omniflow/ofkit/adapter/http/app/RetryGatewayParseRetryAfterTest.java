package com.omniflow.ofkit.adapter.http.app;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class RetryGatewayParseRetryAfterTest {

    @Test
    void parses_delta_seconds_and_http_date() throws Exception {
        Method m = RetryGateway.class.getDeclaredMethod("parseRetryAfter", String.class);
        m.setAccessible(true);
        // Delta seconds
        Long ms = (Long) m.invoke(null, "2");
        assertNotNull(ms);
        assertTrue(ms >= 2000 && ms <= 2000); // exactly 2s

        // HTTP-date (RFC 1123); set to 1s in the future
        String httpDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now().plusSeconds(1));
        Long fromDate = (Long) m.invoke(null, httpDate);
        assertNotNull(fromDate);
        assertTrue(fromDate >= 0);
    }
}

