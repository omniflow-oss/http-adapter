package com.omniflow.ofkit.adapter.http.infra.rest;

import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.PathSegment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AdapterResourceHeaderPropagationTest {
    @Test
    void propagates_custom_header_but_not_host() throws Exception {
        AdapterResource res = new AdapterResource();
        final HttpRequest[] seen = {null};
        AdapterFacade facade = new AdapterFacade(null, new com.omniflow.ofkit.adapter.http.domain.rules.RuleEngine(), id -> Optional.of(
                new AdapterProfile(id, "https://api.example.com", List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""),
                        CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None()))) {
            @Override public Result handle(String profileId, HttpRequest request) {
                seen[0] = request;
                return new Result.Success(new HttpResponse(200, Map.of(), new byte[0]), "ok");
            }
        };
        set(res, "facade", facade);
        set(res, "profiles", (ProfileRegistry) id -> Optional.of(
                new AdapterProfile(id, "https://api.example.com", List.of(), List.of(), ProblemDetails.of("about:blank","t",502, ""),
                        CachePolicy.disabled(), RetrySpec.disabled(), HttpClientSpec.defaults(), new AuthSpec.None())));

        UriInfo uriInfo = new UriInfo() {
            @Override public String getPath() { return "/r"; }
            @Override public String getPath(boolean decode) { return getPath(); }
            @Override public List<PathSegment> getPathSegments() { return List.of(); }
            @Override public List<PathSegment> getPathSegments(boolean decode) { return List.of(); }
            @Override public URI getRequestUri() { return URI.create("http://x/r"); }
            @Override public UriBuilder getRequestUriBuilder() { return UriBuilder.fromUri(getRequestUri()); }
            @Override public URI getAbsolutePath() { return URI.create("http://x/r"); }
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
        HttpHeaders headers = new HttpHeaders() {
            final MultivaluedMap<String, String> m = new MultivaluedHashMap<>();
            { m.putSingle("Host", "example.org"); m.putSingle("X-Corr", "123"); }
            @Override public List<String> getRequestHeader(String name) { return m.get(name); }
            @Override public String getHeaderString(String name) { return m.getFirst(name); }
            @Override public List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() { return List.of(); }
            @Override public List<java.util.Locale> getAcceptableLanguages() { return List.of(); }
            @Override public jakarta.ws.rs.core.MediaType getMediaType() { return null; }
            @Override public java.util.Locale getLanguage() { return null; }
            @Override public MultivaluedMap<String, String> getRequestHeaders() { return m; }
            @Override public Date getDate() { return null; }
            @Override public int getLength() { return -1; }
            @Override public Map<String, jakarta.ws.rs.core.Cookie> getCookies() { return Map.of(); }
        };

        Response r = res.get("p", "r", uriInfo, headers);
        assertEquals(200, r.getStatus());
        assertNotNull(seen[0]);
        assertNull(first(seen[0].headers(), "Host"));
        assertEquals("123", first(seen[0].headers(), "X-Corr"));
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        if (v == null) for (var e : headers.entrySet()) if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) { v = e.getValue(); break; }
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}

