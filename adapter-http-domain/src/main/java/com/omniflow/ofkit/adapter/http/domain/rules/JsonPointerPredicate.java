package com.omniflow.ofkit.adapter.http.domain.rules;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * JSON Pointer predicate that uses Jackson via reflection if available on the classpath.
 * If Jackson is not present, this predicate returns false.
 */
public final class JsonPointerPredicate implements ResponsePredicate {
    private final String pointer;
    private final String equals;
    private final String regex;
    private final boolean exists;

    public static JsonPointerPredicate equalsAt(String pointer, String value) {
        return new JsonPointerPredicate(pointer, value, null, false);
    }

    public static JsonPointerPredicate matchesAt(String pointer, String regex) {
        return new JsonPointerPredicate(pointer, null, regex, false);
    }

    public static JsonPointerPredicate existsAt(String pointer) {
        return new JsonPointerPredicate(pointer, null, null, true);
    }

    private JsonPointerPredicate(String pointer, String equals, String regex, boolean exists) {
        this.pointer = pointer;
        this.equals = equals;
        this.regex = regex;
        this.exists = exists;
    }

    @Override
    public boolean test(ResponseContext ctx) {
        try {
            Class<?> omClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            Object om = omClass.getConstructor().newInstance();
            Method readTree = omClass.getMethod("readTree", byte[].class);
            Object root = readTree.invoke(om, ctx.body() == null ? new byte[0] : ctx.body());
            if (root == null) return false;
            Method at = root.getClass().getMethod("at", String.class);
            Object node = at.invoke(root, pointer);
            if (node == null) return false;

            // node.isMissingNode()
            Method isMissingNode = node.getClass().getMethod("isMissingNode");
            boolean missing = (boolean) isMissingNode.invoke(node);
            if (exists) {
                return !missing;
            }

            // node.asText()
            Method asText = node.getClass().getMethod("asText");
            String text = (String) asText.invoke(node);

            if (equals != null) {
                return equals.equals(text);
            }
            if (regex != null) {
                return text != null && text.matches(regex);
            }
            return false;
        } catch (ClassNotFoundException e) {
            // Jackson not available → predicate cannot evaluate
            return false;
        } catch (Exception e) {
            // Parsing/evaluation failure → safe default false
            return false;
        }
    }
}
