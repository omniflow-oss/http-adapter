package com.omniflow.ofkit.adapter.http.infra.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponsePredicate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileRegistryParseWhenAllTest {
    @Test
    void parse_when_all_combines_predicates() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode when = om.readTree("{\"all\":[{\"status\":\"200-299\"},{\"json\":{\"pointer\":\"/x\",\"exists\":true}}]}");
        Method parseWhen = YamlProfileRegistry.class.getDeclaredMethod("parseWhen", JsonNode.class);
        parseWhen.setAccessible(true);
        ResponsePredicate p = (ResponsePredicate) parseWhen.invoke(null, when);
        var resp = new HttpResponse(200, Map.of(), "{\"x\":1}".getBytes(StandardCharsets.UTF_8));
        assertTrue(p.test(new ResponseContext(resp)));
    }
}

