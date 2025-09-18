package com.omniflow.ofkit.adapter.http.infra.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponsePredicate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileRegistryParseWhenTest {

    @Test
    void parse_when_header_and_body_regex() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode whenHeader = om.readTree("{\"header\":{\"name\":\"X-Env\",\"regex\":\"prod\"}}");
        JsonNode whenBody = om.readTree("{\"body_regex\":\"hello.*\\\\d\"}");

        Method parseWhen = YamlProfileRegistry.class.getDeclaredMethod("parseWhen", JsonNode.class);
        parseWhen.setAccessible(true);
        ResponsePredicate pHeader = (ResponsePredicate) parseWhen.invoke(null, whenHeader);
        ResponsePredicate pBody = (ResponsePredicate) parseWhen.invoke(null, whenBody);

        var resp = new HttpResponse(200, Map.of("X-Env", List.of("prod")), "hello 1".getBytes(StandardCharsets.UTF_8));
        var ctx = new ResponseContext(resp);
        assertTrue(pHeader.test(ctx));
        assertTrue(pBody.test(ctx));
    }
}
