package com.omniflow.ofkit.adapter.http.domain.rules;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class HeaderRegexPredicate implements ResponsePredicate {
    private final String headerName;
    private final Pattern pattern;

    public HeaderRegexPredicate(String headerName, String regex) {
        this.headerName = headerName;
        this.pattern = Pattern.compile(regex);
    }

    @Override
    public boolean test(ResponseContext ctx) {
        Map<String, List<String>> headers = ctx.headers();
        if (headers == null) return false;
        List<String> values = headers.get(headerName);
        if (values == null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(headerName)) {
                    values = e.getValue();
                    break;
                }
            }
        }
        if (values == null) return false;
        return values.stream().anyMatch(v -> v != null && pattern.matcher(v).find());
    }
}
