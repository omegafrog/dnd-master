package com.dndmaster.aigamemaster.application.intent;

import java.util.Objects;

public interface IntentClassificationModelPort {
    IntentClassificationOutput classify(IntentClassificationInput input);

    record IntentClassificationInput(String question) {
        public IntentClassificationInput {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("question must not be blank");
            }
            question = question.trim();
        }
    }
}
