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

class AdapterFacadeDefaultHttpSpecTest {
    @Test
    void uses_default_http_spec_when_null() throws Exception {
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur", 502, "");
        AdapterProfile profile = new AdapterProfile("p", "http://localhost", List.of(ok), List.of(), generic,
                CachePolicy.disabled(), RetrySpec.disabled(), null, new AuthSpec.None());
        ProfileRegistry registry = pid -> Optional.of(profile);

        final HttpRequest[] seen = new HttpRequest[1];
        HttpPort port = req -> { seen[0] = req; return new HttpResponse(200, Map.of(), new byte[0]); };
        AdapterFacade facade = new AdapterFacade(port, new RuleEngine(), registry);
        HttpRequest input = new HttpRequest("GET", URI.create("http://localhost/test"), Map.of(), null);
        var res = facade.handle("p", input);
        assertInstanceOf(Result.Success.class, res);
        Map<String, List<String>> h = seen[0].headers();
        assertEquals("10000", first(h, "X-OF-Read-Timeout-Ms"));
        assertEquals("5000", first(h, "X-OF-Connect-Timeout-Ms"));
        assertEquals("true", first(h, "X-OF-HTTP-Keep-Alive"));
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}

