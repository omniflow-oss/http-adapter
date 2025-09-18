package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.AdapterProfile;
import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.model.RetrySpec;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class RetryGateway {

    public HttpResponse execute(AdapterProfile profile, HttpRequest request, HttpPort http) throws Exception {
        RetrySpec spec = profile.retrySpec();
        if (spec == null || !spec.enabled()) {
            return http.execute(request);
        }
        boolean idempotent = isIdempotent(request.method());
        if (spec.idempotentOnly() && !idempotent) {
            return http.execute(request);
        }

        int attempts = 0;
        HttpResponse last = null;
        while (true) {
            attempts++;
            Exception lastEx = null;
            try {
                last = http.execute(request);
            } catch (Exception e) {
                lastEx = e;
            }

            boolean retriable = lastEx != null || isRetriableStatus(last);
            if (!retriable) {
                if (lastEx != null) throw lastEx;
                return last;
            }
            if (attempts > spec.maxRetries()) {
                if (lastEx != null) throw lastEx;
                return last;
            }

            long delayMs = computeDelayMs(spec, attempts, last);
            if (delayMs > 0) Thread.sleep(delayMs);
        }
    }

    private static boolean isIdempotent(String method) {
        String m = method.toUpperCase(Locale.ROOT);
        return m.equals("GET") || m.equals("HEAD") || m.equals("PUT") || m.equals("DELETE") || m.equals("OPTIONS");
    }

    private static boolean isRetriableStatus(HttpResponse resp) {
        if (resp == null) return true;
        int s = resp.statusCode();
        return s == 429 || (s >= 500 && s <= 599);
    }

    private static long computeDelayMs(RetrySpec spec, int attempt, HttpResponse resp) {
        if (spec.respectRetryAfter() && resp != null) {
            String ra = firstHeader(resp.headers(), "Retry-After");
            if (ra != null) {
                Long fromHeader = parseRetryAfter(ra);
                if (fromHeader != null) return Math.min(fromHeader, spec.maxDelayMs());
            }
        }
        long base = spec.initialDelayMs() <= 0 ? 0 : spec.initialDelayMs() * (1L << Math.max(0, attempt - 1));
        base = Math.min(base, spec.maxDelayMs() > 0 ? spec.maxDelayMs() : base);
        if (spec.jitter()) {
            return ThreadLocalRandom.current().nextLong(0, Math.max(1, base + 1));
        }
        return base;
    }

    private static Long parseRetryAfter(String header) {
        try {
            // Delta-seconds
            return Long.parseLong(header.trim()) * 1000L;
        } catch (NumberFormatException ignored) { }
        try {
            // HTTP-date (RFC 7231)
            TemporalAccessor ta = DateTimeFormatter.RFC_1123_DATE_TIME.parse(header);
            long epochMs = java.time.ZonedDateTime.from(ta).toInstant().toEpochMilli();
            long now = System.currentTimeMillis();
            return Math.max(0, epochMs - now);
        } catch (DateTimeParseException ignored) { }
        return null;
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        List<String> values = headers.get(name);
        if (values == null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    values = e.getValue();
                    break;
                }
            }
        }
        if (values == null || values.isEmpty()) return null;
        return values.get(0);
    }
}

