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

class CacheGatewayBodySizeAndVaryTest {

    @Test
    void large_body_not_cached_when_exceeds_max_kb() throws Exception {
        CachePolicy pol = new CachePolicy(true, 60, 0, 0, true, true, List.of("accept"), 1, 0); // 1 KB max
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(),
                HttpClientSpec.defaults(), new AuthSpec.None());

        CacheGateway gw = new CacheGateway(new InMemoryCacheStore());

        final int[] calls = {0};
        byte[] big = new byte[2049]; // > 2KB to avoid integer division rounding
        HttpPort http = req -> { calls[0]++; return new HttpResponse(200, Map.of(), big); };

        HttpRequest r = new HttpRequest("GET", URI.create("http://x/large"), Map.of("accept", List.of("application/json")), null);
        HttpResponse a = gw.execute(profile, r, http);
        assertEquals(200, a.statusCode());
        HttpResponse b = gw.execute(profile, r, http);
        assertEquals(200, b.statusCode());
        assertEquals(2, calls[0], "large response should not be cached");
    }

    @Test
    void small_body_cached_and_vary_header_creates_separate_entries() throws Exception {
        CachePolicy pol = new CachePolicy(true, 60, 0, 0, true, true, List.of("accept"), 1024, 0);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(),
                HttpClientSpec.defaults(), new AuthSpec.None());

        CacheGateway gw = new CacheGateway(new InMemoryCacheStore());

        final int[] calls = {0};
        HttpPort http = req -> {
            calls[0]++;
            String body = "v" + calls[0];
            return new HttpResponse(200, Map.of(), body.getBytes(StandardCharsets.UTF_8));
        };

        // Request A: accept json
        HttpRequest a = new HttpRequest("GET", URI.create("http://x/obj"), Map.of("accept", List.of("application/json")), null);
        HttpResponse r1 = gw.execute(profile, a, http);
        assertEquals(200, r1.statusCode());
        HttpResponse r2 = gw.execute(profile, a, http);
        assertEquals(200, r2.statusCode());
        assertEquals("hit", first(r2.headers(), "X-OF-Cache"));

        // Request B: accept xml -> different cache entry due to vary
        HttpRequest b = new HttpRequest("GET", URI.create("http://x/obj"), Map.of("accept", List.of("application/xml")), null);
        HttpResponse r3 = gw.execute(profile, b, http);
        assertEquals(200, r3.statusCode());

        assertEquals(2, calls[0], "should have cached JSON variant and created a separate XML entry");
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}
