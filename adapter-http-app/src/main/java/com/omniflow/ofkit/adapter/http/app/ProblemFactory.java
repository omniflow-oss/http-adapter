package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;

import java.util.Map;

public final class ProblemFactory {
    public ProblemDetails generic(String type, String title, int status, String detail) {
        return new ProblemDetails(type, title, status, detail, null, Map.of());
    }
}

