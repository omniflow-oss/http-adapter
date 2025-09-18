package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import com.omniflow.ofkit.adapter.http.domain.model.Result;
import com.omniflow.ofkit.adapter.http.domain.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineSmokeTest {

    @Test
    void success_then_passthrough() {
        RuleEngine engine = new RuleEngine();
        SuccessRule ok2xx = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ErrorRule errAny = new ErrorRule("err-any", ctx -> true, ProblemDetails.of("about:blank", "err", 500, "boom"));
        HttpResponse resp = new HttpResponse(200, Map.of(), new byte[0]);
        Result r = engine.evaluate(new ResponseContext(resp), List.of(ok2xx), List.of(errAny), ProblemDetails.of("about:blank","generic",502, ""));
        assertInstanceOf(Result.Success.class, r);
        assertEquals("ok-2xx", ((Result.Success) r).ruleId());
    }
}

