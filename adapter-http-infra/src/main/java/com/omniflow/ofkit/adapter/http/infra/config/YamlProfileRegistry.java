package com.omniflow.ofkit.adapter.http.infra.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.AdapterProfile;
import com.omniflow.ofkit.adapter.http.domain.model.AuthSpec;
import com.omniflow.ofkit.adapter.http.domain.model.CachePolicy;
import com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec;
import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import com.omniflow.ofkit.adapter.http.domain.model.RetrySpec;
import com.omniflow.ofkit.adapter.http.domain.model.SslSpec;
import com.omniflow.ofkit.adapter.http.domain.rules.AndPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.BodyRegexPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.ErrorRule;
import com.omniflow.ofkit.adapter.http.domain.rules.HeaderRegexPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.JsonPointerPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.ResponsePredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.StatusPredicate;
import com.omniflow.ofkit.adapter.http.domain.rules.SuccessRule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@ApplicationScoped
public class YamlProfileRegistry implements ProfileRegistry {
    private static final Logger LOG = Logger.getLogger(YamlProfileRegistry.class);
    private final Map<String, AdapterProfile> profiles = new HashMap<>();

    @Inject
    ProfileMapper mapper;

    @ConfigProperty(name = "ofkit.http.profiles.includes")
    Optional<String> includes;

    // Support unit tests that instantiate directly without CDI
    public YamlProfileRegistry() {
        this.mapper = new ProfileMapper() {};
        this.includes = Optional.empty();
        try {
            loadAll();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load profiles", e);
        }
    }

    @PostConstruct
    void init() {
        try {
            loadAll();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load profiles", e);
        }
    }

    @Override
    public Optional<AdapterProfile> findById(String id) {
        return Optional.ofNullable(profiles.get(id));
    }

