package com.omniflow.ofkit.adapter.http.domain.rules;

import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;
import com.omniflow.ofkit.adapter.http.domain.model.ProblemDetails;
import com.omniflow.ofkit.adapter.http.domain.model.Result;

import java.util.List;
import java.util.Map;

/**
 * Inversed logic evaluation: success → errors → generic.
 */
public final class RuleEngine {

    public Result evaluate(ResponseContext ctx,
                           List<SuccessRule> success,
                           List<ErrorRule> errors,
                           ProblemDetails genericProblem) {
        // 1) Success rules
        for (SuccessRule r : success) {
            if (r.when().test(ctx)) {
                HttpResponse upstream = new HttpResponse(ctx.status(), ctx.headers(), ctx.body());
                if (r.pickPointer() != null) {
                    byte[] extracted = tryPickJsonPointer(ctx.body(), r.pickPointer());
                    if (extracted != null) {
                        upstream = new HttpResponse(ctx.status(), ctx.headers(), extracted);
                    }
                }
                return new Result.Success(upstream, r.id());
            }
        }
        // 2) Specific errors
        for (ErrorRule r : errors) {
            if (r.when().test(ctx)) {
                return new Result.Failure(r.problem(), r.id());
            }
        }
        // 3) Generic RFC-7807
        ProblemDetails generic = new ProblemDetails(
                genericProblem.type(),
                genericProblem.title(),
                genericProblem.status(),
                // Basic detail templating for status
                "No rule matched (status=" + ctx.status() + ")",
                null,
                Map.of()
        );
        return new Result.Failure(generic, "generic_problem");
    }

    private static byte[] tryPickJsonPointer(byte[] body, String pointer) {
        if (body == null) return null;
        try {
            Class<?> omClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            Object om = omClass.getConstructor().newInstance();
            java.lang.reflect.Method readTree = omClass.getMethod("readTree", byte[].class);
            Object root = readTree.invoke(om, body);
            if (root == null) return null;
            java.lang.reflect.Method at = root.getClass().getMethod("at", String.class);
            Object node = at.invoke(root, pointer);
            if (node == null) return null;
            java.lang.reflect.Method isMissingNode = node.getClass().getMethod("isMissingNode");
            boolean missing = (boolean) isMissingNode.invoke(node);
            if (missing) return null;
            java.lang.reflect.Method toString = node.getClass().getMethod("toString");
            String json = (String) toString.invoke(node);
            return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable ignore) {
            return null;
        }
    }
}
