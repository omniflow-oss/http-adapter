package com.omniflow.ofkit.adapter.http.infra.http;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class RestClientReactiveAdapterInitTest {
    @Test
    void constructor_initializes_two_clients() throws Exception {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Field f = RestClientReactiveAdapter.class.getDeclaredField("clients");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentMap<?,?> map = (ConcurrentMap<?,?>) f.get(ad);
        assertTrue(map.size() >= 2, "expected at least two WebClient instances (secure/insecure)");
    }
}

