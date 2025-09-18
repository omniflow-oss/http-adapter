package com.omniflow.ofkit.adapter.http.infra.http;

import java.util.List;
import java.util.Map;

public final class HttpHeaderUtils {
    private HttpHeaderUtils() {}

    public static boolean hasHeaderTrue(Map<String, List<String>> headers, String name) {
        if (headers == null) return false;
        List<String> v = headers.get(name);
        if (v == null) {
            for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        }
        return v != null && !v.isEmpty() && Boolean.parseBoolean(v.get(0));
    }

    public static long parseLongHeader(Map<String, List<String>> headers, String name, long dflt) {
        if (headers == null) return dflt;
        List<String> v = headers.get(name);
        if (v == null) {
            for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        }
        if (v == null || v.isEmpty()) return dflt;
        try { return Long.parseLong(v.get(0)); } catch (Exception ignored) { return dflt; }
    }
}

