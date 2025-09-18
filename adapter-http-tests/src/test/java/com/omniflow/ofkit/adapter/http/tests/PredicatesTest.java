package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PredicatesTest {
    @Test
    void body_regex_matches() {
        HttpResponse r = new HttpResponse(200, Map.of(), "hello 123".getBytes(StandardCharsets.UTF_8));
        ResponseContext ctx = new ResponseContext(r);
        assertTrue(new BodyRegexPredicate("\\d+").test(ctx));
        assertFalse(new BodyRegexPredicate("[A-Z]{5}").test(ctx));
    }
}

