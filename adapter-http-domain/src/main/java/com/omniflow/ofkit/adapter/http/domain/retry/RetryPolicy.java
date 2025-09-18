package com.omniflow.ofkit.adapter.http.domain.retry;

import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;

import java.time.Duration;
import java.util.Optional;

public interface RetryPolicy {
    /** Return next backoff for the given attempt (1-based), or empty if no retry. */
    Optional<Duration> nextBackoff(int attempt, ResponseContext ctx, Throwable error);
}

