package com.omniflow.ofkit.adapter.http.infra.http;

import org.junit.jupiter.api.Assumptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
class RestClientReactiveAdapterHttpsInsecureE2ETest {
    static Vertx vertx;
    static HttpServer server;
    static int port;

    @BeforeAll
    static void start() throws Exception {
        vertx = Vertx.vertx();
        try {
            String keyPath = java.nio.file.Paths.get(RestClientReactiveAdapterHttpsInsecureE2ETest.class.getResource("/tls/test-key.pem").toURI()).toFile().getAbsolutePath();
            String certPath = java.nio.file.Paths.get(RestClientReactiveAdapterHttpsInsecureE2ETest.class.getResource("/tls/test-cert.pem").toURI()).toFile().getAbsolutePath();
            HttpServerOptions opts = new HttpServerOptions()
                    .setSsl(true)
                    .setPemKeyCertOptions(new PemKeyCertOptions()
                            .setKeyPath(keyPath)
                            .setCertPath(certPath));
            server = vertx.createHttpServer(opts);
            java.util.concurrent.CompletableFuture<Integer> fut = new java.util.concurrent.CompletableFuture<>();
            server.requestHandler(req -> req.response().putHeader("X-Echo", req.method().name()).end("ok"))
                  .listen(0, ar -> { if (ar.succeeded()) { port = ar.result().actualPort(); fut.complete(port);} else fut.completeExceptionally(ar.cause()); });
            fut.get();
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Skipping HTTPS E2E due to TLS setup issue: " + e.getMessage());
        }
    }

    @AfterAll
    static void stop() throws Exception {
        java.util.concurrent.CompletableFuture<Void> fut = new java.util.concurrent.CompletableFuture<>();
        server.close(ar -> fut.complete(null));
        fut.get();
        vertx.close();
    }

    @Test
    void https_with_insecure_trustall_succeeds() throws Exception {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-OF-SSL-Insecure", List.of("true"));
        headers.put("X-OF-Connect-Timeout-Ms", List.of("500"));
        headers.put("X-OF-Read-Timeout-Ms", List.of("1000"));
        URI uri = URI.create("https://127.0.0.1:" + port + "/t");
        HttpRequest req = new HttpRequest("GET", uri, headers, null);
        HttpResponse resp = ad.execute(req);
        assertEquals(200, resp.statusCode());
        assertEquals("GET", first(resp.headers(), "X-Echo"));
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}
