package com.omniflow.ofkit.adapter.http.tests;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.rules.*;
import com.omniflow.ofkit.adapter.http.infra.http.JdkHttpClientAdapter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
class AdapterFacadeE2ETest {
    WireMockServer wm;
    AdapterFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        WireMock.configureFor("localhost", wm.port());

        // Stubs
        stubFor(get(urlPathEqualTo("/v1/accounts"))
                .withQueryParam("msisdn", matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(resource("golden/accounts/by-msisdn/ok.json"))));

        stubFor(get(urlPathEqualTo("/v1/accounts/error"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(resource("golden/accounts/by-msisdn/error.json"))));

        stubFor(get(urlPathEqualTo("/429"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "3")));

        // Build facade
        HttpPort http = new JdkHttpClientAdapter();
        RuleEngine engine = new RuleEngine();
        ProfileRegistry registry = pid -> Optional.of(testProfile());
        facade = new AdapterFacade(http, engine, registry);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private static String resource(String path) throws IOException {
        try (var is = AdapterFacadeE2ETest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Missing resource: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private AdapterProfile testProfile() {
        ResponsePredicate okFlag = ctx -> new StatusPredicate(200, 299).test(ctx)
                && JsonPointerPredicate.equalsAt("/status", "OK").test(ctx);
        SuccessRule ok = new SuccessRule("ok-2xx-json-flag", okFlag, "/data");

        ResponsePredicate bizError = ctx -> new StatusPredicate(200, 299).test(ctx)
                && JsonPointerPredicate.existsAt("/error/code").test(ctx);
        ErrorRule err = new ErrorRule(
                "err-business",
                bizError,
                ProblemDetails.of("https://omniflow/business", "Erreur métier amont", 422, "Business error")
        );
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur service externe", 502, "No rule matched");
        return new AdapterProfile("test", null, List.of(ok), List.of(err), generic, com.omniflow.ofkit.adapter.http.domain.model.CachePolicy.disabled(), com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.disabled(), com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec.defaults(), new com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.None());
    }

    @Test
    void success_2xx_with_ok_flag() throws Exception {
        URI uri = URI.create("http://localhost:" + wm.port() + "/v1/accounts?msisdn=33612345678");
        HttpRequest req = new HttpRequest("GET", uri, Map.of(), null);
        Result r = facade.handle("test", req);
        assertInstanceOf(Result.Success.class, r);
        assertEquals("ok-2xx-json-flag", ((Result.Success) r).ruleId());
        assertEquals(200, ((Result.Success) r).response().statusCode());
    }

    @Test
    void business_error_detected_in_200() throws Exception {
        URI uri = URI.create("http://localhost:" + wm.port() + "/v1/accounts/error");
        HttpRequest req = new HttpRequest("GET", uri, Map.of(), null);
        Result r = facade.handle("test", req);
        assertInstanceOf(Result.Failure.class, r);
        Result.Failure f = (Result.Failure) r;
        assertEquals("err-business", f.ruleId());
        assertEquals(422, f.problem().status());
    }

    @Test
    void rate_limit_429_maps_to_problem_rule_if_defined() throws Exception {
        // Profile does not define 429 error rule → expect generic_problem
        URI uri = URI.create("http://localhost:" + wm.port() + "/429");
        HttpRequest req = new HttpRequest("GET", uri, Map.of(), null);
        Result r = facade.handle("test", req);
        assertInstanceOf(Result.Failure.class, r);
        Result.Failure f = (Result.Failure) r;
        assertEquals("generic_problem", f.ruleId());
        assertEquals(502, f.problem().status());
    }
}
