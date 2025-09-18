package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import com.omniflow.ofkit.adapter.http.domain.model.Result;
import com.omniflow.ofkit.adapter.http.domain.rules.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineAllPredicatesTest {
    @Test
    void complex_all_predicates_must_all_match() {
        String body = "{\"status\":\"OK\",\"data\":{\"id\":\"42\"}} hello-xyz";
        HttpResponse resp = new HttpResponse(200, Map.of("X-Env", List.of("prod")), body.getBytes(StandardCharsets.UTF_8));
        ResponseContext ctx = new ResponseContext(resp);

        ResponsePredicate all = new AndPredicate(List.of(
                new StatusPredicate(200, 299),
                new HeaderRegexPredicate("x-env", "prod"),
                new BodyRegexPredicate("hello-\\w+"),
                JsonPointerPredicate.equalsAt("/status", "OK")
        ));
        SuccessRule rule = new SuccessRule("ok-all", all, null);
        RuleEngine engine = new RuleEngine();
        Result r = engine.evaluate(ctx, List.of(rule), List.of(), ProblemDetails.of("about:blank","g",502, ""));
        assertInstanceOf(Result.Success.class, r);
        assertEquals("ok-all", ((Result.Success) r).ruleId());
    }

    @Test
    void missing_pick_pointer_leaves_body_unchanged_and_generic_on_no_match() {
        String body = "{\"status\":\"KO\",\"data\":{\"id\":\"42\"}}";
        HttpResponse resp = new HttpResponse(200, Map.of(), body.getBytes(StandardCharsets.UTF_8));
        ResponseContext ctx = new ResponseContext(resp);
        SuccessRule rule = new SuccessRule("ok-json", new StatusPredicate(200, 299), "/missing");
        RuleEngine engine = new RuleEngine();
        Result r = engine.evaluate(ctx, List.of(rule), List.of(), ProblemDetails.of("about:blank","g",502, ""));
        // Status matches but pick_pointer missing => body stays same and still success because predicate matched
        assertInstanceOf(Result.Success.class, r);
        String out = new String(((Result.Success) r).response().body(), StandardCharsets.UTF_8);
        assertEquals(body, out);
    }
}

