package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OllamaModelNames {
    private static final Pattern NAME = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private OllamaModelNames() {
    }

    static Set<String> parse(String response) {
        Matcher matcher = NAME.matcher(response == null ? "" : response);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return Set.copyOf(names);
    }
}
