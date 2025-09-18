package com.omniflow.ofkit.adapter.http.domain.retry;

import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;

public interface RetryClassifier {
    boolean isRetriable(ResponseContext ctx, Throwable error);
}

