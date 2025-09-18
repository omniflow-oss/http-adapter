package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import com.omniflow.ofkit.adapter.http.domain.model.Result;
import com.omniflow.ofkit.adapter.http.domain.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineErrorMatchTest {
    @Test
    void error_rule_matches_when_success_does_not() {
        RuleEngine engine = new RuleEngine();
        SuccessRule okStatusButNoMatch = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ErrorRule err429 = new ErrorRule("err-429", new StatusPredicate(429, 429), ProblemDetails.of("about:blank","rate",429, "limited"));
        HttpResponse resp = new HttpResponse(429, Map.of(), new byte[0]);
        Result r = engine.evaluate(new ResponseContext(resp), List.of(okStatusButNoMatch), List.of(err429), ProblemDetails.of("about:blank","generic",502, ""));
        assertInstanceOf(Result.Failure.class, r);
        assertEquals("err-429", ((Result.Failure) r).ruleId());
        assertEquals(429, ((Result.Failure) r).problem().status());
    }
}

