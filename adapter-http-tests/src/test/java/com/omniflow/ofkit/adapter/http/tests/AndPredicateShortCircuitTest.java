package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AndPredicateShortCircuitTest {
    @Test
    void returns_false_when_any_inner_fails() {
        ResponsePredicate p1 = ctx -> true;
        ResponsePredicate p2 = ctx -> false;
        AndPredicate and = new AndPredicate(List.of(p1, p2));
        var ctx = new ResponseContext(new HttpResponse(200, Map.of(), new byte[0]));
        assertFalse(and.test(ctx));
    }
}

