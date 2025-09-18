# `AGENTS.md` — Adapter HTTP Omniflow

Ce document explique le problème, la solution, les exigences, l’architecture/structure du code et le plan de travail pour implémenter et tester l’adapter HTTP d’Omniflow. Il est conçu pour être concret et actionnable par des IA (Copilot/agents) et des devs juniors.

---

## 🎯 Mission

Construire un adapter HTTP standardisé (Quarkus + Mutiny + GraalVM) qui applique une logique inversée sur les réponses :

1. Vérifie les règles de succès.
2. Sinon, vérifie les règles d’erreur spécifiques.
3. Sinon, retourne une erreur générique RFC‑7807.

L’adapter doit être configurable, résilient, observable, sécurisé et mockable.

---

## 🧩 Problématique (ce qu’on veut corriger)

- Réponses amont incohérentes (ex. 200 OK avec erreur métier dans le corps).
- Erreurs non normalisées, messages hétérogènes difficiles à diagnostiquer.
- Performance & stabilité : appels bloquants, gros payloads, retentatives mal gérées, dépendance forte à la latence amont.
- Tests & mocks compliqués, manque de golden data reproductibles (même en prod).
- Sécurité & conformité fragiles : gestion des secrets, TLS, fuites en logs/cache.
- Observabilité insuffisante : peu de visibilité sur la règle matchée, latence, taux d’erreurs.
- Maintenance coûteuse : trop de code spécifique par upstream, config non gouvernée.

---

## 🧭 Solution (vue d’ensemble)

Un adapter HTTP standardisé, piloté par configuration, en Quarkus (REST Client Reactive + Mutiny) qui, pour chaque réponse amont :

- Vérifie d’abord les règles de succès (status, headers, body regex, checks JSON).
- Sinon, vérifie des règles d’erreur spécifiques.
- Sinon, renvoie une erreur générique RFC‑7807.

Autour du pipeline : Auth configurable (api‑key, bearer, OAuth2 CC, HMAC, mTLS), SSL configurable, Retry/Circuit‑Breaker, timeouts, rate limiting, Cache (in‑memory/Redis) avec ETag/304 + SWR + SIE, Mock prod‑safe via golden data, Observabilité (métriques, traces, logs structurés, redaction PII).

---

## 📌 État d’implémentation (aujourd’hui)

Implémenté
- Domain : `Result`, `ProblemDetails`, `AdapterProfile`, `CachePolicy`, `RetrySpec`, `HttpClientSpec`, `SslSpec`, `AuthSpec`.
- Règles : `status`, `header.regex`, `body_regex`, `json(pointer: equals|regex|exists)`, `all` (AND). Extraction `produce.pick_pointer` supportée.
- Application : `AdapterFacade`, `AuthGateway` (bearer, api_key), `CacheGateway` (ETag/Last‑Modified, SWR, SIE, negative caching, `max_body_kb`, `vary_headers`), `RetryGateway` (idempotent‑only, backoff + jitter, respect `Retry‑After`).
- Infrastructure : `JdkHttpClientAdapter` (CDI Alternative par défaut) et `RestClientReactiveAdapter` (Vert.x), ressource REST `/adapter/{profile}/{path...}`, `InMemoryCacheStore`, `MicrometerMetricsAdapter` (compteurs cache), `YamlProfileRegistry` (auto‑discovery `profiles/*.yaml` avec validation JSON‑Schema), logs JSON, endpoints `/q/metrics` et `/q/health`.

Pas encore
- MockGateway/MockStore, Redis cache, OAuth2 CC, HMAC, mTLS complet, tracing (OTel), Secrets/Vault adapter, linter CLI CI, canary/shadow routing.
- Commutateurs MicroProfile Config pour choisir dynamiquement les impl (HTTP/cache/metrics) : non implémentés. Surcharger via CDI (beans `@Alternative`) ou un `Producers` custom.

Par défaut
- `InMemoryProfileRegistry` est activé en `@Alternative` (dev/demo). Pour charger des profils YAML en prod, fournissez votre propre `ProfileRegistry` qui délègue à `YamlProfileRegistry` ou désactivez l’alternative.

---

## 🏗️ Architecture (hexagonale)

- Domain (Java pur) : modèles, règles, policies, ports.
- Application (CDI) : orchestration via `AdapterFacade`.
- Infrastructure (Quarkus) : REST Client Reactive, Auth providers, Cache (in‑memory/Redis), Mock store (ConfigMap/S3), Observabilité (Micrometer/OTel).
- Tests : TCK avec golden data, WireMock.

