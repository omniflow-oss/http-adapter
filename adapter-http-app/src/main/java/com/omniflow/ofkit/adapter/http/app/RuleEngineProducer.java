package com.omniflow.ofkit.adapter.http.app;

import com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class RuleEngineProducer {
    @Produces
    @ApplicationScoped
    RuleEngine ruleEngine() {
        return new RuleEngine();
    }
}

