package com.omniflow.ofkit.adapter.http.infra.http;

import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("e2e")
class JdkHttpClientAdapterTest {
    static com.sun.net.httpserver.HttpServer server;
    static int port;

    @BeforeAll
    static void start() throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/echo", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("X-Echo-Method", exchange.getRequestMethod());
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Test
    void forwards_headers_and_body_and_adds_latency_header() throws Exception {
        JdkHttpClientAdapter http = new JdkHttpClientAdapter(Duration.ofMillis(500), Duration.ofSeconds(2));
        byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
        Map<String, List<String>> headers = Map.of("X-Test", List.of("v"));
        HttpRequest req = new HttpRequest("POST", URI.create("http://localhost:" + port + "/echo"), headers, payload);
        HttpResponse resp = http.execute(req);
        assertEquals(200, resp.statusCode());
        assertEquals("v", first(resp.headers(), "X-Test")); // echoed back by upstream
        assertNotNull(first(resp.headers(), "X-OF-Upstream-Latency-Ms"));
        assertArrayEquals(payload, resp.body());
    }

    @Test
    void head_put_patch_delete_supported() throws Exception {
        JdkHttpClientAdapter http = new JdkHttpClientAdapter(Duration.ofMillis(500), Duration.ofSeconds(2));
        Map<String, List<String>> headers = Map.of();

        var head = http.execute(new HttpRequest("HEAD", URI.create("http://localhost:" + port + "/echo"), headers, null));
        assertEquals(200, head.statusCode());
        assertEquals("HEAD", first(head.headers(), "X-Echo-Method"));

        var put = http.execute(new HttpRequest("PUT", URI.create("http://localhost:" + port + "/echo"), headers, "x".getBytes()));
        assertEquals(200, put.statusCode());
        assertEquals("PUT", first(put.headers(), "X-Echo-Method"));

        var patch = http.execute(new HttpRequest("PATCH", URI.create("http://localhost:" + port + "/echo"), headers, "y".getBytes()));
        assertEquals(200, patch.statusCode());
        assertEquals("PATCH", first(patch.headers(), "X-Echo-Method"));

        var del = http.execute(new HttpRequest("DELETE", URI.create("http://localhost:" + port + "/echo"), headers, null));
        assertEquals(200, del.statusCode());
        assertEquals("DELETE", first(del.headers(), "X-Echo-Method"));
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}
