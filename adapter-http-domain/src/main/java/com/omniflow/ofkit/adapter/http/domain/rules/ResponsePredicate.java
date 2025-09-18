package com.omniflow.ofkit.adapter.http.domain.rules;

@FunctionalInterface
public interface ResponsePredicate {
    boolean test(ResponseContext ctx);
}

