package com.omniflow.ofkit.adapter.http.domain.rules;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class BodyRegexPredicate implements ResponsePredicate {
    private final Pattern pattern;

    public BodyRegexPredicate(String regex) {
        this.pattern = Pattern.compile(regex, Pattern.DOTALL);
    }

    @Override
    public boolean test(ResponseContext ctx) {
        byte[] body = ctx.body();
        if (body == null) return false;
        String s = new String(body, StandardCharsets.UTF_8);
        return pattern.matcher(s).find();
    }
}

