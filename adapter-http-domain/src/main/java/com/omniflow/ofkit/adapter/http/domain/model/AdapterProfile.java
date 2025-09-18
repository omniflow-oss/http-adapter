package com.omniflow.ofkit.adapter.http.domain.model;

import com.omniflow.ofkit.adapter.http.domain.rules.ErrorRule;
import com.omniflow.ofkit.adapter.http.domain.rules.SuccessRule;

import java.util.List;
import java.util.Objects;

/** Minimal profile aggregate required by the RuleEngine. */
public record AdapterProfile(
        String id,
        String baseUrl,
        List<SuccessRule> successRules,
        List<ErrorRule> errorRules,
        ProblemDetails genericProblem,
        CachePolicy cachePolicy,
        RetrySpec retrySpec,
        HttpClientSpec httpSpec,
        AuthSpec authSpec
) {
    public AdapterProfile {
        Objects.requireNonNull(id, "id");
        successRules = successRules == null ? List.of() : List.copyOf(successRules);
        errorRules = errorRules == null ? List.of() : List.copyOf(errorRules);
        Objects.requireNonNull(genericProblem, "genericProblem");
    }
}
