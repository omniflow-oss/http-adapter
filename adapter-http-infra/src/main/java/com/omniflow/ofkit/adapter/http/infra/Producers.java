package com.omniflow.ofkit.adapter.http.infra;

import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.AuthGateway;
import com.omniflow.ofkit.adapter.http.app.CacheGateway;
import com.omniflow.ofkit.adapter.http.app.RetryGateway;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.ports.CacheStore;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine;
import com.omniflow.ofkit.adapter.http.infra.cache.InMemoryCacheStore;
import com.omniflow.ofkit.adapter.http.infra.config.YamlProfileRegistry;
import com.omniflow.ofkit.adapter.http.infra.http.RestClientReactiveAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class Producers {
    private static final Logger LOG = Logger.getLogger(Producers.class);

    @Inject
    HttpPort http;

    @Inject
    CacheStore cacheStore;

    @Inject
    ProfileRegistry profileRegistry;

    @Produces
    @ApplicationScoped
    public AdapterFacade adapterFacade() {
        var httpPort = (http != null) ? http : new RestClientReactiveAdapter();
        var cacheStoreBean = (cacheStore != null) ? cacheStore : new InMemoryCacheStore();
        var profiles = (profileRegistry != null) ? profileRegistry : new YamlProfileRegistry();
        var re = new RuleEngine();
        var cache = new CacheGateway(cacheStoreBean);
        var retry = new RetryGateway();
        var auth = new AuthGateway();
        LOG.infof("AdapterFacade wiring: http=%s, cacheStore=%s", httpPort.getClass().getSimpleName(), cacheStoreBean.getClass().getSimpleName());
        return new AdapterFacade(httpPort, re, profiles, cache, retry, auth);
    }
}
