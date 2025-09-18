package com.omniflow.ofkit.adapter.http.domain.rules;

import java.util.List;

public final class AndPredicate implements ResponsePredicate {
    private final List<ResponsePredicate> predicates;

    public AndPredicate(List<ResponsePredicate> predicates) {
        this.predicates = List.copyOf(predicates);
    }

    @Override
    public boolean test(ResponseContext ctx) {
        for (ResponsePredicate p : predicates) {
            if (!p.test(ctx)) return false;
        }
        return true;
    }
}