```
Connector -> AdapterFacade
              ├─ AuthGateway (Strategy)
              ├─ MockGateway (Golden data)  ← court-circuit si match
              ├─ CacheGateway (SWR/SIE/304) ← court-circuit si hit
              ├─ HttpPort (Reactive REST client)
              ├─ RuleEngine (success → error → generic)
              ├─ ProblemFactory (RFC-7807)
              ├─ Retry/CircuitBreaker + SIE fallback cache
              └─ Observability (metrics/traces/logs)
```

Principes :

- Hexagonal : cœur de domaine pur (pas de types Quarkus).
- SOLID/DRY : responsabilités séparées, extensions par stratégies.
- Config‑first : tout comportement piloté par YAML (Helm/ArgoCD).

---

## 📦 Modules & structure des packages

```
ofkit-adapter-http/
├─ adapter-http-domain/          # Java pur (tests rapides)
│  ├─ model/ (AdapterProfile, Result, ProblemDetails, CachePolicy, RetrySpec, AuthSpec, SslSpec)
│  ├─ rules/ (Predicates, RuleEngine)
│  ├─ ports/ (HttpPort, CacheStore, MockStore, MetricsPort, TracerPort, SecretsPort)
│  ├─ retry/ (RetryPolicy, Backoff, RetryClassifier)
│  └─ cache/ (CacheKey, CachedEntry)
├─ adapter-http-app/             # Orchestration (CDI)
│  ├─ AdapterFacade, ProfileRegistry, ProblemFactory, Observability
│  ├─ AuthGateway, CacheGateway, MockGateway, RetryGateway, Producers
├─ adapter-http-infra/           # Adapters Quarkus
│  ├─ http/ (RestClientReactiveAdapter, SslContextFactory)
│  ├─ auth/ (ApiKey, Bearer, OAuth2CC, Hmac, Mtls)
│  ├─ cache/ (InMemoryCacheStore, RedisCacheStore)
│  ├─ mock/ (ConfigMapMockStore, S3MockStore)
│  ├─ obs/  (MicrometerMetricsAdapter, OtelTracerAdapter)
│  └─ secrets/ (VaultSecretsAdapter) + config (ProfileFactory @ConfigMapping)
└─ adapter-http-tests/           # TCK & WireMock, golden data, CLI (validate/run)
```

---

## 📜 Naming conventions

- Wire (JSON) : `snake_case`.
- Java classes : `CamelCase` par couche (ex. `AdapterProfile`, `ProblemDetails`).
- DTOs : suffixe `Dto`.
- Ports (interfaces) : suffixe `Port` (ex. `HttpPort`, `CacheStore`).
- Strategies : suffixe `Provider` ou `Policy`.
- Rules : `success_*`, `error_*`, `generic_problem`.
- Config : `ofkit.http.profiles.<profile_name>` (YAML/Helm).

---

## ✅ Requirements (fonctionnels & non‑fonctionnels)

Fonctionnels :

- Règles de succès/erreur ordonnées, court‑circuit au premier match.
- Vérifications possibles : status exact/plage, header regex, body_regex, json_checks via JSON Pointer (exists/equals/regex).
- Production du résultat en succès : passthrough ou extraction JSON via `pick_pointer`.
- Erreurs normalisées RFC‑7807 (detail depuis header/pointer/template).
- Cache : clé = profil + méthode + chemin + vary_headers + hash du body (optionnel), ETag/304, SWR, SIE, negative caching.
- Mock : golden data, déclenché par route/headers/tenant, option signature HMAC.

Non‑fonctionnels :

- Non‑bloquant, performant, GraalVM‑friendly (pas de reflection sauvage/SPI non supporté).
- Sécurité : secrets via Vault/KMS, allow‑list d’en‑têtes sortants, ciphers/protocoles TLS, redaction logs.
- Observabilité standardisée : métriques, traces, logs JSON corrélés.
- Gouvernance : JSON‑Schema + linter (CI) pour profils/règles ; versioning, canary/shadow.
- Testabilité : TCK/golden tests, WireMock/ConfigMap, CLI de validation.

---

## 🔬 TDD Workflow (agents & devs)

1) Commencer par les tests (domaine pur)

- Créer un test JUnit par prédicat (status, header regex, json pointer).
- Ajouter des samples dans `adapter-http-tests/tck`.
- Vérifier que `RuleEngine` retourne le `Result.Success` ou `Result.Failure` attendu.

2) Implémenter les classes domain

- `Result`, `ProblemDetails`, `AdapterProfile`, `RuleEngine`, `RetryPolicy`, `CachePolicy`.
- Respecter SRP (une classe = une responsabilité).

3) Brancher les gateways/app

- Implémenter `AdapterFacade`.
- Ajouter `AuthGateway`, `CacheGateway`, `MockGateway`.
- Ajouter `ProblemFactory`.

4) Ajout des impl infra

