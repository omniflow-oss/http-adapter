package com.omniflow.ofkit.adapter.http.domain.cache;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;

import java.time.Instant;

public record CachedEntry(
        HttpResponse response,
        Instant storedAt,
        Instant expiresAt,
        String etag,
        String lastModified
) {}

