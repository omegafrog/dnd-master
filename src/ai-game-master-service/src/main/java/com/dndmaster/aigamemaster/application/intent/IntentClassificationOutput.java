package com.dndmaster.aigamemaster.application.intent;

import java.util.Objects;

public record IntentClassificationOutput(QueryIntent intent) {
    public IntentClassificationOutput {
        intent = Objects.requireNonNull(intent, "intent must not be null");
    }

    public static IntentClassificationOutput fromModelText(String value) {
        return new IntentClassificationOutput(QueryIntent.fromModelText(value));
    }
}
