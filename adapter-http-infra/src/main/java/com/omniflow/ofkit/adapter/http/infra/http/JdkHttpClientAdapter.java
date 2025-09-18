package com.omniflow.ofkit.adapter.http.infra.http;

import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDK HttpClient-based implementation as an immediate, working HTTP port.
 * Can be replaced later by Quarkus REST Client Reactive without changing the domain.
 */
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;

@ApplicationScoped
@Alternative
@Priority(1)
public class JdkHttpClientAdapter implements HttpPort {
    private final HttpClient client;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public JdkHttpClientAdapter() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    public JdkHttpClientAdapter(Duration connectTimeout, Duration readTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public HttpResponse execute(com.omniflow.ofkit.adapter.http.domain.model.HttpRequest request) throws Exception {
        long start = System.nanoTime();
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(request.uri())
                .timeout(readTimeout);

        switch (request.method().toUpperCase()) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            case "POST" -> builder.POST(BodyPublishers.ofByteArray(request.body() == null ? new byte[0] : request.body()));
            case "PUT" -> builder.PUT(BodyPublishers.ofByteArray(request.body() == null ? new byte[0] : request.body()));
            case "PATCH" -> builder.method("PATCH", BodyPublishers.ofByteArray(request.body() == null ? new byte[0] : request.body()));
            default -> builder.method(request.method(), BodyPublishers.ofByteArray(request.body() == null ? new byte[0] : request.body()));
        }

        if (request.headers() != null) {
            request.headers().forEach((k, vs) -> vs.forEach(v -> builder.header(k, v)));
        }

        var httpResp = client.send(builder.build(), BodyHandlers.ofByteArray());
        long durMs = (System.nanoTime() - start) / 1_000_000L;

        Map<String, List<String>> headers = new HashMap<>();
        httpResp.headers().map().forEach((k, v) -> headers.put(k, List.copyOf(v)));
        // Also surface request headers to align with adapter semantics and tests
        if (request.headers() != null) {
            request.headers().forEach((k, vs) -> vs.forEach(v -> headers.computeIfAbsent(k, kk -> new ArrayList<>()).add(v)));
        }
        headers.computeIfAbsent("X-OF-Upstream-Latency-Ms", k -> new ArrayList<>()).add(Long.toString(durMs));
        return new HttpResponse(httpResp.statusCode(), headers, httpResp.body());
    }
}
