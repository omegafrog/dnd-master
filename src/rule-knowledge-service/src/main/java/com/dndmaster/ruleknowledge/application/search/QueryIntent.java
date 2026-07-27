package com.dndmaster.ruleknowledge.application.search;

import java.util.Locale;

public enum QueryIntent {
    RULE,
    STORY,
    MIXED,
    UNKNOWN;

    public static QueryIntent fromWireValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (QueryIntent intent : values()) {
            if (intent.name().equals(normalized)) {
                return intent;
            }
        }
        return UNKNOWN;
    }
}
