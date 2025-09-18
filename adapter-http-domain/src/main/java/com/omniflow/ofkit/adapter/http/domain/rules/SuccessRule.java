package com.omniflow.ofkit.adapter.http.domain.rules;

/** Minimal success rule with an ID and predicate. */
public record SuccessRule(String id, ResponsePredicate when, String pickPointer) { }

