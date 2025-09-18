package com.omniflow.ofkit.adapter.http.infra.config;

import com.omniflow.ofkit.adapter.http.domain.model.AdapterProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileRegistryTest {
    @Test
    void loads_profiles_and_parses_fields() {
        YamlProfileRegistry reg = new YamlProfileRegistry();
        var p1 = reg.findById("default");
        var p2 = reg.findById("accounts_api");
        assertTrue(p1.isPresent());
        assertTrue(p2.isPresent());
        AdapterProfile acc = p2.get();
        assertNotNull(acc.genericProblem());
        assertFalse(acc.successRules().isEmpty());
        assertNotNull(acc.httpSpec());
        assertNotNull(acc.cachePolicy());
        assertNotNull(acc.retrySpec());
    }
}

