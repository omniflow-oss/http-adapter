package com.omniflow.ofkit.adapter.http.tests;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.AuthGateway;
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
class AuthGatewayE2ETest {
    WireMockServer wm;
    String base;

    @BeforeEach
    void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        WireMock.configureFor("localhost", wm.port());
        base = "http://localhost:" + wm.port();
    }

    @AfterEach
    void stop() { wm.stop(); }

    private AdapterProfile profile(AuthSpec auth) {
        SuccessRule ok = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur service externe", 502, "No rule matched");
        return new AdapterProfile("p", base, List.of(ok), List.of(), generic, CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), auth);
    }

    @Test
    void bearer_token_sets_authorization_header() throws Exception {
        stubFor(get(urlEqualTo("/auth"))
                .withHeader("Authorization", equalTo("Bearer secret"))
                .willReturn(aResponse().withStatus(200)));

        HttpPort http = new JdkHttpClientAdapter();
        AdapterFacade facade = new AdapterFacade(http, new RuleEngine(), pid -> Optional.of(profile(new AuthSpec.Bearer("secret"))), new CacheGateway(new InMemoryCacheStore()));
        // We need AuthGateway in CDI ctor; for test, we simulate adornment via AdapterFacade test constructor that doesn't include AuthGateway, so inject manually by headers is not doable here. Instead, use the 6-arg constructor via null retry & new AuthGateway.
        facade = new AdapterFacade(http, new RuleEngine(), pid -> Optional.of(profile(new AuthSpec.Bearer("secret"))), new CacheGateway(new InMemoryCacheStore()), new com.omniflow.ofkit.adapter.http.app.RetryGateway(), new AuthGateway());

        HttpRequest req = new HttpRequest("GET", URI.create(base + "/auth"), Map.of(), null);
        Result r = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r);
        verify(getRequestedFor(urlEqualTo("/auth")).withHeader("Authorization", equalTo("Bearer secret")));
    }

    @Test
    void api_key_header_is_added() throws Exception {
        stubFor(get(urlEqualTo("/key"))
                .withHeader("X-API-Key", equalTo("k123"))
                .willReturn(aResponse().withStatus(200)));

        HttpPort http = new JdkHttpClientAdapter();
        AdapterFacade facade = new AdapterFacade(http, new RuleEngine(), pid -> Optional.of(profile(new AuthSpec.ApiKey("X-API-Key", "k123"))), new CacheGateway(new InMemoryCacheStore()), new com.omniflow.ofkit.adapter.http.app.RetryGateway(), new AuthGateway());
        HttpRequest req = new HttpRequest("GET", URI.create(base + "/key"), Map.of(), null);
        Result r = facade.handle("p", req);
        assertInstanceOf(Result.Success.class, r);
        verify(getRequestedFor(urlEqualTo("/key")).withHeader("X-API-Key", equalTo("k123")));
    }
}
