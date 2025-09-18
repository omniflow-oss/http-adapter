package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.HeaderRegexPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeaderRegexPredicateTest {
    @Test
    void matches_case_insensitive_header_name() {
        HttpResponse resp = new HttpResponse(200, Map.of("x-flag", List.of("OK")), new byte[0]);
        ResponseContext ctx = new ResponseContext(resp);
        assertTrue(new HeaderRegexPredicate("X-Flag", "^OK$").test(ctx));
        assertFalse(new HeaderRegexPredicate("X-Flag", "^NO$").test(ctx));
    }
}