- `RestClientReactiveAdapter` (non‑bloquant).
- `InMemoryCacheStore`, puis `RedisCacheStore`.
- `ConfigMapMockStore`.
- Observabilité (Micrometer, OTel).

5) Tests E2E (intégration)

- WireMock et golden scenarios.
- Cas : 200 success, 200 error, 429 rate‑limit, 5xx retry, 304 cache, mock route.

6) Native build (GraalVM)

- Exécution native avec 1–2 profils simples.

---

## 🛠️ Plan de travail (A → F)

Étape A — Bootstrapping

- Créer les modules et POMs.
- Dépendances Quarkus : rest-client-reactive, mutiny, arc, micrometer, opentelemetry.
- Implémenter domain/model (Result, ProblemDetails, specs Auth/Retry/Cache/Ssl).
- Implémenter RuleEngine + Predicates (status/header/body/json).

Étape B — HTTP & Orchestration

- Implémenter `HttpPort` via `RestClientReactiveAdapter` (timeouts, HTTP/2, SSL).
- Écrire `AdapterFacade` (pipeline) : Auth → Mock → Cache (ETag/Last‑Modified) → Http → 304 → RuleEngine → ProblemFactory → Retry/CB → SIE fallback cache.
- Observabilité (métriques/traces/logs) et `ProfileRegistry` + `ProfileFactory` (@ConfigMapping).

Étape C — Auth, Retry, Cache

- Auth : api_key, bearer, oauth2_cc (cache token), hmac, mtls.
- Retry : max tentatives, backoff + jitter, respect `Retry-After`, idempotent only.
- Cache : `InMemoryCacheStore` (clé, TTL, SWR, SIE, negative caching, ETag/304).

Étape D — Mock en production

- `MockStore` (ConfigMap d’abord). `MockGateway` : match routes (méthode + regex chemin + headers), déclencheur header (signature HMAC optionnelle), templating simple. Shadow recording (optionnel).

Étape E — Observabilité & Sécurité

- Metrics : compteurs (requests/success/errors/cache/mock), histogrammes de latence.
- Tracing : attributs profile, rule_id, upstream_status, cache.hit, mock.hit, retry.attempt.
- Logs : JSON structuré, correlation id, redaction (headers/body pointers).
- Sécurité : secrets via Vault/KMS, allow‑list d’en‑têtes sortants, TLS protocoles/ciphers.

Étape F — Gouvernance & Outils

- JSON‑Schema + linter pour valider profils/règles (CI : fail si regex dangereuse ou pas de success rule).
- CLI (optionnel) : `validate --profile`, `run --samples`.
- Canary/shadow : router 5–10 % vers un nouveau profil.

---

## 📊 Observabilité standard

- Metrics :
  - `omniflow_adapter_requests_total{profile,method}`
  - `omniflow_adapter_success_total{profile,rule_id}`
  - `omniflow_adapter_errors_total{profile,rule_id,type}`
  - `omniflow_adapter_latency_ms{profile}` histogram
  - `omniflow_adapter_cache_hits_total{profile}`, `…_swr_total`, `…_sie_total`
  - `omniflow_adapter_mock_hits_total{profile,scenario}`
- Tracing : attributs `profile`, `rule_id`, `upstream_status`, `cache.hit`, `mock.hit`, `retry.attempt`.
- Logs : JSON, corrélés via `x-request-id`, headers/body sensibles redacts.

---

## 🧪 Tests (quoi et comment)

Unit (domaine pur)

- Predicates (status/header/body/json).
- RuleEngine (success puis error, court‑circuit).
- RetryClassifier (retriable vs non).
- CacheKey (stabilité, vary headers).
- ProblemFactory (pointer/header/template).

TCK (golden tests)

- Dossier `adapter-http-tests/tck/` : `profiles/<profile>.yaml`, `golden/<scenario>.yaml`, `samples/…` (réponses amont figées).
- Tests paramétrés JUnit : pour chaque sample → assert `Result.Success/Failure`, `rule_id`, RFC‑7807.

Intégration / E2E

- WireMock (ou MockStore ConfigMap) pour simuler l’amont.
- Scénarios : 200 OK métier, 200 avec erreur métier, 429, 5xx + retry + `Retry-After`, ETag/304 + cache hit, SWR (stale servi + refresh), SIE (stale si erreur), mock en prod (header signé).

Native smoke (GraalVM)

- Lancer l’appli native, tester : timeouts, 304, cap cache, règles simples, un mock.

Critères d’acceptation (DoD)

- 100 % des cas TCK verts (success/error/generic + cache + mock).
- Overhead P99 du pipeline ≤ 2 ms (sans réseau).
- Profils validés par linter (schema OK, pas de regex catastrophique).
- Observabilité visible (dashboards & traces avec rule_id).
- Canary 5–10 % sans régression en INT/PRE‑PROD.

