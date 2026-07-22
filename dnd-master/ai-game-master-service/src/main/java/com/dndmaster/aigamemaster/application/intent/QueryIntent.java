package com.dndmaster.aigamemaster.application.intent;

import java.util.Locale;

public enum QueryIntent {
    RULE,
    STORY,
    MIXED,
    UNKNOWN;

    public static QueryIntent fromModelText(String value) {
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
