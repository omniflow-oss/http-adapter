package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RetryGatewayExceptionTest {
    @Test
    void throws_after_max_retries_on_exceptions() {
        RetryGateway gw = new RetryGateway();
        RetrySpec spec = new RetrySpec(true, 1, 0, 0, false, false, true);
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), CachePolicy.disabled(), spec,
                HttpClientSpec.defaults(), new AuthSpec.None());

        HttpPort http = req -> { throw new RuntimeException("boom"); };
        HttpRequest r = new HttpRequest("GET", URI.create("http://x"), Map.of(), null);
        assertThrows(RuntimeException.class, () -> gw.execute(profile, r, http));
    }
}

