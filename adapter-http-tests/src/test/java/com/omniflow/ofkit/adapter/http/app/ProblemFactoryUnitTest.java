package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProblemFactoryUnitTest {
    @Test
    void builds_generic_problem() {
        ProblemFactory pf = new ProblemFactory();
        ProblemDetails p = pf.generic("about:blank", "Erreur", 502, "No rule matched");
        assertEquals("about:blank", p.type());
        assertEquals("Erreur", p.title());
        assertEquals(502, p.status());
        assertEquals("No rule matched", p.detail());
        assertNotNull(p.extensions());
        assertTrue(p.extensions().isEmpty());
    }
}

