package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine;
import com.omniflow.ofkit.adapter.http.domain.rules.StatusPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.SuccessRule;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AdapterFacadeHttpOptionsTest {

    @Test
    void adorns_http_headers_from_profile() throws Exception {
        // Arrange a profile with specific HTTP options
        HttpClientSpec http = new HttpClientSpec(1234, 5678, new SslSpec(true), 77, 99, false, 33);
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur", 502, "");
        AdapterProfile profile = new AdapterProfile("p", "http://localhost", List.of(ok), List.of(), generic,
                CachePolicy.disabled(), RetrySpec.disabled(), http, new AuthSpec.None());
        ProfileRegistry registry = pid -> Optional.of(profile);

        // Capture the request that Facade passes to HttpPort
        final HttpRequest[] seen = new HttpRequest[1];
        HttpPort port = req -> {
            seen[0] = req;
            return new HttpResponse(200, Map.of(), new byte[0]);
        };

        AdapterFacade facade = new AdapterFacade(port, new RuleEngine(), registry);
        HttpRequest input = new HttpRequest("GET", URI.create("http://localhost/test"), Map.of(), null);

        // Act
        var res = facade.handle("p", input);

        // Assert
        assertInstanceOf(Result.Success.class, res);
        assertNotNull(seen[0]);
        Map<String, List<String>> h = seen[0].headers();
        assertEquals("5678", first(h, "X-OF-Read-Timeout-Ms"));
        assertEquals("1234", first(h, "X-OF-Connect-Timeout-Ms"));
        assertEquals("true", first(h, "X-OF-SSL-Insecure"));
        assertEquals("77", first(h, "X-OF-HTTP-Max-Pool-Size"));
        assertEquals("99", first(h, "X-OF-HTTP-Max-Wait-Queue"));
        assertEquals("false", first(h, "X-OF-HTTP-Keep-Alive"));
        assertEquals("33", first(h, "X-OF-HTTP-Keep-Alive-Timeout-S"));
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) {
            for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        }
        return (v == null || v.isEmpty()) ? null : v.get(0);
    }
}

