package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.model.AdapterProfile;

import java.util.Optional;

public interface ProfileRegistry {
    Optional<AdapterProfile> findById(String id);
}

