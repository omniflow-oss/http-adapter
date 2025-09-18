package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.infra.cache.InMemoryCacheStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CacheGatewayUnitTest {

    @Test
    void etag_304_revalidate_path_sets_header() throws Exception {
        CacheGateway gw = new CacheGateway(new InMemoryCacheStore());
        CachePolicy pol = new CachePolicy(true, 0, 0, 0, true, true, List.of("accept"), 0, 0);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(),
                HttpClientSpec.defaults(), new AuthSpec.None());

        final int[] calls = {0};
        HttpPort http = req -> {
            // If first call (no If-None-Match) return 200 with ETag, else 304
            if (calls[0]++ == 0) return new HttpResponse(200, Map.of("ETag", List.of("\"abc\"")), "v1".getBytes(StandardCharsets.UTF_8));
            return new HttpResponse(304, Map.of(), new byte[0]);
        };

        HttpRequest r = new HttpRequest("GET", URI.create("http://x/cache"), Map.of(), null);
        HttpResponse a = gw.execute(profile, r, http);
        assertEquals(200, a.statusCode());

        HttpResponse b = gw.execute(profile, r, http);
        assertEquals(200, b.statusCode());
        assertEquals("revalidate", first(b.headers(), "X-OF-Cache"));
    }

    @Test
    void swr_returns_stale_immediately() throws Exception {
        CacheGateway gw = new CacheGateway(new InMemoryCacheStore());
        CachePolicy pol = new CachePolicy(true, 0, 60, 0, true, true, List.of("accept"), 0, 0);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(),
                HttpClientSpec.defaults(), new AuthSpec.None());

        final int[] calls = {0};
        HttpPort http = req -> {
            if (calls[0]++ == 0) return new HttpResponse(200, Map.of("ETag", List.of("\"abc\"")), "v1".getBytes(StandardCharsets.UTF_8));
            // Should not be required for SWR validation; return 500 to prove stale served
            return new HttpResponse(500, Map.of(), new byte[0]);
        };

        HttpRequest r = new HttpRequest("GET", URI.create("http://x/cache"), Map.of(), null);
        HttpResponse a = gw.execute(profile, r, http);
        assertEquals(200, a.statusCode());
        HttpResponse b = gw.execute(profile, r, http);
        assertEquals(200, b.statusCode());
        assertEquals("swr", first(b.headers(), "X-OF-Cache"));
    }

    @Test
    void sie_serves_stale_on_5xx_after_expiry() throws Exception {
        CacheGateway gw = new CacheGateway(new InMemoryCacheStore());
        CachePolicy pol = new CachePolicy(true, 0, 0, 60, true, true, List.of("accept"), 0, 0);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(),
                HttpClientSpec.defaults(), new AuthSpec.None());

        final int[] calls = {0};
        HttpPort http = req -> {
            if (calls[0]++ == 0) return new HttpResponse(200, Map.of("ETag", List.of("\"abc\"")), "v1".getBytes(StandardCharsets.UTF_8));
            return new HttpResponse(500, Map.of(), new byte[0]);
        };

        HttpRequest r = new HttpRequest("GET", URI.create("http://x/cache"), Map.of(), null);
        HttpResponse a = gw.execute(profile, r, http);
        assertEquals(200, a.statusCode());
        HttpResponse b = gw.execute(profile, r, http);
        assertEquals(200, b.statusCode());
        assertEquals("sie", first(b.headers(), "X-OF-Cache"));
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}

