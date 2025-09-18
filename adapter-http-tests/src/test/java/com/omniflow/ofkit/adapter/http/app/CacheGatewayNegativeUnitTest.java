package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.infra.cache.InMemoryCacheStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CacheGatewayNegativeUnitTest {

    @Test
    void negative_entry_cached_and_served_with_hit_header() throws Exception {
        CachePolicy pol = new CachePolicy(true, 60, 0, 0, true, true, List.of(), 0, 30);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(),
                HttpClientSpec.defaults(), new AuthSpec.None());
        CacheGateway gw = new CacheGateway(new InMemoryCacheStore());

        final int[] calls = {0};
        HttpPort http = req -> {
            calls[0]++;
            return new HttpResponse(404, Map.of(), new byte[0]);
        };

        HttpRequest r = new HttpRequest("GET", URI.create("http://x/neg"), Map.of(), null);
        var first = gw.execute(profile, r, http);
        assertEquals(404, first.statusCode());
        var second = gw.execute(profile, r, http);
        assertEquals(404, second.statusCode());
        assertEquals("hit", first(second.headers(), "X-OF-Cache"));
        assertEquals(1, calls[0], "negative response should be cached");
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}

