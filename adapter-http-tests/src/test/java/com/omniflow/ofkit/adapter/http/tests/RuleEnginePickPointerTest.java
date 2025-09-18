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

class RuleEnginePickPointerTest {

    @Test
    void picks_subtree_when_rule_has_pick_pointer() {
        String body = "{\"status\":\"OK\",\"data\":{\"id\":\"42\"}}";
        HttpResponse resp = new HttpResponse(200, Map.of("content-type", List.of("application/json")), body.getBytes(StandardCharsets.UTF_8));
        ResponseContext ctx = new ResponseContext(resp);

        ResponsePredicate okFlag = new AndPredicate(List.of(new StatusPredicate(200, 299), JsonPointerPredicate.equalsAt("/status", "OK")));
        SuccessRule rule = new SuccessRule("ok-json", okFlag, "/data");
        RuleEngine engine = new RuleEngine();
        Result r = engine.evaluate(ctx, List.of(rule), List.of(), ProblemDetails.of("about:blank","g",502, ""));
        assertInstanceOf(Result.Success.class, r);
        String out = new String(((Result.Success) r).response().body(), StandardCharsets.UTF_8);
        assertEquals("{\"id\":\"42\"}", out);
    }
}

