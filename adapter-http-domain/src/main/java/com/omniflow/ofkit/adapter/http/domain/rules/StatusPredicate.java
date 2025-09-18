package com.omniflow.ofkit.adapter.http.domain.rules;

public record StatusPredicate(int minInclusive, int maxInclusive) implements ResponsePredicate {
    @Override
    public boolean test(ResponseContext ctx) {
        int s = ctx.status();
        return s >= minInclusive && s <= maxInclusive;
    }
}

