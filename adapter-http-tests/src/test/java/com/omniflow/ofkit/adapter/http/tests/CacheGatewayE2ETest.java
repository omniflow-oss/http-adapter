package com.omniflow.ofkit.adapter.http.tests;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.CacheGateway;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine;
import com.omniflow.ofkit.adapter.http.domain.rules.StatusPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.SuccessRule;
import com.omniflow.ofkit.adapter.http.infra.cache.InMemoryCacheStore;
import com.omniflow.ofkit.adapter.http.infra.http.JdkHttpClientAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
class CacheGatewayE2ETest {

    WireMockServer wm;
    AdapterFacade facade;
    String base;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        WireMock.configureFor("localhost", wm.port());
        base = "http://localhost:" + wm.port();

        // First request without If-None-Match -> 200 with ETag and body
        stubFor(get(urlEqualTo("/cache"))
                .withHeader("If-None-Match", absent())
                .willReturn(aResponse().withStatus(200)
                        .withHeader("ETag", "\"abc\"")
                        .withHeader("Content-Type", "text/plain")
                        .withBody("v1")));

        // Subsequent conditional request with If-None-Match -> 304
        stubFor(get(urlEqualTo("/cache"))
                .withHeader("If-None-Match", equalTo("\"abc\""))
                .willReturn(aResponse().withStatus(304)));

        HttpPort http = new JdkHttpClientAdapter();
        RuleEngine engine = new RuleEngine();
        ProfileRegistry registry = pid -> Optional.of(simpleProfile());
        CacheGateway cacheGateway = new CacheGateway(new InMemoryCacheStore());
        facade = new AdapterFacade(http, engine, registry, cacheGateway);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private AdapterProfile simpleProfile() {
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur service externe", 502, "No rule matched");
        com.omniflow.ofkit.adapter.http.domain.model.CachePolicy cache = new com.omniflow.ofkit.adapter.http.domain.model.CachePolicy(true, 0, 0, 0, true, true, List.of("accept"), 0, 0);
        return new AdapterProfile("test", base, List.of(ok), List.of(), generic, cache, com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.disabled(), com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec.defaults(), new com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.None());
    }

    @Test
    void etag_304_serves_cached_body() throws Exception {
        HttpRequest req = new HttpRequest("GET", URI.create(base + "/cache"), Map.of(), null);
        Result r1 = facade.handle("test", req);
        assertInstanceOf(Result.Success.class, r1);
        assertEquals(200, ((Result.Success) r1).response().statusCode());
        assertEquals("v1", new String(((Result.Success) r1).response().body(), StandardCharsets.UTF_8));

        // Second request should hit cache with conditional, upstream returns 304, adapter returns cached 200
        Result r2 = facade.handle("test", req);
        assertInstanceOf(Result.Success.class, r2);
        assertEquals(200, ((Result.Success) r2).response().statusCode());
        assertEquals("v1", new String(((Result.Success) r2).response().body(), StandardCharsets.UTF_8));

        // Verify WireMock saw two requests and the second had If-None-Match
        verify(2, getRequestedFor(urlEqualTo("/cache")));
        verify(getRequestedFor(urlEqualTo("/cache")).withHeader("If-None-Match", equalTo("\"abc\"")));
    }
}
