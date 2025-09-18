package com.omniflow.ofkit.adapter.http.infra.rest;

import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.PathSegment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AdapterResourceFailureMappingTest {

    @Test
    void maps_failure_to_problem_json_with_headers() throws Exception {
        AdapterResource res = new AdapterResource();
        AdapterFacade facade = new AdapterFacade(null, new com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine(), id -> Optional.of(
                new AdapterProfile(id, "https://example.com", List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""),
                        CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None()))) {
            @Override public Result handle(String profileId, HttpRequest request) {
                ProblemDetails p = new ProblemDetails("https://omniflow/test", "Bad Upstream", 422, "oops", null, Map.of("code", "X1"));
                return new Result.Failure(p, "err-business");
            }
        };
        set(res, "facade", facade);
        set(res, "profiles", (ProfileRegistry) id -> Optional.of(
                new AdapterProfile(id, "https://example.com", List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""),
                        CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None())));

        UriInfo uriInfo = simpleUri("/r");
        HttpHeaders headers = new HeadersSimple2();
        Response r = res.get("p", "r", uriInfo, headers);
        assertEquals(422, r.getStatus());
        assertEquals("err-business", r.getHeaderString("X-OF-Rule-Id"));
        assertNotNull(r.getHeaderString("X-OF-Total-Latency-Ms"));
        assertEquals("application/problem+json", r.getMediaType().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> problem = (Map<String, Object>) r.getEntity();
        assertEquals("https://omniflow/test", problem.get("type"));
        assertEquals("Bad Upstream", problem.get("title"));
        assertEquals(422, problem.get("status"));
        assertEquals("oops", problem.get("detail"));
        assertEquals("X1", problem.get("code"));
    }

    private static UriInfo simpleUri(String path) {
        return new UriInfo() {
            @Override public String getPath() { return path; }
            @Override public String getPath(boolean decode) { return getPath(); }
            @Override public List<PathSegment> getPathSegments() { return List.of(); }
            @Override public List<PathSegment> getPathSegments(boolean decode) { return List.of(); }
            @Override public URI getRequestUri() { return URI.create("http://x" + path); }
            @Override public UriBuilder getRequestUriBuilder() { return UriBuilder.fromUri(getRequestUri()); }
            @Override public URI getAbsolutePath() { return URI.create("http://x" + path); }
            @Override public UriBuilder getAbsolutePathBuilder() { return UriBuilder.fromUri(getAbsolutePath()); }
            @Override public URI getBaseUri() { return URI.create("http://x/"); }
            @Override public UriBuilder getBaseUriBuilder() { return UriBuilder.fromUri(getBaseUri()); }
            @Override public MultivaluedMap<String, String> getPathParameters() { return new MultivaluedHashMap<>(); }
            @Override public MultivaluedMap<String, String> getPathParameters(boolean decode) { return new MultivaluedHashMap<>(); }
            @Override public MultivaluedMap<String, String> getQueryParameters() { return new MultivaluedHashMap<>(); }
            @Override public MultivaluedMap<String, String> getQueryParameters(boolean decode) { return new MultivaluedHashMap<>(); }
            @Override public List<String> getMatchedURIs() { return List.of(); }
            @Override public List<String> getMatchedURIs(boolean decode) { return List.of(); }
            @Override public List<Object> getMatchedResources() { return List.of(); }
            @Override public URI resolve(URI uri) { return uri; }
            @Override public URI relativize(URI uri) { return uri; }
        };
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}

class HeadersSimple2 implements HttpHeaders {
    final MultivaluedMap<String, String> m = new MultivaluedHashMap<>();
    @Override public java.util.List<String> getRequestHeader(String name) { return m.get(name); }
    @Override public String getHeaderString(String name) { return m.getFirst(name); }
    @Override public java.util.List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() { return java.util.List.of(); }
    @Override public java.util.List<java.util.Locale> getAcceptableLanguages() { return java.util.List.of(); }
    @Override public jakarta.ws.rs.core.MediaType getMediaType() { return null; }
    @Override public java.util.Locale getLanguage() { return null; }
    @Override public MultivaluedMap<String, String> getRequestHeaders() { return m; }
    @Override public java.util.Date getDate() { return null; }
    @Override public int getLength() { return -1; }
    @Override public java.util.Map<String, jakarta.ws.rs.core.Cookie> getCookies() { return java.util.Map.of(); }
}

