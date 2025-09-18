package com.omniflow.ofkit.adapter.http.domain.cache;

import java.util.Map;

public record CacheKey(
        String profile,
        String method,
        String path,
        Map<String, String> varyHeaders,
        String bodyHash
) {}

