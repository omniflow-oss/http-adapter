package com.omniflow.ofkit.adapter.http.domain.model;

public sealed interface AuthSpec permits AuthSpec.None, AuthSpec.Bearer, AuthSpec.ApiKey {
    record None() implements AuthSpec {}
    record Bearer(String token) implements AuthSpec {}
    record ApiKey(String headerName, String value) implements AuthSpec {}
}

