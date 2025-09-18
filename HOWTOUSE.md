# How to Use the HTTP Adapter from a Quarkus Connector

This guide shows two integration modes for a Quarkus connector:

- Sidecar HTTP proxy (call the adapter over HTTP)
- Embedded SDK (call the adapter pipeline in‑process via CDI)

Prerequisites

- Java 17+, Maven 3.9+
- Add this repo as a dependency (local install or artifact repository)

## 1) Sidecar HTTP Proxy

When the adapter runs as a service, the connector calls a generic proxy route:

- `http://<adapter-host>:8081/adapter/{profile}/{path...}`
- Method, query string and most headers are forwarded to the upstream.

Typical call

- `GET http://adapter:8081/adapter/accounts_api/v1/accounts?msisdn=336...`
- Optional header `X-OF-Target-Base: https://api.accounts.example.com` overrides `base_url` from the selected profile.

Responses

- Success: passthrough of upstream status/headers/body, with added headers:
  - `X-OF-Rule-Id`: matched rule id
  - `X-OF-Total-Latency-Ms`: end‑to‑end latency inside adapter
  - Optional `X-OF-Cache: hit|swr|sie|revalidate`
- Failure: `application/problem+json` (RFC‑7807) with `type`, `title`, `status`, optional `detail` and extensions.

Connector tips

- Propagate correlation (`x-request-id`) and `accept` headers from connector.
- Avoid including `Authorization` in cache vary headers unless necessary.
- Rely on profiles for timeouts/SSL/retry/cache; no per‑request tuning needed.

## 2) Embedded SDK (CDI)

