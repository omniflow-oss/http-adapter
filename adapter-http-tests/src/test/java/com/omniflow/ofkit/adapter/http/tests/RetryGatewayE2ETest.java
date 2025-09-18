package com.omniflow.ofkit.adapter.http.tests;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.CacheGateway;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.app.RetryGateway;
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
class RetryGatewayE2ETest {

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

    private AdapterProfile profile(RetrySpec retry) {
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur service externe", 502, "No rule matched");
        return new AdapterProfile("p", base, List.of(ok), List.of(), generic, CachePolicy.disabled(), retry, HttpClientSpec.defaults(), new AuthSpec.None());
    }

    @Test
    void retries_5xx_then_succeeds_for_get() throws Exception {
        stubFor(get(urlEqualTo("/unstable"))
                .inScenario("unstable")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second"));
        stubFor(get(urlEqualTo("/unstable"))
                .inScenario("unstable")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200)));

        RetrySpec retry = new RetrySpec(true, 2, 1, 10, false, false, true);
        AdapterFacade facade = new AdapterFacade(new JdkHttpClientAdapter(), new RuleEngine(), pid -> Optional.of(profile(retry)), new CacheGateway(new InMemoryCacheStore()), new RetryGateway());
        HttpRequest req = new HttpRequest("GET", URI.create(base + "/unstable"), Map.of(), null);
        Result r = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r);
        verify(2, getRequestedFor(urlEqualTo("/unstable")));
    }

    @Test
    void honors_retry_after_header() throws Exception {
        stubFor(get(urlEqualTo("/ra"))
                .inScenario("ra")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "0"))
                .willSetStateTo("ok"));
        stubFor(get(urlEqualTo("/ra"))
                .inScenario("ra")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200)));

        RetrySpec retry = new RetrySpec(true, 1, 1000, 1000, false, true, true);
        AdapterFacade facade = new AdapterFacade(new JdkHttpClientAdapter(), new RuleEngine(), pid -> Optional.of(profile(retry)), new CacheGateway(new InMemoryCacheStore()), new RetryGateway());
        HttpRequest req = new HttpRequest("GET", URI.create(base + "/ra"), Map.of(), null);
        Result r = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r);
        verify(2, getRequestedFor(urlEqualTo("/ra")));
    }

    @Test
    void no_retry_on_post_when_idempotent_only() throws Exception {
        stubFor(post(urlEqualTo("/np")).willReturn(aResponse().withStatus(500)));
        RetrySpec retry = new RetrySpec(true, 2, 1, 10, false, false, true);
        AdapterFacade facade = new AdapterFacade(new JdkHttpClientAdapter(), new RuleEngine(), pid -> Optional.of(profile(retry)), new CacheGateway(new InMemoryCacheStore()), new RetryGateway());
        HttpRequest req = new HttpRequest("POST", URI.create(base + "/np"), Map.of(), new byte[0]);
        Result r = facade.handle("p", req);
        assertInstanceOf(Result.Failure.class, r);
        verify(1, postRequestedFor(urlEqualTo("/np")));
    }
}
