package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.AuthSpec;
import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AuthGateway {
    public HttpRequest apply(AuthSpec auth, HttpRequest req) {
        if (auth == null || auth instanceof AuthSpec.None) return req;
        Map<String, List<String>> headers = new HashMap<>();
        if (req.headers() != null) req.headers().forEach((k, vs) -> headers.put(k, List.copyOf(vs)));
        if (auth instanceof AuthSpec.Bearer b) {
            List<String> vals = new ArrayList<>();
            vals.add("Bearer " + b.token());
            headers.put("Authorization", vals);
        } else if (auth instanceof AuthSpec.ApiKey ak) {
            headers.put(ak.headerName(), List.of(ak.value()));
        }
        return new HttpRequest(req.method(), req.uri(), headers, req.body());
    }
}

