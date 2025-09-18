
# Extending the OFKit HTTP Adapter

This file describes supported extension points and how to add or swap concrete implementations without modifying core logic. The adapter follows a hexagonal architecture: the domain defines ports (interfaces) and the application composes them via gateways; infrastructure provides CDI‑wired adapters.

Guiding principles
- Domain‑pure: extension interfaces live in `adapter-http-domain` (no Quarkus types).
- Swap by CDI: provide alternative beans in `adapter-http-infra` (or your connector) and inject them.
- Config‑first: prefer behavior via profiles before code forks.
- GraalVM‑friendly: avoid heavy reflection, dynamic classloading, or unsupported SPI.

## Domain Ports (implement/replace)

- `HttpPort`
  - Purpose: execute an HTTP request and return a response (non‑blocking preferred).
  - Defaults: `JdkHttpClientAdapter` (CDI `@Alternative`, active by default) and `RestClientReactiveAdapter` (Vert.x).
  - Extend: create `class MyHttpPort implements HttpPort`, register as CDI bean with `@ApplicationScoped @Alternative @Priority(1)` in infra and ensure it’s chosen.
  - Notes: The application passes HTTP options via `X-OF-*` headers (timeouts, pool, SSL). Your adapter can consume them or ignore as needed.

- `CacheStore`
  - Purpose: store/retrieve HTTP responses with validators (ETag/Last‑Modified) and negative entries.
  - Default: `InMemoryCacheStore` (bounded LRU).
  - Extend: implement a Redis or distributed cache store `class RedisCacheStore implements CacheStore` and wire it in `Producers` (or your connector’s CDI config).
  - Notes: Keep keys stable and respect `invalidate` for programmatic busting.

- `MetricsPort`
  - Purpose: record counters for cache events and (optionally) request metrics.
  - Default: `MicrometerMetricsAdapter` (infra). If absent, the adapter still works.
  - Extend: implement `MetricsPort` and bind in CDI; `CacheGateway` accepts a metrics bean in its constructor.

(Pluggable in future)
- Tracing port, Mock store, Secrets port are planned; design mirrors `MetricsPort` (define port in domain → implement in infra → compose in app).

## Application Gateways (compose/swap)

- `AuthGateway`
  - Injects auth headers from profile `AuthSpec` into `HttpRequest`.
  - Default supports `bearer` and `api_key`.
  - Extend: add new variants to `AuthSpec` (domain) and teach `AuthGateway` to apply them; or implement an alternative gateway with custom policies (HMAC, OAuth2 CC) and wire it in `Producers`.

- `CacheGateway`
  - Adds validators, SWR/SIE, negative caching, metrics.
  - Replaceable as a unit if you need custom cache keying or eviction; typically you swap `CacheStore` not the gateway.

- `RetryGateway`
  - Applies idempotent‑aware retry with exponential backoff/jitter and `Retry-After`.
  - Extend: customize classification or backoff (e.g., circuit breaker) by providing an alternative gateway with the same signature and using it in `AdapterFacade` construction.

- `AdapterFacade`
  - Orchestrates pipeline: Auth → Cache → Http → RuleEngine → Problem mapping → Retry and headers.
  - Compose: Provide a custom `Producers` to assemble your preferred `HttpPort`, `CacheStore`, `MetricsPort`, and gateways.

## Infra Wiring

- `Producers` (infra) creates a default `AdapterFacade` with:
  - `RestClientReactiveAdapter` (Vert.x)
  - `InMemoryCacheStore`
  - `RetryGateway`, `AuthGateway`, `RuleEngine`, `YamlProfileRegistry`
- Override in your connector:
  - Provide your own CDI producer for `AdapterFacade` (with custom `HttpPort`, `CacheStore`, `MetricsPort`).
  - Mark with `@Alternative @Priority(1)` to take precedence.

Strategy selection
- No MicroProfile Config toggles are implemented for swapping HTTP/cache implementations. Swap by providing your own CDI beans (alternatives) or custom `Producers`.

Auto‑discovery of profiles
- Profiles are auto‑loaded from all classpath resources matching `profiles/*.yaml` (works in dev and packaged JARs).
- Pin an explicit list with `ofkit.http.profiles.includes=profiles/a.yaml,profiles/b.yaml`.

