package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.AuthGateway;
import com.omniflow.ofkit.adapter.http.app.CacheGateway;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.app.RetryGateway;
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

class AdapterFacadePipelineTest {

    @Test
    void pipeline_with_retry_cache_and_auth_executes_success() throws Exception {
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur", 502, "");
        CachePolicy cache = new CachePolicy(true, 60, 0, 0, true, true, List.of("accept"), 1024, 0);
        RetrySpec retry = new RetrySpec(true, 1, 0, 0, false, false, true);
        HttpClientSpec http = HttpClientSpec.defaults();
        AdapterProfile profile = new AdapterProfile("p", "http://upstream", List.of(ok), List.of(), generic, cache, retry, http, new AuthSpec.Bearer("tkn"));
        ProfileRegistry registry = pid -> Optional.of(profile);

        final int[] calls = {0};
        HttpPort httpPort = req -> {
            calls[0]++;
            if (calls[0] == 1) return new HttpResponse(500, Map.of(), new byte[0]);
            return new HttpResponse(200, Map.of(), new byte[0]);
        };

        CacheGateway cacheGateway = new CacheGateway(new InMemoryCacheStore());
        RetryGateway retryGateway = new RetryGateway();
        AuthGateway authGateway = new AuthGateway();
        AdapterFacade facade = new AdapterFacade(httpPort, new RuleEngine(), registry, cacheGateway, retryGateway, authGateway);

        HttpRequest req = new HttpRequest("GET", URI.create("http://upstream/r"), Map.of("accept", List.of("application/json")), null);
        var res = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, res);
        assertEquals(2, calls[0]);
    }
}

