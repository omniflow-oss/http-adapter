package com.omniflow.ofkit.adapter.http.infra.http;

import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class RestClientReactiveAdapter implements HttpPort {
    private final Vertx vertx;
    private final ConcurrentMap<PoolKey, WebClient> clients = new ConcurrentHashMap<>();

    public RestClientReactiveAdapter() {
        this.vertx = Vertx.vertx();
        // Default client
        clients.put(new PoolKey(false, 5000, 50, -1, true, 60), WebClient.create(vertx, new WebClientOptions()));
        // Default insecure client
        WebClientOptions insecure = new WebClientOptions().setTrustAll(true).setVerifyHost(false).setSsl(true);
        clients.put(new PoolKey(true, 5000, 50, -1, true, 60), WebClient.create(vertx, insecure));
    }

    @Override
    public HttpResponse execute(HttpRequest request) throws Exception {
        HttpMethod method = HttpMethod.valueOf(request.method().toUpperCase());
        CompletableFuture<HttpResponse> fut = new CompletableFuture<>();
        boolean insecure = HttpHeaderUtils.hasHeaderTrue(request.headers(), "X-OF-SSL-Insecure");
        long readTimeout = HttpHeaderUtils.parseLongHeader(request.headers(), "X-OF-Read-Timeout-Ms", 10000);
        int connectMs = (int) HttpHeaderUtils.parseLongHeader(request.headers(), "X-OF-Connect-Timeout-Ms", 5000);
        int maxPool = (int) HttpHeaderUtils.parseLongHeader(request.headers(), "X-OF-HTTP-Max-Pool-Size", 50);
        int waitQ = (int) HttpHeaderUtils.parseLongHeader(request.headers(), "X-OF-HTTP-Max-Wait-Queue", -1);
        boolean keepAlive = HttpHeaderUtils.hasHeaderTrue(request.headers(), "X-OF-HTTP-Keep-Alive");
        int keepAliveS = (int) HttpHeaderUtils.parseLongHeader(request.headers(), "X-OF-HTTP-Keep-Alive-Timeout-S", 60);
        WebClient client = clients.computeIfAbsent(new PoolKey(insecure, connectMs, maxPool, waitQ, keepAlive, keepAliveS), k -> {
            WebClientOptions opts = new WebClientOptions()
                    .setConnectTimeout(connectMs)
                    .setKeepAlive(keepAlive)
                    .setKeepAliveTimeout(keepAliveS)
                    .setMaxPoolSize(maxPool)
                    .setMaxWaitQueueSize(waitQ);
            if (insecure) opts.setSsl(true).setTrustAll(true).setVerifyHost(false);
            return WebClient.create(vertx, opts);
        });
        long start = System.nanoTime();
        var req = client.requestAbs(method, request.uri().toString());
        req.timeout(readTimeout);
        if (request.headers() != null) request.headers().forEach((k, vs) -> vs.forEach(v -> req.putHeader(k, v)));
        var future = (request.body() == null) ? req.send() : req.sendBuffer(Buffer.buffer(request.body()));
        future.onSuccess(resp -> {
                    Map<String, List<String>> headers = new HashMap<>();
                    resp.headers().forEach(entry -> headers.computeIfAbsent(entry.getKey(), kk -> new ArrayList<>()).add(entry.getValue()));
                    long durMs = (System.nanoTime() - start) / 1_000_000L;
                    headers.computeIfAbsent("X-OF-Upstream-Latency-Ms", k -> new ArrayList<>()).add(Long.toString(durMs));
                    io.vertx.core.buffer.Buffer body = resp.bodyAsBuffer();
                    fut.complete(new HttpResponse(resp.statusCode(), headers, body == null ? new byte[0] : body.getBytes()));
                })
              .onFailure(err -> fut.completeExceptionally(err));

        return fut.get(15, TimeUnit.SECONDS);
    }

    // header helpers moved to HttpHeaderUtils

    private record PoolKey(boolean insecure, int connectMs, int maxPool, int waitQueue, boolean keepAlive, int keepAliveS) { }
}
