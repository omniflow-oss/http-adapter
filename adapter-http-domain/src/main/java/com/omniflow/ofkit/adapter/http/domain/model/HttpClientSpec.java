package com.omniflow.ofkit.adapter.http.domain.model;

public record HttpClientSpec(
        int connectTimeoutMs,
        int readTimeoutMs,
        SslSpec ssl,
        int maxPoolSize,
        int maxWaitQueueSize,
        boolean keepAlive,
        int keepAliveTimeoutSeconds
) {
    public static HttpClientSpec defaults() {
        return new HttpClientSpec(5000, 10000, new SslSpec(false), 50, -1, true, 60);
    }
}
