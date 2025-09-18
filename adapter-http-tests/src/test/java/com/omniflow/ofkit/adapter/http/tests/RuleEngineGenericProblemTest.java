package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import com.omniflow.ofkit.adapter.http.domain.model.Result;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;
import com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineGenericProblemTest {
    @Test
    void falls_back_to_generic_problem_if_no_rule_matches() {
        HttpResponse resp = new HttpResponse(404, Map.of(), new byte[0]);
        ResponseContext ctx = new ResponseContext(resp);
        RuleEngine engine = new RuleEngine();
        var r = engine.evaluate(ctx, List.of(), List.of(), ProblemDetails.of("about:blank","Service error",502, ""));
        assertInstanceOf(Result.Failure.class, r);
        var f = (Result.Failure) r;
        assertEquals(502, f.problem().status());
        assertEquals("generic_problem", f.ruleId());
    }
}

