package com.omniflow.ofkit.adapter.http.domain.model;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record HttpRequest(
        String method,
        URI uri,
        Map<String, List<String>> headers,
        byte[] body
) {
}

