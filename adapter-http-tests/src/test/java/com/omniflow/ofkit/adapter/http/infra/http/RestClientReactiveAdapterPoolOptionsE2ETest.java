package com.omniflow.ofkit.adapter.http.infra.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
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
class RestClientReactiveAdapterPoolOptionsE2ETest {
    static Vertx vertx;
    static HttpServer server;
    static int port;

    @BeforeAll
    static void start() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer();
        java.util.concurrent.CompletableFuture<Integer> fut = new java.util.concurrent.CompletableFuture<>();
        server.requestHandler(req -> req.response().putHeader("X-Echo", req.method().name()).end("ok"))
              .listen(0, ar -> { if (ar.succeeded()) { port = ar.result().actualPort(); fut.complete(port);} else fut.completeExceptionally(ar.cause()); });
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
    void executes_with_pool_and_keepalive_options() throws Exception {
        RestClientReactiveAdapter ad = new RestClientReactiveAdapter();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        headers.put("X-OF-Connect-Timeout-Ms", List.of("300"));
        headers.put("X-OF-Read-Timeout-Ms", List.of("500"));
        headers.put("X-OF-HTTP-Max-Pool-Size", List.of("1"));
        headers.put("X-OF-HTTP-Max-Wait-Queue", List.of("0"));
        headers.put("X-OF-HTTP-Keep-Alive", List.of("false"));
        headers.put("X-OF-HTTP-Keep-Alive-Timeout-S", List.of("1"));

        URI uri = URI.create("http://127.0.0.1:" + port + "/t");
        HttpRequest req = new HttpRequest("GET", uri, headers, null);
        HttpResponse r1 = ad.execute(req);
        assertEquals(200, r1.statusCode());
        HttpResponse r2 = ad.execute(req);
        assertEquals(200, r2.statusCode());
        assertEquals("GET", first(r2.headers(), "X-Echo"));
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}

