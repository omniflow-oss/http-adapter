package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.CacheGateway;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine;
import com.omniflow.ofkit.adapter.http.domain.rules.StatusPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.SuccessRule;
import com.omniflow.ofkit.adapter.http.infra.cache.InMemoryCacheStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AdapterFacadeCacheHitPipelineTest {
    @Test
    void cache_hit_serves_without_http_call_and_sets_header() throws Exception {
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur", 502, "");
        CachePolicy cache = new CachePolicy(true, 60, 0, 0, true, true, List.of("accept"), 1024, 0);
        AdapterProfile profile = new AdapterProfile("p", "http://upstream", List.of(ok), List.of(), generic, cache, RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None());
        ProfileRegistry registry = pid -> Optional.of(profile);

        final int[] calls = {0};
        HttpPort http = req -> { calls[0]++; return new HttpResponse(200, Map.of(), new byte[0]); };
        AdapterFacade facade = new AdapterFacade(http, new RuleEngine(), registry, new CacheGateway(new InMemoryCacheStore()));

        HttpRequest req = new HttpRequest("GET", URI.create("http://upstream/r"), Map.of("accept", List.of("application/json")), null);
        var r1 = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r1);
        var r2 = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r2);
        assertEquals(1, calls[0], "second call served from cache");
    }
}

