package com.omniflow.ofkit.adapter.http.infra;

import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProducersTest {
    @Test
    void creates_adapter_facade() {
        Producers p = new Producers();
        AdapterFacade facade = p.adapterFacade();
        assertNotNull(facade);
    }
}

