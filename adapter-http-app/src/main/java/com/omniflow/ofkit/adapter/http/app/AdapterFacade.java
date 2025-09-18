package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.rules.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the adapter pipeline: HttpPort → RuleEngine (success → errors → generic).
 * Gateways like auth/cache/mock can be introduced later without changing this basic contract.
 */
@ApplicationScoped
public class AdapterFacade {
    private final HttpPort http;
    private final RuleEngine ruleEngine;
    private final ProfileRegistry profiles;
    private final CacheGateway cacheGateway; // optional in tests
    private final RetryGateway retryGateway; // optional in tests
    private final AuthGateway authGateway; // optional in tests

    public AdapterFacade(HttpPort http, RuleEngine ruleEngine, ProfileRegistry profiles, CacheGateway cacheGateway) {
        this.http = http;
        this.ruleEngine = ruleEngine;
        this.profiles = profiles;
        this.cacheGateway = cacheGateway;
        this.retryGateway = null;
        this.authGateway = null;
    }

    // Non-CDI convenience constructor for tests without caching
    public AdapterFacade(HttpPort http, RuleEngine ruleEngine, ProfileRegistry profiles) {
        this.http = http;
        this.ruleEngine = ruleEngine;
        this.profiles = profiles;
        this.cacheGateway = null;
        this.retryGateway = null;
        this.authGateway = null;
    }

    public AdapterFacade(HttpPort http, RuleEngine ruleEngine, ProfileRegistry profiles, CacheGateway cacheGateway, RetryGateway retryGateway) {
        this.http = http;
        this.ruleEngine = ruleEngine;
        this.profiles = profiles;
        this.cacheGateway = cacheGateway;
        this.retryGateway = retryGateway;
        this.authGateway = null;
    }

    @Inject
    public AdapterFacade(HttpPort http, RuleEngine ruleEngine, ProfileRegistry profiles, CacheGateway cacheGateway, RetryGateway retryGateway, AuthGateway authGateway) {
        this.http = http;
        this.ruleEngine = ruleEngine;
        this.profiles = profiles;
        this.cacheGateway = cacheGateway;
        this.retryGateway = retryGateway;
        this.authGateway = authGateway;
    }

    public Result handle(String profileId, HttpRequest request) throws Exception {
        AdapterProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown profile: " + profileId));

        HttpPort effective = (retryGateway != null)
                ? (req) -> retryGateway.execute(profile, req, http)
                : http;
        // Apply auth headers first
        HttpRequest authed = (authGateway != null)
                ? authGateway.apply(profile.authSpec() == null ? new com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.None() : profile.authSpec(), request)
                : request;
        // Apply HTTP options to headers for the reactive adapter (timeouts/SSL)
        authed = adornHttpOptions(profile, authed);
        HttpResponse upstream = (cacheGateway != null)
                ? cacheGateway.execute(profile, authed, effective)
                : effective.execute(authed);
        ResponseContext ctx = new ResponseContext(upstream);
        List<SuccessRule> success = profile.successRules();
        List<ErrorRule> errors = profile.errorRules();
        return ruleEngine.evaluate(ctx, success, errors, profile.genericProblem());
    }

    private static HttpRequest adornHttpOptions(com.omniflow.ofkit.adapter.http.domain.model.AdapterProfile profile, HttpRequest req) {
        var http = profile.httpSpec() != null ? profile.httpSpec() : com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec.defaults();
        Map<String, List<String>> headers = new java.util.HashMap<>();
        if (req.headers() != null) req.headers().forEach((k, vs) -> headers.put(k, java.util.List.copyOf(vs)));
        headers.put("X-OF-Read-Timeout-Ms", java.util.List.of(Integer.toString(http.readTimeoutMs())));
        headers.put("X-OF-Connect-Timeout-Ms", java.util.List.of(Integer.toString(http.connectTimeoutMs())));
        if (http.ssl() != null && http.ssl().insecure()) headers.put("X-OF-SSL-Insecure", java.util.List.of("true"));
        headers.put("X-OF-HTTP-Max-Pool-Size", java.util.List.of(Integer.toString(http.maxPoolSize())));
        headers.put("X-OF-HTTP-Max-Wait-Queue", java.util.List.of(Integer.toString(http.maxWaitQueueSize())));
        headers.put("X-OF-HTTP-Keep-Alive", java.util.List.of(Boolean.toString(http.keepAlive())));
        headers.put("X-OF-HTTP-Keep-Alive-Timeout-S", java.util.List.of(Integer.toString(http.keepAliveTimeoutSeconds())));
        return new HttpRequest(req.method(), req.uri(), headers, req.body());
    }
}
