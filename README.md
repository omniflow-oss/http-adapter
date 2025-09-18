# OFKit Adapter HTTP (Omniflow)

![CI](https://github.com/omniflow-oss/http-adapter/actions/workflows/ci.yml/badge.svg?branch=main)
![Coverage](.github/badges/jacoco.svg)

Build a configurable, resilient, observable, secure, mockable HTTP adapter for Omniflow, running on Quarkus (JDK 17). The adapter standardizes upstream HTTP integrations by applying an inverted response evaluation logic and by concentrating cross‑cutting concerns (auth, retry, cache, observability, security) behind a clean, hexagonal boundary.

- Inverted logic per response: success rules → specific error rules → generic RFC‑7807
- HTTP via JDK HttpClient by default (CDI alternative); reactive Vert.x WebClient adapter available
- Config‑first behavior via YAML profiles (validated by JSON‑Schema)
- Auth providers (bearer, api_key), SSL options (insecure toggle)
- Retry policy (max retries, exponential backoff with jitter, respect Retry‑After, idempotent‑only)
- Cache with ETag/304, stale‑while‑revalidate (SWR), stale‑if‑error (SIE), negative caching, LRU cap
- Observability: cache metrics via Micrometer, JSON logs; tracing planned
- GraalVM‑friendly design in the domain (no heavy reflection; optional Jackson via reflection)


## Architecture (Hexagonal)

Domain (pure Java) contains models, rules, ports; application orchestrates the pipeline; infrastructure adapts Quarkus, HTTP, metrics, config.

```
Connector -> AdapterFacade
              ├─ AuthGateway (Strategy)
              ├─ CacheGateway (SWR/SIE/304)
              ├─ HttpPort (Reactive REST client)
              ├─ RuleEngine (success → errors → generic)
              ├─ ProblemFactory (RFC-7807)
              ├─ Retry (idempotent + Retry-After)
              └─ Observability (metrics/traces/logs)
```


## Modules

```
ofkit-adapter-http/
├─ adapter-http-domain/          # Pure domain (models, rules, ports)
├─ adapter-http-app/             # Orchestration (AdapterFacade + gateways)
├─ adapter-http-infra/           # Quarkus adapters (HTTP, config, metrics, CDI)
└─ adapter-http-tests/           # TCK & E2E tests (WireMock, golden scenarios)
```


## Integration

- For using the adapter from another Quarkus connector (sidecar or embedded), see `HOWTOUSE.md`.
- For extension points (ports/gateways/CDI wiring), see `EXTEND.md`.


## Quickstart

Prerequisites
- JDK 17+
- Maven 3.9+

Build & test
- `mvn test`
- `mvn install`

Run dev (Quarkus)
- `cd adapter-http-infra`
- `mvn quarkus:dev -Dquarkus.enforceBuildGoal=false`
- Health: GET `http://localhost:8081/q/health`
- Metrics: GET `http://localhost:8081/q/metrics`

Try the adapter endpoint
- The adapter exposes a generic proxy: `/adapter/{profile}/{path...}`
- Base URL resolution (precedence):
  1) `X-OF-Target-Base` header (if present)
  2) profile `base_url`
  3) otherwise HTTP 400 (missing)

Examples
- `curl -v -H 'X-OF-Target-Base: https://httpbin.org' 'http://localhost:8081/adapter/default/get?foo=bar'`


## Using Inside Connectors

Two recommended integration modes:

1) Sidecar HTTP proxy
- Deploy this adapter next to your connector and call it over HTTP.
- Call `http://<adapter-host>:8081/adapter/{profile}/{path...}` with the same method and query.
- Provide `X-OF-Target-Base` only if the profile has no `base_url` or you need to override it per-call.
- The adapter returns either the upstream response (success rule matched) or an RFC‑7807 problem JSON (error or generic).

Example
- GET passthrough via profile `accounts_api`:
  - `curl -v \
     -H 'x-request-id: 123' \
     -H 'accept: application/json' \
     'http://adapter:8081/adapter/accounts_api/v1/accounts?msisdn=336...'`
- Response headers added by the adapter:
  - `X-OF-Rule-Id` – matched rule id
  - `X-OF-Total-Latency-Ms` – end‑to‑end latency inside adapter
  - If cached: `X-OF-Cache` in `{hit|swr|sie|revalidate}`

Notes
- Timeouts, SSL, retry, and cache are controlled by the selected profile; no per‑request tuning is needed.
- Propagate correlation headers such as `x-request-id` from the connector to keep traces aligned.
- On problem results, the body is `application/problem+json` with `type`, `title`, `status`, and optional `detail`.

2) Embedded (in‑process SDK)
- Add dependencies to your connector and call the `AdapterFacade` directly.

