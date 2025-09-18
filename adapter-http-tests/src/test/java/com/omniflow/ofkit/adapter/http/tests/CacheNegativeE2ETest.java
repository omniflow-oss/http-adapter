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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
class CacheNegativeE2ETest {
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

    private AdapterProfile profile() {
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur service externe", 502, "No rule matched");
        CachePolicy cache = new CachePolicy(true, 0, 0, 0, true, true, List.of("accept"), 0, 30);
        return new AdapterProfile("p", base, List.of(ok), List.of(), generic, cache, RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None());
    }

    @Test
    void caches_404_negative_then_serves_from_cache() throws Exception {
        stubFor(get(urlEqualTo("/neg"))
                .inScenario("neg")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(404))
                .willSetStateTo("second"));
        // Any second call would 500 but should not be reached due to negative cache
        stubFor(get(urlEqualTo("/neg"))
                .inScenario("neg")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(500)));

        HttpPort http = new JdkHttpClientAdapter();
        AdapterFacade facade = new AdapterFacade(http, new RuleEngine(), pid -> Optional.of(profile()), new CacheGateway(new InMemoryCacheStore()));
        HttpRequest req = new HttpRequest("GET", URI.create(base + "/neg"), Map.of(), null);

        Result first = facade.handle("p", req);
        assertInstanceOf(Result.Failure.class, first);
        assertEquals(502, ((Result.Failure) first).problem().status());

        Result second = facade.handle("p", req);
        assertInstanceOf(Result.Failure.class, second);
        verify(1, getRequestedFor(urlEqualTo("/neg")));
    }
}
