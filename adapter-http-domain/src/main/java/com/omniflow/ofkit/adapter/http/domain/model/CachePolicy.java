package com.omniflow.ofkit.adapter.http.domain.model;

import java.util.List;
import java.util.Objects;

public record CachePolicy(
        boolean enabled,
        int defaultTtlSeconds,
        int swrTtlSeconds,
        int sieTtlSeconds,
        boolean useEtag,
        boolean useLastModified,
        List<String> varyHeaders,
        int maxBodyKb,
        int negativeTtlSeconds
) {
    public CachePolicy {
        varyHeaders = varyHeaders == null ? List.of() : List.copyOf(varyHeaders);
    }
    public static CachePolicy disabled() { return new CachePolicy(false, 0, 0, 0, true, true, List.of(), 0, 0); }
}
