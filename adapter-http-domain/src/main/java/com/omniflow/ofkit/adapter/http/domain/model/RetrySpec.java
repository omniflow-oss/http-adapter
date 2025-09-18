package com.omniflow.ofkit.adapter.http.domain.model;

public record RetrySpec(
        boolean enabled,
        int maxRetries,
        long initialDelayMs,
        long maxDelayMs,
        boolean jitter,
        boolean respectRetryAfter,
        boolean idempotentOnly
) {
    public static RetrySpec disabled() {
        return new RetrySpec(false, 0, 0, 0, false, false, true);
    }
}

