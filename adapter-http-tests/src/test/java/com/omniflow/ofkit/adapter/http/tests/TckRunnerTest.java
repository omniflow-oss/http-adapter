package com.omniflow.ofkit.adapter.http.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.*;
import com.omniflow.ofkit.adapter.http.domain.ports.HttpPort;
import com.omniflow.ofkit.adapter.http.domain.rules.*;
import com.omniflow.ofkit.adapter.http.infra.http.JdkHttpClientAdapter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("e2e")
public class TckRunnerTest {

    static Stream<Path> scenarios() throws IOException {
        Path root = Path.of("src/test/resources/tck/scenarios");
        if (!Files.exists(root)) return Stream.of();
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".yaml")).toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void runScenario(Path scenarioPath) throws Exception {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        JsonNode scenario = yaml.readTree(Files.readAllBytes(scenarioPath));

        WireMockServer wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        try {
            WireMock.configureFor("localhost", wm.port());

            // Load profile YAML and override base_url with WireMock base URL
            String profileId = scenario.path("profile").asText();
            Path profilePath = Path.of("src/test/resources/tck/profiles/" + profileId + ".yaml");
            JsonNode profileRoot = yaml.readTree(Files.readAllBytes(profilePath));
            JsonNode p = profileRoot.path("ofkit").path("http").path("profiles").path(profileId);

            AdapterProfile profile = toProfile(profileId, p, "http://localhost:" + wm.port());

            // Stub upstream according to scenario
            JsonNode up = scenario.path("upstream");
            String reqPath = scenario.path("request").path("path").asText();
            String method = scenario.path("request").path("method").asText("GET").toUpperCase(Locale.ROOT);
            com.github.tomakehurst.wiremock.client.MappingBuilder mapping;
            switch (method) {
                case "GET" -> mapping = get(urlEqualTo(reqPath));
                case "POST" -> mapping = post(urlEqualTo(reqPath));
                case "PUT" -> mapping = put(urlEqualTo(reqPath));
                case "PATCH" -> mapping = patch(urlEqualTo(reqPath));
                case "DELETE" -> mapping = delete(urlEqualTo(reqPath));
                default -> mapping = any(urlEqualTo(reqPath));
            }
            var response = aResponse().withStatus(up.path("status").asInt());
            JsonNode headers = up.path("headers");
            headers.fieldNames().forEachRemaining(h -> response.withHeader(h, headers.path(h).asText()));
            if (up.has("body_file")) {
                String bodyRes = up.path("body_file").asText();
                try (InputStream is = getResource(bodyRes)) {
                    if (is == null) throw new IOException("Missing body file: " + bodyRes);
                    response.withBody(is.readAllBytes());
                }
            }
            wm.stubFor(mapping.willReturn(response));

            // Execute via AdapterFacade
            HttpPort http = new JdkHttpClientAdapter();
            RuleEngine engine = new RuleEngine();
            ProfileRegistry registry = pid -> Optional.of(profile);
            AdapterFacade facade = new AdapterFacade(http, engine, registry);

            String base = profile.baseUrl();
            String target = base + reqPath;
            HttpRequest req = new HttpRequest(method, URI.create(target), Map.of(), null);
            Result r = facade.handle(profileId, req);

            JsonNode expect = scenario.path("expect");
            String kind = expect.path("kind").asText();
            String ruleId = expect.path("rule_id").asText();
            int status = expect.path("status").asInt();

            if ("success".equals(kind)) {
                assertInstanceOf(Result.Success.class, r, scenarioPath.toString());
                Result.Success s = (Result.Success) r;
                assertEquals(ruleId, s.ruleId(), scenarioPath.toString());
                assertEquals(status, s.response().statusCode(), scenarioPath.toString());
            } else if ("failure".equals(kind)) {
                assertInstanceOf(Result.Failure.class, r, scenarioPath.toString());
                Result.Failure f = (Result.Failure) r;
                assertEquals(ruleId, f.ruleId(), scenarioPath.toString());
                assertEquals(status, f.problem().status(), scenarioPath.toString());
            } else {
                fail("Unknown expect.kind: " + kind + " in " + scenarioPath);
            }
        } finally {
            wm.stop();
        }
    }

    private static InputStream getResource(String path) {
        return TckRunnerTest.class.getClassLoader().getResourceAsStream(path);
    }

    private static AdapterProfile toProfile(String id, JsonNode p, String baseUrl) {
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
        return new AdapterProfile(id, baseUrl, success, errors, generic,
                com.omniflow.ofkit.adapter.http.domain.model.CachePolicy.disabled(),
                com.omniflow.ofkit.adapter.http.domain.model.RetrySpec.disabled(),
                com.omniflow.ofkit.adapter.http.domain.model.HttpClientSpec.defaults(),
                new com.omniflow.ofkit.adapter.http.domain.model.AuthSpec.None());
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
            for (JsonNode item : when.path("all")) list.add(parseWhen(item));
            return new AndPredicate(list);
        }
        return ctx -> false;
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
}