    private void loadAll() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<String> resources = discoverProfileResources(cl);
        LOG.infof("Discovered profile resources: %s", resources);
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream schemaIs = cl.getResourceAsStream("profiles-schema.json")) {
            if (schemaIs == null) throw new IOException("profiles-schema.json not found");
            JsonSchema schema = schemaFactory.getSchema(schemaIs);

            for (String res : resources) {
                try (InputStream is = cl.getResourceAsStream(res)) {
                    if (is == null) continue;
                    JsonNode root = yaml.readTree(is);
                    Set<ValidationMessage> errors = schema.validate(root);
                    if (!errors.isEmpty()) {
                        throw new IllegalArgumentException("Profile " + res + " invalid: " + errors);
                    }
                    JsonNode profilesNode = root.path("ofkit").path("http").path("profiles");
                    Iterator<String> it = profilesNode.fieldNames();
                    while (it.hasNext()) {
                        String pid = it.next();
                        JsonNode p = profilesNode.get(pid);
                        AdapterProfile profile = mapper.toProfile(pid, p);
                        profiles.put(pid, profile);
                    }
                }
            }
        }
    }

    private List<String> discoverProfileResources(ClassLoader cl) throws IOException {
        if (includes != null && includes.isPresent() && !includes.get().isBlank()) {
            String[] parts = includes.get().split(",");
            List<String> out = new ArrayList<>();
            for (String p : parts) { String s = p.trim(); if (!s.isEmpty()) out.add(s); }
            if (!out.isEmpty()) return out;
        }

        List<String> result = new ArrayList<>();
        Enumeration<URL> urls = cl.getResources("profiles");
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                File dir = new File(url.getPath());
                File[] files = dir.listFiles((d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
                if (files != null) for (File f : files) result.add("profiles/" + f.getName());
            } else if ("jar".equals(protocol)) {
                try {
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    try (JarFile jar = conn.getJarFile()) {
                        Enumeration<JarEntry> e = jar.entries();
                        while (e.hasMoreElements()) {
                            JarEntry entry = e.nextElement();
                            String name = entry.getName();
                            if (name.startsWith("profiles/") && (name.endsWith(".yaml") || name.endsWith(".yml"))) {
                                result.add(name);
                            }
                        }
                    }
                } catch (IOException ignored) {}
            }
        }
        return result;
    }

    // Static helpers kept for unit tests via reflection
    private static AdapterProfile toProfile(String id, JsonNode p) {
        String baseUrl = p.path("base_url").asText(null);
        List<SuccessRule> success = new ArrayList<>();
        JsonNode successArr = p.path("rules").path("success");
        if (successArr.isArray()) {
            for (JsonNode r : successArr) {
                String rid = r.path("id").asText();
                ResponsePredicate pred = parseWhen(r.path("when"));
                String pick = r.path("produce").path("pick_pointer").asText(null);
                success.add(new SuccessRule(rid, pred, pick));
            }
        }

        List<ErrorRule> errors = new ArrayList<>();
        JsonNode errArr = p.path("rules").path("errors");
        if (errArr.isArray()) {
            for (JsonNode r : errArr) {
                String rid = r.path("id").asText();
                ResponsePredicate pred = parseWhen(r.path("when"));
                JsonNode prob = r.path("problem");
                ProblemDetails pd = ProblemDetails.of(
                        prob.path("type").asText("about:blank"),
                        prob.path("title").asText("Erreur"),
                        prob.path("status").asInt(500),
                        prob.path("detail_template").asText("")
                );
                errors.add(new ErrorRule(rid, pred, pd));
            }
        }

        JsonNode gp = p.path("generic_problem");
        ProblemDetails generic = ProblemDetails.of(
                gp.path("type").asText("about:blank"),
                gp.path("title").asText("Erreur service externe"),
                gp.path("status").asInt(502),
                gp.path("detail_template").asText("No rule matched")
        );
        CachePolicy cache = parseCache(p.path("cache"));
        RetrySpec retry = parseRetry(p.path("retry"));
        HttpClientSpec httpSpec = parseHttp(p.path("timeouts"), p.path("ssl"), p.path("pool"));
        AuthSpec auth = parseAuth(p.path("auth"));
        return new AdapterProfile(id, baseUrl, success, errors, generic, cache, retry, httpSpec, auth);
    }

    private static CachePolicy parseCache(JsonNode c) {
        if (c == null || c.isMissingNode() || c.isNull() || !c.path("enabled").asBoolean(false)) {
            return CachePolicy.disabled();
        }
        int ttl = c.path("default_ttl_s").asInt(0);
        int swr = c.path("swr_ttl_s").asInt(0);
        int sie = c.path("sie_ttl_s").asInt(0);
        boolean useEtag = c.path("validators").path("use_etag").asBoolean(true);
        boolean useLm = c.path("validators").path("use_last_modified").asBoolean(true);
        int maxBodyKb = c.path("max_body_kb").asInt(0);
        int negativeTtl = c.path("negative_ttl_s").asInt(0);
        List<String> vary = new ArrayList<>();
        if (c.has("vary_headers") && c.path("vary_headers").isArray()) {
            c.path("vary_headers").forEach(n -> vary.add(n.asText()));
        }
        return new CachePolicy(true, ttl, swr, sie, useEtag, useLm, vary, maxBodyKb, negativeTtl);
    }

    private static RetrySpec parseRetry(JsonNode r) {
        if (r == null || r.isMissingNode() || r.isNull() || !r.path("enabled").asBoolean(false)) {
            return RetrySpec.disabled();
        }
        boolean enabled = true;
        int max = Math.max(0, r.path("max_retries").asInt(0));
        long initial = Math.max(0, r.path("initial_delay_ms").asLong(0));
        long maxDelay = Math.max(0, r.path("max_delay_ms").asLong(initial));
        boolean jitter = r.path("jitter").asBoolean(true);
        boolean respect = r.path("respect_retry_after").asBoolean(true);
        boolean idempotent = r.path("idempotent_only").asBoolean(true);
        return new RetrySpec(enabled, max, initial, maxDelay, jitter, respect, idempotent);
    }

    private static HttpClientSpec parseHttp(JsonNode t, JsonNode s, JsonNode pool) {
        int connect = t.path("connect_ms").asInt(5000);
        int read = t.path("read_ms").asInt(10000);
        boolean insecure = s.path("insecure").asBoolean(false);
        int maxPool = pool.path("max_pool_size").asInt(50);
        int maxWait = pool.path("max_wait_queue").asInt(-1);
        boolean keepAlive = pool.path("keep_alive").asBoolean(true);
        int keepAliveS = pool.path("keep_alive_timeout_s").asInt(60);
        return new HttpClientSpec(connect, read, new SslSpec(insecure), maxPool, maxWait, keepAlive, keepAliveS);
    }

    private static AuthSpec parseAuth(JsonNode a) {
        String kind = a.path("kind").asText("none");
        return switch (kind) {
            case "bearer" -> new AuthSpec.Bearer(a.path("bearer").path("token").asText(""));
            case "api_key" -> new AuthSpec.ApiKey(a.path("api_key").path("header").asText("X-API-Key"), a.path("api_key").path("value").asText(""));
            default -> new AuthSpec.None();
        };
    }

    private static ResponsePredicate parseStatus(String spec) {
        if (spec == null || spec.isBlank()) return ctx -> false;
        if (spec.contains("-")) {
            String[] parts = spec.split("-");
            int min = Integer.parseInt(parts[0]);
            int max = Integer.parseInt(parts[1]);
            return new StatusPredicate(min, max);
        } else {
            int code = Integer.parseInt(spec);
            return new StatusPredicate(code, code);
        }
    }

    private static ResponsePredicate parseWhen(JsonNode when) {
        if (when == null || when.isMissingNode() || when.isNull()) return ctx -> false;

        if (when.has("status")) {
            return parseStatus(when.path("status").asText());
        }
        if (when.has("header")) {
            JsonNode h = when.path("header");
            return new HeaderRegexPredicate(h.path("name").asText(), h.path("regex").asText());
        }
        if (when.has("body_regex")) {
            return new BodyRegexPredicate(when.path("body_regex").asText());
        }
        if (when.has("json")) {
            JsonNode j = when.path("json");
            String ptr = j.path("pointer").asText();
            if (j.has("equals")) return JsonPointerPredicate.equalsAt(ptr, j.path("equals").asText());
            if (j.has("regex")) return JsonPointerPredicate.matchesAt(ptr, j.path("regex").asText());
            if (j.has("exists")) return JsonPointerPredicate.existsAt(ptr);
        }
        if (when.has("all") && when.path("all").isArray()) {
            List<ResponsePredicate> list = new ArrayList<>();
            for (JsonNode item : when.path("all")) {
                list.add(parseWhen(item));
            }
            return new AndPredicate(list);
        }
        return ctx -> false;
    }
}