---

## ⚙️ Exigences à respecter (rappel synthétique)

- Inversed logic : règles → erreurs → générique.
- Configurable : DSL YAML (status, headers regex, body regex, json pointer).
- Auth : `none`, `api_key`, `bearer`, `oauth2_cc`, `hmac`, `mtls`.
- SSL : truststore/protocols, option insecure.
- Retry : backoff + jitter, respect `Retry-After`, idempotent only.
- Cache : SWR, SIE, ETag/304, negative caching.
- Mock : golden data, routes, templating, déclencheur header signé, shadow recording.
- Observabilité : métriques, traces, logs JSON avec redaction.
- Sécurité : secrets via Vault/KMS, header allowlist, PII redaction.
- GraalVM‑friendly : pas de SPI/reflection sauvage.

---

## 🧭 Bonnes pratiques & pièges à éviter

- Toujours imposer un `max_body_kb` (réseau non fiable).
- Éviter d’inclure `Authorization` dans `vary_headers` sauf nécessité (sinon explosion du cache).
- Règles : au moins une success rule claire pour les 2xx.
- Logs : ne jamais logger les bodies PII en INFO ; activer redaction.
- Mock en prod : protéger par tenant allowlist et/ou signature HMAC, audit obligatoire.
- Retry : privilégier méthodes idempotentes ; respecter `Retry-After`.
- Sécurité : secrets jamais en clair dans le YAML → utiliser Vault/KMS.

---

## 🧩 Exemple minimal de profil (YAML)

```yaml
ofkit:
  http:
    profiles:
      accounts_api:
        base_url: https://api.accounts.example.com
        timeouts: { connect_ms: 1500, read_ms: 3000 }
        ssl: { insecure: false, protocols: ["TLSv1.2","TLSv1.3"] }
        auth: { kind: bearer, bearer: { token: ${ACCOUNTS_TOKEN:} } }
        evaluation: { stop_on_first_match: true, regex_timeout_ms: 50 }
        retry:
          enabled: true
          max_retries: 2
          initial_delay_ms: 100
          max_delay_ms: 800
          jitter: true
          respect_retry_after: true
          idempotent_only: true
          retriable_rules: [ { id: r-5xx, when: { status: "500-599" } } ]
        cache:
          enabled: true
          backend: in_memory
          default_ttl_s: 60
          validators: { use_etag: true, use_last_modified: true }
          swr_ttl_s: 30
          sie_ttl_s: 30
          vary_headers: ["accept"]
          max_body_kb: 512
        mock:
          enabled: true
          allow_tenants: ["sandbox"]
          trigger: { header: "X-OF-Mock", require_signature: true }
          routes:
            - id: acc-by-msisdn
              when: { method: GET, path_regex: "^/v1/accounts\\?msisdn=.*$" }
              scenario: "accounts/by-msisdn/ok"
        rules:
          success:
            - id: ok-2xx-json-flag
              when:
                all:
                  - status: "200-299"
                  - json: { pointer: "/status", equals: "OK" }
              produce: { type: json, pick_pointer: "/data" }
          errors:
            - id: err-business
              when:
                all:
                  - status: "200-299"
                  - json: { pointer: "/error/code", exists: true }
              problem:
                type: "https://omniflow/business"
                title: "Erreur métier amont"
                status: 422
                detail_from_pointer: "/error/message"
        generic_problem: { type: "about:blank", title: "Erreur service externe", status: 502, detail_template: "No rule matched (status={status})" }
```

---

## 📋 Work to be done (juniors)

- [ ] Créer `adapter-http-domain` avec modèles de base (`Result`, `ProblemDetails`).
- [ ] Implémenter `RuleEngine` + `Predicates`.
- [ ] Implémenter `ProblemFactory` (RFC‑7807).
- [ ] Implémenter `AdapterFacade` (pipeline complet).
- [ ] Implémenter `HttpPort` via Rest Client Reactive.
- [ ] Ajouter `AuthGateway` (api_key, bearer).
- [ ] Ajouter `CacheGateway` (in‑memory, ETag/304).
- [ ] Ajouter `MockGateway` + `ConfigMapMockStore`.
- [ ] Ajouter observabilité (metrics/traces/logs).
- [ ] Écrire TCK (golden data) pour au moins 3 scénarios.
- [ ] Intégrer linter JSON‑Schema pour profils.
- [ ] Test build GraalVM native.

---

## ✅ Definition of Done (DoD)

- Tous les tests unitaires et TCK passent.
- Build Quarkus JVM & GraalVM OK.
- Profil(s) validés par JSON‑Schema et linter.
- Dashboards (Prometheus/Grafana, Jaeger) affichent metrics/traces.
- Mock testable en INT/PROD (header signé, golden data montés).