Maven (connector `pom.xml`)
```xml
<dependency>
  <groupId>com.omniflow.ofkit</groupId>
  <artifactId>adapter-http-app</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>com.omniflow.ofkit</groupId>
  <artifactId>adapter-http-infra</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <!-- Brings CDI/Quarkus adapters for HttpPort and REST resource if needed -->
</dependency>
```

CDI usage (Quarkus)
```java
@Path("/connector")
@ApplicationScoped
public class ConnectorResource {
  @Inject AdapterFacade adapter;

  @GET
  @Path("/accounts")
  public Response accounts(@QueryParam("msisdn") String msisdn) throws Exception {
    var uri = java.net.URI.create("https://api.accounts.example.com/v1/accounts?msisdn=" + msisdn);
    var headers = java.util.Map.of("accept", java.util.List.of("application/json"));
    var req = new com.omniflow.ofkit.adapter.http.domain.model.HttpRequest("GET", uri, headers, null);
    var result = adapter.handle("accounts_api", req);
    if (result instanceof com.omniflow.ofkit.adapter.http.domain.model.Result.Success s) {
      var up = s.response();
      return Response.status(up.statusCode()).entity(up.body()).build();
    } else {
      var f = (com.omniflow.ofkit.adapter.http.domain.model.Result.Failure) result;
      var p = f.problem();
      var payload = new java.util.LinkedHashMap<String,Object>();
      payload.put("type", p.type()); payload.put("title", p.title()); payload.put("status", p.status());
      if (p.detail()!=null) payload.put("detail", p.detail());
      return Response.status(p.status()).entity(payload).type("application/problem+json").build();
    }
  }
}
```

Profiles in connectors
- Dev: use the built‑in `InMemoryProfileRegistry` (defaults `default`, `accounts_api`).
- Prod: implement `ProfileRegistry` to load YAML profiles (e.g., ConfigMap, S3, Vault‑backed secrets for tokens) and expose it as a CDI bean.

Example `ProfileRegistry`
```java
@ApplicationScoped
public class YamlProfileRegistry implements ProfileRegistry {
  private final java.util.Map<String, AdapterProfile> byId = new java.util.HashMap<>();
  public YamlProfileRegistry() {
    // load YAML and map to AdapterProfile(s); omitted for brevity
  }
  public java.util.Optional<AdapterProfile> findById(String id) { return java.util.Optional.ofNullable(byId.get(id)); }
}
```

Connector best practices
- Prefer idempotent methods for retried calls; respect `Retry-After`.
- Forward `x-request-id` and avoid leaking `Authorization` into cache vary headers.
- Keep payloads under `max_body_kb` when enabling cache.
- Do not log PII bodies at INFO; rely on adapter logs/metrics for observability.


## Configuration (Profiles)

Profiles are YAML files validated by `profiles-schema.json` and loaded from the classpath under `profiles/*.yaml`.

Minimal example (with full options excerpt):

```yaml
ofkit:
  http:
    profiles:
      accounts_api:
        base_url: https://api.accounts.example.com
        timeouts: { connect_ms: 1500, read_ms: 3000 }
        ssl: { insecure: false }
        auth: { kind: bearer, bearer: { token: ${ACCOUNTS_TOKEN:} } }
        retry:
          enabled: true
          max_retries: 2
          initial_delay_ms: 100
          max_delay_ms: 800
          jitter: true
          respect_retry_after: true
          idempotent_only: true
        cache:
          enabled: true
          default_ttl_s: 60
          validators: { use_etag: true, use_last_modified: true }
          swr_ttl_s: 30
          sie_ttl_s: 30
          vary_headers: ["accept"]
          max_body_kb: 512
          negative_ttl_s: 30
        rules:
          success:
            - id: ok-2xx-json-flag
              when:
                all:
                  - status: "200-299"
                  - json: { pointer: "/status", equals: "OK" }
              produce: { type: json, pick_pointer: "/data" }
          errors:
            - id: err-429
              when: { status: "429" }
              problem:
                type: "https://omniflow/rate-limit"
                title: "Rate limited"
                status: 429
        generic_problem:
          type: "about:blank"
          title: "Erreur service externe"
          status: 502
```

Rule DSL supported
- `status`: single code or range, e.g. `"200-299"`, `"404"`
- `header`: `{ name, regex }` (case‑insensitive header name)
- `body_regex`: string regex (applies to UTF‑8 body)
- `json`: `{ pointer, equals|regex|exists }` (JSON Pointer)
- `all`: array of nested conditions (logical AND)

Produce (success)
- `produce.pick_pointer`: optional extraction of a JSON subtree from the body (serialized back to JSON)

Auth
- `auth.kind`: `none`, `bearer`, `api_key`
  - bearer: `bearer.token`
  - api_key: `api_key.header`, `api_key.value`

