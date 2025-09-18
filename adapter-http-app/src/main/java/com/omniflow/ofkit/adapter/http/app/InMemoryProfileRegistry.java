package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.AdapterProfile;
import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import com.omniflow.ofkit.adapter.http.domain.rules.*;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.*;

/** Simple in-memory registry with a couple of example profiles. */
@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryProfileRegistry implements ProfileRegistry {
    private final Map<String, AdapterProfile> byId = new HashMap<>();

    public InMemoryProfileRegistry() {
        byId.put("default", defaultProfile());
        byId.put("accounts_api", accountsProfile());
    }

    @Override
    public Optional<AdapterProfile> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    private static AdapterProfile defaultProfile() {
        SuccessRule ok2xx = new SuccessRule("ok-2xx", new StatusPredicate(200, 299), null);
        ErrorRule rateLimit429 = new ErrorRule(
                "err-429",
                new StatusPredicate(429, 429),
                ProblemDetails.of("https://omniflow/rate-limit", "Rate limited", 429, "Too many requests")
        );
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur service externe", 502, "No rule matched");
        return new AdapterProfile("default", null, List.of(ok2xx), List.of(rateLimit429), generic, com.omniflow.ofkit.adapter.http.domain.model.CachePolicy.disabled(), com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.disabled(), com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec.defaults(), new com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.None());
    }

    private static AdapterProfile accountsProfile() {
        ResponsePredicate okFlag = ctx -> new StatusPredicate(200, 299).test(ctx)
                && JsonPointerPredicate.equalsAt("/status", "OK").test(ctx);

        SuccessRule ok = new SuccessRule("ok-2xx-json-flag", okFlag, "/data");

        ResponsePredicate bizError = ctx -> new StatusPredicate(200, 299).test(ctx)
                && JsonPointerPredicate.existsAt("/error/code").test(ctx);

        ErrorRule err = new ErrorRule(
                "err-business",
                bizError,
                ProblemDetails.of("https://omniflow/business", "Erreur métier amont", 422, "Business error")
        );
        ProblemDetails generic = ProblemDetails.of("about:blank", "Erreur service externe", 502, "No rule matched");
        return new AdapterProfile("accounts_api", null, List.of(ok), List.of(err), generic, com.omniflow.ofkit.adapter.http.domain.model.CachePolicy.disabled(), com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.disabled(), com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec.defaults(), new com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.None());
    }
}
