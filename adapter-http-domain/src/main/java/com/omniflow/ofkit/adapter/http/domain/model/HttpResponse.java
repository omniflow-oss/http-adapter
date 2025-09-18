package com.omniflow.ofkit.adapter.http.domain.model;

import java.util.List;
import java.util.Map;

public record HttpResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body
) {
}

