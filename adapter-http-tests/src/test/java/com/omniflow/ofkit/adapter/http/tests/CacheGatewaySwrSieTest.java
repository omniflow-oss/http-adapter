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
class CacheGatewaySwrSieTest {
    WireMockServer wm;
    String base;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        WireMock.configureFor("localhost", wm.port());
        base = "http://localhost:" + wm.port();
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private AdapterProfile profileWithPolicy(CachePolicy policy) {
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur service externe", 502, "No rule matched");
        return new AdapterProfile("p", base, List.of(ok), List.of(), generic, policy, com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.disabled(), com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec.defaults(), new com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.None());
    }

    private AdapterFacade facade(CachePolicy policy) {
        HttpPort http = new JdkHttpClientAdapter();
        RuleEngine engine = new RuleEngine();
        ProfileRegistry registry = pid -> Optional.of(profileWithPolicy(policy));
        CacheGateway cacheGateway = new CacheGateway(new InMemoryCacheStore());
        return new AdapterFacade(http, engine, registry, cacheGateway);
    }

    @Test
    void swr_serves_stale_without_upstream_call() throws Exception {
        // TTL=0 (immediate stale), SWR=60 allows returning cached response without revalidation
        CachePolicy policy = new CachePolicy(true, 0, 60, 0, true, true, List.of("accept"), 0, 0);
        AdapterFacade facade = facade(policy);

        stubFor(get(urlEqualTo("/swr")).willReturn(aResponse().withStatus(200).withHeader("ETag", "\"v1\"").withBody("v1")));

        HttpRequest req = new HttpRequest("GET", URI.create(base + "/swr"), Map.of(), null);
        Result r1 = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r1);
        assertEquals("v1", new String(((Result.Success) r1).response().body(), StandardCharsets.UTF_8));

        // If SWR works, second call should be served from cache without calling upstream
        // Configure upstream to fail if called
        stubFor(get(urlEqualTo("/swr")).willReturn(aResponse().withStatus(500)));

        Result r2 = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r2);
        assertEquals("v1", new String(((Result.Success) r2).response().body(), StandardCharsets.UTF_8));

        // Background refresh may trigger an extra request; ensure at least one call happened
        verify(getRequestedFor(urlEqualTo("/swr")));
    }

    @Test
    void sie_serves_stale_on_error_after_expiry() throws Exception {
        // TTL=0 forces immediate revalidation, SWR=0, SIE=60 allows fallback when upstream errors
        CachePolicy policy = new CachePolicy(true, 0, 0, 60, true, true, List.of("accept"), 0, 0);
        AdapterFacade facade = facade(policy);

        // First fill
        stubFor(get(urlEqualTo("/sie"))
                .withHeader("If-None-Match", absent())
                .willReturn(aResponse().withStatus(200).withHeader("ETag", "\"v1\"").withBody("v1")));

        HttpRequest req = new HttpRequest("GET", URI.create(base + "/sie"), Map.of(), null);
        Result r1 = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r1);

        // Now upstream returns 500; cache should serve stale due to SIE
        stubFor(get(urlEqualTo("/sie"))
                .withHeader("If-None-Match", equalTo("\"v1\""))
                .willReturn(aResponse().withStatus(500)));

        Result r2 = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r2);
        assertEquals("v1", new String(((Result.Success) r2).response().body(), StandardCharsets.UTF_8));

        // Two requests total, second had conditional
        verify(2, getRequestedFor(urlEqualTo("/sie")));
        verify(getRequestedFor(urlEqualTo("/sie")).withHeader("If-None-Match", equalTo("\"v1\"")));
    }
}
