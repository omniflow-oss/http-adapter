package com.omniflow.ofkit.adapter.http.domain.rules;

import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;

/** Minimal error rule with an ID, predicate and RFC-7807 problem template. */
public record ErrorRule(String id, ResponsePredicate when, ProblemDetails problem) { }

