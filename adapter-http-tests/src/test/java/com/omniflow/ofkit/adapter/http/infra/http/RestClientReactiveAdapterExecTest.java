package com.omniflow.ofkit.adapter.http.infra.http;

import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestClientReactiveAdapterExecTest {

    @Test
    void execute_handles_connection_failure_quickly() {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-OF-Connect-Timeout-Ms", List.of("50"));
        headers.put("X-OF-Read-Timeout-Ms", List.of("50"));
        headers.put("X-OF-HTTP-Max-Pool-Size", List.of("1"));
        headers.put("X-OF-HTTP-Max-Wait-Queue", List.of("0"));
        headers.put("X-OF-HTTP-Keep-Alive", List.of("false"));
        headers.put("X-OF-HTTP-Keep-Alive-Timeout-S", List.of("1"));
        HttpRequest req = new com.omniflow.ofkit.adapter.http.domain.model.HttpRequest(
                "GET",
                URI.create("http://127.0.0.1:9/"), // closed port to force fast failure
                headers,
                null
        );
        Exception caught = null;
        try {
            ad.execute(req);
        } catch (Exception e) {
            caught = e;
        }
        assertNotNull(caught, "should fail to connect and throw");
    }
}

