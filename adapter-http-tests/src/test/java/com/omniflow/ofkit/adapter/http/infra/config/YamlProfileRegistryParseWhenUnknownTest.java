package com.omniflow.ofkit.adapter.http.infra.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponsePredicate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileRegistryParseWhenUnknownTest {
    @Test
    void parse_when_unknown_node_returns_false_predicate() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode when = om.readTree("{\"foo\":\"bar\"}");
        Method parseWhen = YamlProfileRegistry.class.getDeclaredMethod("parseWhen", JsonNode.class);
        parseWhen.setAccessible(true);
        ResponsePredicate p = (ResponsePredicate) parseWhen.invoke(null, when);
        assertFalse(p.test(new ResponseContext(new HttpResponse(200, Map.of(), new byte[0]))));
    }
}