HTTP
- `timeouts.connect_ms`, `timeouts.read_ms`
- `ssl.insecure`: `true` to trust all (dev/INT only)
- `pool` (connection pool):
  - `max_pool_size` (default 50)
  - `max_wait_queue` (default -1: unlimited)
  - `keep_alive` (default true)
  - `keep_alive_timeout_s` (default 60)

Retry
- `enabled`, `max_retries`, `initial_delay_ms`, `max_delay_ms`, `jitter`, `respect_retry_after`, `idempotent_only`

Cache
- `default_ttl_s`, `swr_ttl_s`, `sie_ttl_s`, `vary_headers`, `validators.use_etag/use_last_modified`
- `max_body_kb` (store only if body is small enough)
- `negative_ttl_s` (cache non‑2xx as negative entries)


## Observability

Metrics (Prometheus)
- Implemented: cache counters `…_cache_hits_total/misses_total/swr_total/sie_total/revalidate_total/negative_total/evictions_total`
- Planned: request/latency/success/error counters and histograms

Tracing
- Planned attributes: `profile`, `rule_id`, `upstream_status`, `cache.hit`, `cache.swr`, `cache.sie`, `retry.attempt`

Logs
- JSON console logs enabled. Be mindful of PII; redact sensitive fields.


## Configuration Reference

- Profiles auto‑discovery and includes (YamlProfileRegistry):
  - Auto‑loads classpath resources matching `profiles/*.yaml`
  - Pin explicit list with `ofkit.http.profiles.includes=profiles/default.yaml,profiles/accounts_api.yaml`
- CDI wiring (default beans):
  - `RestClientReactiveAdapter` for HTTP, `InMemoryCacheStore` for cache, `MicrometerMetricsAdapter` for metrics (if Micrometer is present)
  - `InMemoryProfileRegistry` is enabled as a CDI `@Alternative` by default (dev/demo). To use YAML profiles in production, provide your own `ProfileRegistry` that delegates to `YamlProfileRegistry`, or disable the alternative in your build.
  - No MicroProfile Config toggles are implemented for swapping HTTP/cache implementations; override by supplying CDI beans.

## Development & Testing

Run unit & TCK tests
- Unit only: `mvn test`
- With e2e (sockets/Vert.x/WireMock): `mvn -Pe2e test`

Test coverage (JaCoCo)
- Only the `adapter-http-tests` module generates JaCoCo reports (aggregated for all modules).
- Unit coverage only: `mvn verify`
- Unit + e2e coverage: `mvn -Pe2e verify`
- Or: `mvn -pl adapter-http-tests test && mvn jacoco:report-aggregate`
  - `adapter-http-tests/target/site/jacoco-aggregate/index.html`

Test catalog
- `TESTS.md` describes the intended coverage; current repository contains a minimal testing baseline to bootstrap further work.

Notes
- e2e tests are tagged `@Tag("e2e")` and are excluded by default; enable them with `-Pe2e`.
- Current target: ≥80% line coverage aggregated across modules (achieved when running with `-Pe2e`).

TCK layout (in `adapter-http-tests`)
- `src/test/resources/tck/profiles/*.yaml` – profile configs
- `src/test/resources/tck/scenarios/**.yaml` – WireMock‑driven scenarios
- `src/test/resources/golden/**` – golden bodies for upstream responses
- Parameterized runner loads the profile/scenario, stubs WireMock, invokes `AdapterFacade`, and asserts `Result.Success/Failure`, `rule_id`, and status.

E2E tests included
- Retry: 5xx→success, Retry‑After honored, no retry on POST when idempotent‑only
- Cache: ETag/304, SWR, SIE, negative caching, LRU eviction counter
- Auth: bearer and API key header injection

Quarkus dev tips
- If dev mode is skipped as “support library”, run: `mvn quarkus:dev -Dquarkus.enforceBuildGoal=false`
- Port conflicts: default dev port is 8081; change via `quarkus.http.port`
- Run from module: `cd adapter-http-infra && mvn quarkus:dev`


## Security
- Secrets via env/secret stores, not plain YAML (e.g., `${TOKEN:}` placeholders)
- Header allowlist for outbound logging
- TLS: secure by default; `ssl.insecure` only for non‑prod


## Roadmap

For the detailed plan and milestones, see [ROADMAP.md](ROADMAP.md).
- Additional auth providers: OAuth2 client credentials, HMAC, mTLS
- Redis cache backend
- Mock store (ConfigMap/S3) and prod‑safe mocking with signatures
- Tracing spans and structured logs with rule_id/end‑to‑end correlation
- Profile validation CLI and CI linter (catastrophic regex detection)


## License
TBD (project‑specific)
