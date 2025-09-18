package com.omniflow.ofkit.adapter.http.infra.config;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponseContext;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponsePredicate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileRegistryParseStatusTest {
    @Test
    void parse_single_status_code() throws Exception {
        Method parseStatus = YamlProfileRegistry.class.getDeclaredMethod("parseStatus", String.class);
        parseStatus.setAccessible(true);
        ResponsePredicate p = (ResponsePredicate) parseStatus.invoke(null, "404");
        assertTrue(p.test(new ResponseContext(new HttpResponse(404, Map.of(), new byte[0]))));
        assertFalse(p.test(new ResponseContext(new HttpResponse(200, Map.of(), new byte[0]))));
    }
}

