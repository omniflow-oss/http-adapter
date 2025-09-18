package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.JsonPointerPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonPointerRegexSpecialsTest {
    @Test
    void regex_with_special_characters_matches() {
        String body = "{\"a\":{\"b\":\"Hello+World.\"}}";
        HttpResponse resp = new HttpResponse(200, Map.of(), body.getBytes(StandardCharsets.UTF_8));
        ResponseContext ctx = new ResponseContext(resp);
        assertTrue(JsonPointerPredicate.matchesAt("/a/b", "^H.llo\\+World\\.$").test(ctx));
    }
}

