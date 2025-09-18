package com.omniflow.ofkit.adapter.http.infra.rest.dto;

import java.util.Map;

public record ProblemDetailsDto(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Map<String, Object> extensions
) {}

