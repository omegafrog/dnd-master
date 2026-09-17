package com.dndmaster.aigamemaster.application.intent;

import java.util.Objects;
import java.util.UUID;

public interface IntentClassificationModelPort {
    IntentClassificationOutput classify(IntentClassificationInput input);

    record IntentClassificationInput(UUID soloPlayerId, String question) {
        public IntentClassificationInput {
            Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("question must not be blank");
            }
            question = question.trim();
        }
    }
}
