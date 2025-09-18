package com.omniflow.ofkit.adapter.http.infra.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class AdapterResourceIT {
    WireMockServer wm;

    @BeforeEach
    void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        WireMock.configureFor("localhost", wm.port());
    }

    @AfterEach
    void stop() {
        wm.stop();
    }

    @Test
    void get_success_passthrough_and_pick_pointer() {
        stubFor(get(urlEqualTo("/v1/accounts?msisdn=1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type","application/json")
                        .withBody("{\"status\":\"OK\",\"data\":{\"id\":\"x\"}}")));

        // Profile "accounts_api" has a rule with pick_pointer in our example YAML loader
        given()
            .header("X-OF-Target-Base", "http://localhost:" + wm.port())
        .when()
            .get("/adapter/accounts_api/v1/accounts?msisdn=1")
        .then()
            .statusCode(200)
            .body("id", equalTo("x"));
    }
}

