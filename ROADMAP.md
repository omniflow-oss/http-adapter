# ROADMAP — OFKit Adapter HTTP

This roadmap summarizes what is not implemented yet and lists additional, high‑value features to pursue. It is organized by themes with suggested milestones and acceptance criteria.

## Status Summary

Implemented (high level)
- Domain: models (`Result`, `ProblemDetails`, `AdapterProfile`, `CachePolicy`, `RetrySpec`, `HttpClientSpec`, `SslSpec`, `AuthSpec`).
- Rules: `status`, `header.regex`, `body_regex`, `json(pointer: equals|regex|exists)`, `all` (AND), `produce.pick_pointer` extraction.
- Pipeline: `AdapterFacade`, `AuthGateway` (bearer, api_key), `CacheGateway` (ETag/Last‑Modified, SWR, SIE, negative caching, `max_body_kb`, `vary_headers`), `RetryGateway` (idempotent‑only, jittered backoff, honors Retry‑After).
- Infra: HTTP ports (`JdkHttpClientAdapter` default Alternative, `RestClientReactiveAdapter` Vert.x), REST resource `/adapter/{profile}/{path...}`, `InMemoryCacheStore`, Micrometer cache metrics, `YamlProfileRegistry` with JSON‑Schema validation, JSON console logs, `/q/metrics`, `/q/health`.

Not implemented (core gaps)
- Mocking: `MockGateway` and mock stores (ConfigMap/S3), scenario templating, signed trigger header, shadow recording.
- Auth providers: OAuth2 client credentials (token cache/refresh), HMAC, full mTLS (truststore/keystore, mutual auth selection per profile).
- Distributed cache: `RedisCacheStore` (TTL, invalidation, metrics), optional compression and key namespace.
- Tracing: OpenTelemetry spans/attributes (`profile`, `rule_id`, `upstream_status`, `cache.*`, `retry.attempt`).
- Secrets: Vault/KMS/SM adapter (resolve `${ENV:}`/`${SECRET:}` placeholders, rotation).
- Governance tooling: CLI and CI linter (profile validation, dangerous regex detection, required success rule), canary/shadow routing.
- Resilience: circuit breaker and bulkhead (per profile), client‑side rate limiting.
- Observability: request/success/error counters; latency histogram per profile; structured log redaction (header allowlist + JSON pointer redaction).
- Retry classifier: rule‑driven classification (`retriable_rules` in profiles), retry budget limits.
- SSL hardening: cipher/protocol selection, hostname verification modes, optional certificate pinning (SPKI/CA pinning).
- Streaming/limits: request/response streaming for large payloads; enforce request `max_body_kb`; gzip/deflate support; content encoding handling in cache.
- Profile management: hot‑reload, profile versioning, staged rollout (canary 5–10%), shadow compare.
- Native build (GraalVM): reflection config for Jackson usage in RuleEngine/JsonPointer, vertx/jaxrs substitutions, native smoke tests.

## Milestones

Milestone M1 — Mocking & Governance
- Implement `MockGateway` with ConfigMap store; support route matchers (method + path regex + headers) and optional signed trigger header.
- Add simple templating for golden bodies; add shadow recording option.
- Provide `profiles-schema.json` linter CLI: `validate`, `lint-regex`, `explain`.
- Acceptance: run WireMock/MockGateway parity tests; CLI fails invalid profiles in CI.

Milestone M2 — Auth & Secrets
- Add OAuth2 client credentials with in‑memory token cache and proactive refresh.
- Add HMAC auth provider; add profile options for mTLS (truststore, keystore, protocols/ciphers).
- Add Secrets adapter (Vault/KMS/SM abstraction) used by profile loading.
- Acceptance: e2e tests for bearer/OAuth2/HMAC/mTLS; zero secret in logs.

Milestone M3 — Distributed Cache & Resilience
- Add `RedisCacheStore` with metrics, key namespace, compression option; negative cache segregation.
- Add Circuit Breaker and client‑side rate limiting (token bucket per profile).
- Acceptance: e2e cache tests pass with Redis; CB opens/closes; rate limiter caps RPS.