Add dependencies (connector `pom.xml`)

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
</dependency>
```

Provide profiles

- Dev/POC: by default, `InMemoryProfileRegistry` is enabled as a CDI `@Alternative` with example profiles. You can also bundle YAML profiles under `src/main/resources/profiles/*.yaml` in your connector (schema in `adapter-http-infra/src/main/resources/profiles-schema.json`).
- Prod: implement `ProfileRegistry` to load YAML (ConfigMap/S3) and secrets via Vault/KMS; register it as a CDI bean. Use `YamlProfileRegistry` from the infra module or delegate to it.

Add profiles to your connector (classpath)

- Create files under `src/main/resources/profiles/your_profile.yaml` using the schema.
- At startup, load and validate them before constructing `AdapterFacade`.

Example: simple `ProfileRegistry` loading classpath YAML and validating schema

```java
@ApplicationScoped
public class YamlClasspathProfileRegistry implements ProfileRegistry {
  private final Map<String, AdapterProfile> byId = new java.util.concurrent.ConcurrentHashMap<>();

  public YamlClasspathProfileRegistry() {
    try {
      var cl = Thread.currentThread().getContextClassLoader();
      var yaml = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
      var json = new com.fasterxml.jackson.databind.ObjectMapper();
      var schemaFactory = com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012);
      try (var schemaIs = cl.getResourceAsStream("profiles-schema.json")) {
        var schema = schemaFactory.getSchema(schemaIs);
        for (String res : java.util.List.of("profiles/default.yaml","profiles/accounts_api.yaml")) {
          try (var is = cl.getResourceAsStream(res)) {
            if (is == null) continue;
            var root = yaml.readTree(is);
            var errors = schema.validate(root);
            if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid profile " + res + ": " + errors);
            var profilesNode = root.path("ofkit").path("http").path("profiles");
            var it = profilesNode.fieldNames();
            while (it.hasNext()) {
              String pid = it.next();
              var p = profilesNode.get(pid);
              byId.put(pid, toProfile(pid, p)); // reuse same mapping as adapter-http-infra
            }
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load profiles", e);
    }
  }

  public java.util.Optional<AdapterProfile> findById(String id) { return java.util.Optional.ofNullable(byId.get(id)); }

  private static AdapterProfile toProfile(String id, com.fasterxml.jackson.databind.JsonNode p) {
    import com.omniflow.ofkit.adapter.http.infra.config.ProfileMapper;
    // ...
    return ProfileMapper.INSTANCE.toProfile(id, p); // via CDI or MapStruct instance
  }
}
```

Wiring with CDI (override Producers)

- Provide your own `@Produces AdapterFacade` if you need custom wiring (e.g., Redis cache, OkHttp `HttpPort`, Micrometer metrics).
- Mark with `@Alternative @Priority(1)` so it takes precedence over the default infra `Producers`.

Example CDI producer in a connector (strategy via CDI)

```java
@ApplicationScoped
public class ConnectorProducers {
  @Produces @ApplicationScoped
  public AdapterFacade adapterFacade(YamlClasspathProfileRegistry profiles) {
    // Provide your own beans to swap implementations (no MicroProfile toggles implemented).
    // Default in infra is a JDK HttpClient adapter (enabled as @Alternative).
    // To use the reactive Vert.x adapter, wire it explicitly as below.
    HttpPort http = new com.omniflow.ofkit.adapter.http.infra.http.RestClientReactiveAdapter();
    RuleEngine re = new RuleEngine();
    CacheGateway cache = new CacheGateway(new com.omniflow.ofkit.adapter.http.infra.cache.InMemoryCacheStore());
    RetryGateway retry = new RetryGateway();
    AuthGateway auth = new AuthGateway();
    return new AdapterFacade(http, re, profiles, cache, retry, auth);
  }
}
```

Secrets and placeholders

- Reference secrets via `${ENV:}` placeholders in YAML profiles and resolve them in your `ProfileRegistry` (e.g., from Vault/KMS).
- Do not store cleartext secrets in Git.

Multi‑tenant and multiple profiles

- Add as many entries under `ofkit.http.profiles.*` as needed and use the profile id in your connector (`adapter.handle("<profile>", req)`).
- Use naming like `accounts_api_eu`, `accounts_api_us` to target regions/tenants.

Example CDI resource in connector

```java
@Path("/connector")
@ApplicationScoped
public class ConnectorResource {
  @Inject AdapterFacade adapter;  // provided by adapter-http-infra

  @GET
  @Path("/accounts")
  @Produces("application/json")
  public Response accounts(@QueryParam("msisdn") String msisdn) throws Exception {
    var uri = java.net.URI.create("https://api.accounts.example.com/v1/accounts?msisdn=" + msisdn);
    var headers = java.util.Map.of("accept", java.util.List.of("application/json"),
                                   "x-request-id", java.util.List.of(java.util.UUID.randomUUID().toString()));
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
      if (p.extensions()!=null) payload.putAll(p.extensions());
      return Response.status(p.status()).entity(payload).type("application/problem+json").build();
    }
  }
}
```

Configuring profiles (YAML)

- Minimal example under `adapter-http-infra/src/main/resources/profiles/*.yaml`.
- Key fields per profile:
  - `base_url`, `rules.success/errors/generic_problem`
  - `retry.enabled/max_retries/initial_delay_ms/max_delay_ms/jitter/respect_retry_after/idempotent_only`
  - `cache.enabled/default_ttl_s/swr_ttl_s/sie_ttl_s/validators/vary_headers/max_body_kb/negative_ttl_s`
  - `auth.kind: none|bearer|api_key` and related fields
  - `timeouts.connect_ms/read_ms`, `ssl.insecure`, `pool.max_pool_size/max_wait_queue/keep_alive/keep_alive_timeout_s`

Observability

- Metrics (Prometheus): `GET /q/metrics` on the adapter Quarkus app. Cache metrics are implemented; request metrics/tracing are planned.
- Health: `GET /q/health`.
- Traces/logs: propagate `x-request-id`, avoid PII in INFO logs.

Security

- Do not store secrets in YAML; use `${ENV:}` placeholders and a secrets provider (Vault/KMS).
- Prefer `bearer` or `api_key` providers; configure header/value via env injection.

Testing in a connector

- Unit: mock `ProfileRegistry` and call `AdapterFacade` directly.
- Integration: use WireMock to stub upstream endpoints; verify rule matches and error mapping.
- e2e: run adapter as sidecar and hit `/adapter/{profile}/{path...}` from the connector.
- CI tip: include adapter e2e tests with `mvn -Pe2e test` to validate SSL, pool tuning, and timeouts.

Sidecar vs Embedded – when to choose

- Sidecar: polyglot connectors, hot profile reload, centralized observability.
- Embedded: lowest latency, fewer moving parts, straight CDI wiring in Quarkus.

Quick curl (sidecar)

```bash
curl -v \
  -H 'x-request-id: 123' \
  -H 'accept: application/json' \
  'http://localhost:8081/adapter/accounts_api/v1/accounts?msisdn=336...'
```

Troubleshooting

- Unknown profile → 404 from resource or IllegalArgumentException in embedded mode.
- No `base_url` nor `X-OF-Target-Base` → 400.
- Timeouts or SSL errors → tune `timeouts.*` / `ssl.insecure` in profile (dev only for insecure).
- Auto‑discovery (YamlProfileRegistry): auto‑loads classpath `profiles/*.yaml`. Set `ofkit.http.profiles.includes=a.yaml,b.yaml` to control the list explicitly. Ensure the YAML registry is used (disable the in‑memory alternative or provide your own `ProfileRegistry`).
