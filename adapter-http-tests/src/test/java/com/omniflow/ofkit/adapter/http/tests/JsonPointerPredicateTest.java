package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.JsonPointerPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonPointerPredicateTest {
    @Test
    void equals_regex_exists() {
        String body = "{\"a\":{\"b\":\"Hello\"}}";
        HttpResponse resp = new HttpResponse(200, Map.of(), body.getBytes(StandardCharsets.UTF_8));
        ResponseContext ctx = new ResponseContext(resp);
        assertTrue(JsonPointerPredicate.equalsAt("/a/b", "Hello").test(ctx));
        assertTrue(JsonPointerPredicate.matchesAt("/a/b", "^H.*").test(ctx));
        assertTrue(JsonPointerPredicate.existsAt("/a").test(ctx));
        assertFalse(JsonPointerPredicate.existsAt("/c").test(ctx));
    }
}

