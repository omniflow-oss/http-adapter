package com.omniflow.ofkit.adapter.http.domain.model;

/** Marker interface for pipeline outcome. */
public interface Result {

    /** Successful outcome, typically a passthrough of the upstream response. */
    record Success(HttpResponse response, String ruleId) implements Result { }

    /** Failure outcome normalized as RFC-7807. */
    record Failure(ProblemDetails problem, String ruleId) implements Result { }
}

