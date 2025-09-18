package com.omniflow.ofkit.adapter.http.infra.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileRegistryParseAuthTest {

    @Test
    void parse_auth_api_key_and_bearer_via_reflection() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode apiKey = om.readTree("{\"kind\":\"api_key\",\"api_key\":{\"header\":\"X-API-Key\",\"value\":\"k123\"}}");
        JsonNode bearer = om.readTree("{\"kind\":\"bearer\",\"bearer\":{\"token\":\"tkn\"}}");

        Method parseAuth = YamlProfileRegistry.class.getDeclaredMethod("parseAuth", JsonNode.class);
        parseAuth.setAccessible(true);
        Object ak = parseAuth.invoke(null, apiKey);
        Object br = parseAuth.invoke(null, bearer);

        assertTrue(ak instanceof com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.ApiKey);
        assertTrue(br instanceof com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.Bearer);
        var akSpec = (com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.ApiKey) ak;
        var brSpec = (com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.Bearer) br;
        assertEquals("X-API-Key", akSpec.headerName());
        assertEquals("k123", akSpec.value());
        assertEquals("tkn", brSpec.token());
    }
}

