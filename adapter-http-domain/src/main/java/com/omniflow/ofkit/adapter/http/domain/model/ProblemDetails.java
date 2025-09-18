package com.omniflow.ofkit.adapter.http.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * RFC-7807 Problem Details representation (domain-pure).
 */
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Map<String, Object> extensions
) {
    public ProblemDetails {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(type, "type");
        if (extensions == null) {
            extensions = Map.of();
        }
    }

    public static ProblemDetails of(String type, String title, int status, String detail) {
        return new ProblemDetails(type, title, status, detail, null, Map.of());
    }
}

