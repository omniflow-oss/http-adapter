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

class AdapterResourceUnitTest {

    @Test
    void returns_200_with_headers_and_rule_id() throws Exception {
        AdapterResource res = new AdapterResource();
        AdapterFacade facade = new AdapterFacade(null, new com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine(), id -> Optional.of(
                new AdapterProfile(id, "https://example.com", List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""),
                        CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None()))) {
            @Override public Result handle(String profileId, HttpRequest request) {
                return new Result.Success(new HttpResponse(200, Map.of("X-Up", List.of("v")), "{}".getBytes()), "ok-2xx");
            }
        };
        set(res, "facade", facade);
        set(res, "profiles", (ProfileRegistry) id -> Optional.of(
                new AdapterProfile(id, "https://example.com", List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""),
                        CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None())));

        UriInfo uriInfo = new UriInfo() {
            @Override public String getPath() { return "/get"; }
            @Override public String getPath(boolean decode) { return getPath(); }
            @Override public List<PathSegment> getPathSegments() { return List.of(); }
            @Override public List<PathSegment> getPathSegments(boolean decode) { return List.of(); }
            @Override public URI getRequestUri() { return URI.create("http://x/get?foo=bar"); }
            @Override public UriBuilder getRequestUriBuilder() { return UriBuilder.fromUri(getRequestUri()); }
            @Override public URI getAbsolutePath() { return URI.create("http://x/get"); }
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
        HttpHeaders headers = new HeadersStub();

        Response r = res.get("p", "get", uriInfo, headers);
        assertEquals(200, r.getStatus());
        assertEquals("ok-2xx", r.getHeaderString("X-OF-Rule-Id"));
        assertNotNull(r.getHeaderString("X-OF-Total-Latency-Ms"));
    }

    @Test
    void missing_base_and_no_header_returns_400() throws Exception {
        AdapterResource res = new AdapterResource();
        AdapterFacade facade2 = new AdapterFacade(null, new com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine(), id -> Optional.of(
                new AdapterProfile(id, null, List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""),
                        CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None()))) {
            @Override public Result handle(String profileId, HttpRequest request) {
                return new Result.Success(new HttpResponse(200, Map.of(), new byte[0]), "ok");
            }
        };
        set(res, "facade", facade2);
        set(res, "profiles", (ProfileRegistry) id -> Optional.of(
                new AdapterProfile(id, null, List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""),
                        CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None())));

        UriInfo uriInfo = new UriInfo() {
            @Override public String getPath() { return "/get"; }
            @Override public String getPath(boolean decode) { return getPath(); }
            @Override public List<PathSegment> getPathSegments() { return List.of(); }
            @Override public List<PathSegment> getPathSegments(boolean decode) { return List.of(); }
            @Override public URI getRequestUri() { return URI.create("http://x/get"); }
            @Override public UriBuilder getRequestUriBuilder() { return UriBuilder.fromUri(getRequestUri()); }
            @Override public URI getAbsolutePath() { return URI.create("http://x/get"); }
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
        HttpHeaders headers2 = new HeadersStub();

        Response r = res.get("p", "get", uriInfo, headers2);
        assertEquals(400, r.getStatus());
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}

class HeadersStub implements HttpHeaders {
    final MultivaluedMap<String, String> m = new MultivaluedHashMap<>();
    @Override public List<String> getRequestHeader(String name) { return m.get(name); }
    @Override public String getHeaderString(String name) { return null; }
    @Override public List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() { return List.of(); }
    @Override public List<java.util.Locale> getAcceptableLanguages() { return List.of(); }
    @Override public jakarta.ws.rs.core.MediaType getMediaType() { return null; }
    @Override public java.util.Locale getLanguage() { return null; }
    @Override public MultivaluedMap<String, String> getRequestHeaders() { return m; }
    @Override public Date getDate() { return null; }
    @Override public int getLength() { return -1; }
    @Override public Map<String, jakarta.ws.rs.core.Cookie> getCookies() { return Map.of(); }
}
