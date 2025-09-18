package com.omniflow.ofkit.adapter.http.tests;

import com.omniflow.ofkit.adapter.http.app.AuthGateway;
import com.omniflow.ofkit.adapter.http.domain.model.AuthSpec;
import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthGatewayUnitTest {
    @Test
    void adds_bearer_and_api_key_headers() {
        AuthGateway gw = new AuthGateway();
        HttpRequest base = new HttpRequest("GET", URI.create("http://localhost"), Map.of(), null);

        var b = gw.apply(new AuthSpec.Bearer("tkn"), base);
        assertEquals("Bearer tkn", b.headers().get("Authorization").get(0));

        var k = gw.apply(new AuthSpec.ApiKey("X-API-Key", "k123"), base);
        assertEquals("k123", k.headers().get("X-API-Key").get(0));
    }
}