Milestone M4 — Observability & Tracing
- Add request counters: `requests_total{profile,method}`, `success_total{profile,rule_id}`, `errors_total{profile,rule_id,type}`.
- Add latency histogram `latency_ms{profile}` in Micrometer.
- Integrate OpenTelemetry traces with attributes (`profile`, `rule_id`, `cache.*`, `retry.attempt`).
- Add structured log redaction: header allowlist + JSON pointer redaction.
- Acceptance: dashboards show metrics; traces show attributes; logs redact configured fields.

Milestone M5 — Retry & SSL Hardening
- Implement rule‑driven retry classifier (`retriable_rules` in profiles) and retry budget.
- Add TLS pinning options and explicit cipher/protocol control in profiles.
- Acceptance: retry classification honored in tests; pinning blocks bad certs.

Milestone M6 — Profiles & Deployment
- Profile hot‑reload; versioning with canary/shadow (header or percentage based).
- Provide Helm chart/Kubernetes manifests (ConfigMap for profiles, Service, resources, liveness/readiness).
- Acceptance: canary 5–10% without regression; profile reload visible in logs/metrics.

Milestone M7 — Native & Performance
- GraalVM native image support (reflection configs; substitutions for Jackson/Vert.x where needed).
- Performance budget and benchmarks; end‑to‑end P99 ≤ target; memory footprint target.
- Acceptance: native smoke suite passes; perf budget met in CI benchmark job.

## Detailed Backlog (by theme)

Mocking
- Mock store backends: ConfigMap (K8s), S3/Blob.
- Scenario templating: header/body interpolation; date/uuid helpers.
- Signed trigger header (HMAC/SHA256); tenant allowlist.
- Shadow recording with sampling; audit trail.

Authentication & Secrets
- OAuth2 CC: discovery (optional), token endpoint, scopes, audience; clock skew tolerance; token prefetch.
- HMAC: canonical request builder (headers, method, path, query, body hash); clock window.
- mTLS: keystore/truststore loading, protocol/cipher policy; per‑profile toggle.
- Secrets providers: Vault (AppRole/JWT), AWS Secrets Manager/KMS, GCP Secret Manager; caching and rotation.

Cache
- Redis backend with configurable TTLs, `swr/sie` support, eviction metrics; optional snappy/gzip body compression.
- Negative cache policy by status set; programmatic invalidation API.
- Vary on `accept-encoding`; conditional requests for HEAD/If‑Range; 206 partial content (future).

Resilience
- Circuit breaker (failure rate/time‑based) and bulkhead (concurrency) per profile.
- Client‑side rate limiting (token bucket/leaky bucket) per profile.
- Retry budgets and jitter strategies (full/decorr) with upper bounds.

Observability
- Metrics: requests/success/errors counters; latency histograms; per‑rule counters; upstream status distribution.
- Tracing: spans for Auth/Cache/Http/Rules; baggage propagation (`x-request-id`).
- Logs: redaction config (header allowlist, JSON pointer list); correlation id propagation; sampling knobs.

HTTP & SSL
- Reactive client HTTP/2 tuning, pools separation per base URL; proxy support.
- TLS: protocol/cipher lists; hostname verification policy; certificate/SPKI pinning.

Profiles & Governance
- Hot‑reload, versioning, canary/shadow strategies; profile includes/imports.
- CLI: `validate`, `lint`, `run --samples`, `explain --why <response>` to show matched rule.
- Lints: missing success rule, catastrophic regex, missing cache ttl when cache enabled, security lints (no secrets in YAML).

Native & Performance
- Reflection configs for Jackson JSON Pointer usage; reduce reflection reliance in domain (optional pluggable JSON handler).
- Vert.x/JAX‑RS substitutions; startup time/memory benchmarks.

Developer Experience
- Example profiles and golden scenarios for common upstreams.
- Minimal Quickstart with Helm chart and WireMock demo.

## Acceptance Criteria Recap
- Mocking, Redis, Auth/Secrets, Observability, Resilience, Profiles/CLI, Native support delivered per milestones with passing unit/e2e tests and updated docs.

## Tracking
- Use labels: `feature`, `infra`, `observability`, `security`, `cache`, `auth`, `mock`, `cli`, `native`.
- Link issues/PRs to the corresponding milestone (M1..M7).

