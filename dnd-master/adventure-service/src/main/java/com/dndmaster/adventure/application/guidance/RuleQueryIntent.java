package com.dndmaster.adventure.application.guidance;

import java.util.Locale;

public enum RuleQueryIntent {
    RULE,
    STORY,
    MIXED,
    UNKNOWN;

    public static RuleQueryIntent fromWireValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (RuleQueryIntent intent : values()) {
            if (intent.name().equals(normalized)) {
                return intent;
            }
        }
        return UNKNOWN;
    }
}
