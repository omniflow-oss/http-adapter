package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.infra.cache.InMemoryCacheStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CacheGatewayConditionalHeadersTest {

    @Test
    void adds_if_none_match_and_if_modified_since_on_revalidation() throws Exception {
        CachePolicy pol = new CachePolicy(true, 0, 0, 0, true, true, List.of(), 0, 0);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), pol, RetrySpec.disabled(),
                HttpClientSpec.defaults(), new AuthSpec.None());
        CacheGateway gw = new CacheGateway(new InMemoryCacheStore());

        final HttpRequest[] seen = new HttpRequest[1];
        final int[] calls = {0};
        HttpPort http = req -> {
            calls[0]++;
            if (calls[0] == 1) {
                return new HttpResponse(200, Map.of(
                        "ETag", List.of("\"abc\""),
                        "Last-Modified", List.of("Mon, 02 Jan 2006 15:04:05 GMT")
                ), new byte[0]);
            }
            seen[0] = req;
            return new HttpResponse(200, Map.of(), new byte[0]);
        };

        HttpRequest r = new HttpRequest("GET", URI.create("http://x/cond"), Map.of(), null);
        HttpResponse a = gw.execute(profile, r, http);
        assertEquals(200, a.statusCode());
        HttpResponse b = gw.execute(profile, r, http);
        assertEquals(200, b.statusCode());

        assertNotNull(seen[0], "second call captured");
        assertEquals("\"abc\"", first(seen[0].headers(), "If-None-Match"));
        assertEquals("Mon, 02 Jan 2006 15:04:05 GMT", first(seen[0].headers(), "If-Modified-Since"));
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}

