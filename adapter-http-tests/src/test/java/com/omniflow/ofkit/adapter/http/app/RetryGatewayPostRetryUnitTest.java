package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RetryGatewayPostRetryUnitTest {
    @Test
    void retries_post_when_idempotent_only_false() throws Exception {
        RetryGateway gw = new RetryGateway();
        RetrySpec spec = new RetrySpec(true, 1, 0, 0, false, false, false); // idempotent_only=false
        AdapterProfile profile = new AdapterProfile("p", null, List.of(), List.of(),
                ProblemDetails.of("about:blank","t",502, ""), CachePolicy.disabled(), spec,
                HttpClientSpec.defaults(), new AuthSpec.None());
        final int[] calls = {0};
        HttpPort http = req -> {
            calls[0]++;
            if (calls[0] == 1) return new HttpResponse(500, Map.of(), new byte[0]);
            return new HttpResponse(200, Map.of(), new byte[0]);
        };
        HttpRequest r = new HttpRequest("POST", URI.create("http://x"), Map.of(), new byte[0]);
        HttpResponse resp = gw.execute(profile, r, http);
        assertEquals(200, resp.statusCode());
        assertEquals(2, calls[0]);
    }
}

