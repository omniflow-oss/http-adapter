package com.omniflow.ofkit.adapter.http.infra.rest;

import com.omniflow.ofkit.adapter.http.app.AdapterFacade;
import com.omniflow.ofkit.adapter.http.app.ProfileRegistry;
import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.Result;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.*;

@Path("/adapter")
@ApplicationScoped
public class AdapterResource {
    private static final Logger LOG = Logger.getLogger(AdapterResource.class);

    @Inject AdapterFacade facade;
    @Inject ProfileRegistry profiles;

    @GET
    @Path("/{profile}/{path:.*}")
    public Response get(@PathParam("profile") String profile,
                        @PathParam("path") String path,
                        @Context UriInfo uriInfo,
                        @Context HttpHeaders headers) throws Exception {
        return handle(profile, "GET", path, uriInfo, headers, null);
    }

    @DELETE
    @Path("/{profile}/{path:.*}")
    public Response delete(@PathParam("profile") String profile,
                           @PathParam("path") String path,
                           @Context UriInfo uriInfo,
                           @Context HttpHeaders headers) throws Exception {
        return handle(profile, "DELETE", path, uriInfo, headers, null);
    }

    @POST
    @Path("/{profile}/{path:.*}")
    public Response post(@PathParam("profile") String profile,
                         @PathParam("path") String path,
                         @Context UriInfo uriInfo,
                         @Context HttpHeaders headers,
                         byte[] body) throws Exception {
        return handle(profile, "POST", path, uriInfo, headers, body);
    }

    @PUT
    @Path("/{profile}/{path:.*}")
    public Response put(@PathParam("profile") String profile,
                        @PathParam("path") String path,
                        @Context UriInfo uriInfo,
                        @Context HttpHeaders headers,
                        byte[] body) throws Exception {
        return handle(profile, "PUT", path, uriInfo, headers, body);
    }

    @PATCH
    @Path("/{profile}/{path:.*}")
    public Response patch(@PathParam("profile") String profile,
                          @PathParam("path") String path,
                          @Context UriInfo uriInfo,
                          @Context HttpHeaders headers,
                          byte[] body) throws Exception {
        return handle(profile, "PATCH", path, uriInfo, headers, body);
    }

    private Response handle(String profileId, String method, String path,
                            UriInfo uriInfo, HttpHeaders headers, byte[] body) throws Exception {
        var profile = profiles.findById(profileId).orElseThrow(() -> new NotFoundException("Unknown profile"));
        // Allow header override of base URL for testing and flexible routing
        String base = headers.getHeaderString("X-OF-Target-Base");
        if (base == null || base.isBlank()) base = profile.baseUrl();
        if (base == null || base.isBlank()) {
            return Response.status(400).entity("Missing base_url for profile or X-OF-Target-Base header").type("text/plain").build();
        }
        String query = uriInfo.getRequestUri().getRawQuery();
        String target = base.endsWith("/") ? base.substring(0, base.length()-1) : base;
        target += "/" + path;
        if (query != null && !query.isEmpty()) target += "?" + query;

        Map<String, List<String>> hdrs = new HashMap<>();
        headers.getRequestHeaders().forEach((k, v) -> hdrs.put(k, List.copyOf(v)));
        // Remove Host header to avoid leakage
        hdrs.remove("Host");

        HttpRequest req = new HttpRequest(method, URI.create(target), hdrs, body);
        long t0 = System.nanoTime();
        LOG.debugf("profile=%s method=%s uri=%s", profileId, method, target);
        Result res = facade.handle(profileId, req);
        long totalMs = (System.nanoTime() - t0) / 1_000_000L;
        if (res instanceof Result.Success s) {
            var upstream = s.response();
            Response.ResponseBuilder rb = Response.status(upstream.statusCode());
            upstream.headers().forEach((k, vs) -> vs.forEach(v -> rb.header(k, v)));
            byte[] b = upstream.body() == null ? new byte[0] : upstream.body();
            rb.header("X-OF-Total-Latency-Ms", Long.toString(totalMs));
            rb.header("X-OF-Rule-Id", s.ruleId());
            LOG.debugf("success rule=%s status=%d total_ms=%d", s.ruleId(), upstream.statusCode(), totalMs);
            return rb.entity(b).build();
        } else if (res instanceof Result.Failure f) {
            var p = f.problem();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", p.type());
            payload.put("title", p.title());
            payload.put("status", p.status());
            if (p.detail() != null) payload.put("detail", p.detail());
            if (p.instance() != null) payload.put("instance", p.instance());
            if (p.extensions() != null && !p.extensions().isEmpty()) payload.putAll(p.extensions());
            LOG.debugf("failure rule=%s status=%d total_ms=%d", f.ruleId(), p.status(), totalMs);
            return Response.status(p.status())
                    .header("X-OF-Total-Latency-Ms", Long.toString(totalMs))
                    .header("X-OF-Rule-Id", f.ruleId())
                    .entity(payload).type("application/problem+json").build();
        }
        return Response.serverError().entity("Unknown result").build();
    }
}
