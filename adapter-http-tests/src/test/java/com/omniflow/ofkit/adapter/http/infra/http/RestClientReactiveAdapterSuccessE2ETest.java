package com.omniflow.ofkit.adapter.http.infra.http;

import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.buffer.Buffer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
class RestClientReactiveAdapterSuccessE2ETest {
    static Vertx vertx;
    static HttpServer server;
    static int port;

    @BeforeAll
    static void start() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer();
        java.util.concurrent.CompletableFuture<Integer> fut = new java.util.concurrent.CompletableFuture<>();
        server.requestHandler(req -> {
                    // Echo method and a custom header back
                    String custom = req.getHeader("X-Custom");
                    if (custom != null) {
                        req.response().putHeader("X-Custom", custom);
                    }
                    if ("/slow".equals(req.path())) {
                        // delay response to trigger read timeout in tests
                        vertx.setTimer(200, t -> req.response().putHeader("X-Echo", req.method().name()).end(Buffer.buffer("slow")));
                    } else if ("/echoh".equals(req.path())) {
                        req.response().putHeader("X-Echo", req.method().name()).end(Buffer.buffer("echo"));
                    } else {
                        req.response().putHeader("X-Echo", req.method().name()).end(Buffer.buffer("hello"));
                    }
                })
              .listen(0, ar -> {
                  if (ar.succeeded()) { port = ar.result().actualPort(); fut.complete(port); }
                  else fut.completeExceptionally(ar.cause());
              });
        fut.get();
    }

    @AfterAll
    static void stop() throws Exception {
        java.util.concurrent.CompletableFuture<Void> fut = new java.util.concurrent.CompletableFuture<>();
        server.close(ar -> fut.complete(null));
        fut.get();
        vertx.close();
    }

    @Test
    void success_path_returns_200_and_latency_header() throws Exception {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-OF-Connect-Timeout-Ms", List.of("500"));
        headers.put("X-OF-Read-Timeout-Ms", List.of("1000"));
        HttpRequest req = new HttpRequest("GET", URI.create("http://127.0.0.1:" + port + "/ping"), headers, null);
        HttpResponse resp = ad.execute(req);
        assertEquals(200, resp.statusCode());
        assertEquals("GET", first(resp.headers(), "X-Echo"));
        assertNotNull(first(resp.headers(), "X-OF-Upstream-Latency-Ms"));
        assertEquals("hello", new String(resp.body(), StandardCharsets.UTF_8));
    }

    @Test
    void post_with_body_and_custom_header_is_echoed() throws Exception {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-OF-Connect-Timeout-Ms", List.of("500"));
        headers.put("X-OF-Read-Timeout-Ms", List.of("1000"));
        headers.put("X-Custom", List.of("abc"));
        HttpRequest req = new HttpRequest("POST", URI.create("http://127.0.0.1:" + port + "/echoh"), headers, "ping".getBytes(StandardCharsets.UTF_8));
        HttpResponse resp = ad.execute(req);
        assertEquals(200, resp.statusCode());
        assertEquals("abc", first(resp.headers(), "X-Custom"));
    }

    @Test
    void put_and_patch_methods_echoed() throws Exception {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-OF-Connect-Timeout-Ms", List.of("500"));
        headers.put("X-OF-Read-Timeout-Ms", List.of("1000"));
        HttpRequest putReq = new HttpRequest("PUT", URI.create("http://127.0.0.1:" + port + "/x"), headers, "p".getBytes(StandardCharsets.UTF_8));
        var putResp = ad.execute(putReq);
        assertEquals("PUT", first(putResp.headers(), "X-Echo"));

        HttpRequest patchReq = new HttpRequest("PATCH", URI.create("http://127.0.0.1:" + port + "/y"), headers, "p".getBytes(StandardCharsets.UTF_8));
        var patchResp = ad.execute(patchReq);
        assertEquals("PATCH", first(patchResp.headers(), "X-Echo"));
    }

    @Test
    void head_and_delete_methods_echoed() throws Exception {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-OF-Connect-Timeout-Ms", List.of("500"));
        headers.put("X-OF-Read-Timeout-Ms", List.of("1000"));
        var headResp = ad.execute(new HttpRequest("HEAD", URI.create("http://127.0.0.1:" + port + "/h"), headers, null));
        assertEquals("HEAD", first(headResp.headers(), "X-Echo"));
        var delResp = ad.execute(new HttpRequest("DELETE", URI.create("http://127.0.0.1:" + port + "/d"), headers, null));
        assertEquals("DELETE", first(delResp.headers(), "X-Echo"));
    }

    @Test
    void read_timeout_triggers_exception_on_slow_response() {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-OF-Connect-Timeout-Ms", List.of("500"));
        headers.put("X-OF-Read-Timeout-Ms", List.of("50"));
        HttpRequest req = new HttpRequest("GET", URI.create("http://127.0.0.1:" + port + "/slow"), headers, null);
        Exception ex = null;
        try { ad.execute(req); } catch (Exception e) { ex = e; }
        assertNotNull(ex, "expected read timeout exception");
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}
