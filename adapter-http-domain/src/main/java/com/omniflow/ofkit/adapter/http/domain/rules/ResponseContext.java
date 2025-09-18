package com.omniflow.ofkit.adapter.http.domain.rules;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Snapshot of the upstream HTTP response for predicate evaluation. */
public final class ResponseContext {
    private final int status;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    public ResponseContext(HttpResponse response) {
        this.status = response.statusCode();
        this.headers = response.headers();
        this.body = response.body();
    }

    public int status() { return status; }
    public Map<String, List<String>> headers() { return headers; }
    public byte[] body() { return body; }
    public String bodyAsString() { return body == null ? null : new String(body, StandardCharsets.UTF_8); }
}