## Profiles & Config Schema

- Profiles are YAML documents validated by `profiles-schema.json` (infra resources):
  - `rules.success/errors/generic_problem`
  - `retry.enabled/max_retries/initial_delay_ms/max_delay_ms/jitter/respect_retry_after/idempotent_only`
  - `cache.enabled/default_ttl_s/swr_ttl_s/sie_ttl_s/validators/vary_headers/max_body_kb/negative_ttl_s`
  - `auth.kind: none|bearer|api_key` and fields
  - `timeouts.connect_ms/read_ms`, `ssl.insecure`, `pool.max_pool_size/max_wait_queue/keep_alive/keep_alive_timeout_s`
- Extend schema (carefully):
  - To support a new Auth kind, first add a new `AuthSpec` variant in domain; extend `YamlProfileRegistry` parsing and `AuthGateway`.
  - To support new rule predicates, extend domain predicates (`rules/*Predicate`) and teach `YamlProfileRegistry#parseWhen` to map from YAML to predicate.

Reusable mapping
- Use `com.omniflow.ofkit.adapter.http.infra.config.ProfileMapper` in connectors to map YAML `JsonNode` to `AdapterProfile` (avoids copy‑pasting infra mapping code).

## Observability

- Metrics: `MicrometerMetricsAdapter` implements `MetricsPort` for cache counters. You can provide an alternative adapter or add more metrics.
- Tracing: planned. Add a new `TracerPort` (domain) + `OtelTracerAdapter` (infra) and integrate without leaking tracing types into the domain.
- Logs: `AdapterResource` sets structured headers; for redaction, add a logging filter in infra.

## Testing your extensions

- Unit tests in `adapter-http-tests` are a good template:
  - `*Http*E2ETest` for reactive clients with Vert.x servers.
  - Cache tests for SWR/SIE/validators; negative caching; vary headers; metrics.
  - Parsing tests for YAML schema and mapping to predicates/specs.
- e2e: enable group with `mvn -Pe2e test` to validate sockets/SSL/pooling behavior.

## Examples

- Custom HttpPort (OkHttp example, pseudo‑code)
```java
@ApplicationScoped @Alternative @Priority(1)
public class OkHttpPort implements HttpPort {
  private final okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
  public HttpResponse execute(HttpRequest req) throws Exception {
    okhttp3.Request.Builder b = new okhttp3.Request.Builder().url(req.uri().toString());
    // set method/body
    // set headers
    long t0 = System.nanoTime();
    okhttp3.Response r = client.newCall(b.build()).execute();
    long durMs = (System.nanoTime()-t0)/1_000_000L;
    Map<String,List<String>> h = new java.util.HashMap<>();
    r.headers().toMultimap().forEach((k,v)->h.put(k,List.copyOf(v)));
    h.computeIfAbsent("X-OF-Upstream-Latency-Ms", k->new java.util.ArrayList<>()).add(Long.toString(durMs));
    return new HttpResponse(r.code(), h, r.body()!=null? r.body().bytes(): new byte[0]);
  }
}
```

- Custom CacheStore (Redis outline)
```java
@ApplicationScoped @Alternative @Priority(1)
public class RedisCacheStore implements CacheStore {
  // inject redis client
  public Optional<CachedEntry> get(CacheKey k){ /* read bytes + decode */ }
  public void put(CacheKey k, CachedEntry e){ /* write with TTL */ }
  public void invalidate(CacheKey k){ /* delete key */ }
}
```

- Custom MetricsPort (Micrometer binding)
```java
@ApplicationScoped
public class MicrometerMetricsAdapter implements MetricsPort {
  private final Counter hit = Counter.builder("omniflow_adapter_cache_hits_total").register(reg);
  // ... other counters
  public void incrementCacheHit(String p){ hit.increment(); }
  // ...
}
```

## Conventions
- Don’t leak infra types into domain/application layers.
- Keep latency/headers contract (`X-OF-*`), especially for `HttpPort` implementations.
- Keep methods non‑blocking where possible; if using blocking I/O, ensure timeouts are properly enforced.
- Validate profiles against the JSON schema; consider adding CI linting for your custom profiles.
