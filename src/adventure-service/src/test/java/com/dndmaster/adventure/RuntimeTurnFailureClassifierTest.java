package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.RuntimeTurnFailureClassifier;
import com.dndmaster.adventure.application.runtime.RuntimeTurnFailureCode;
import com.dndmaster.adventure.application.runtime.RuntimeTurnFailureStage;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeTurnFailureClassifierTest {
    @Test
    void onlyTransientProviderFailuresAreAutomaticallyRetryable() {
        var classifier = new RuntimeTurnFailureClassifier();
        var timeout = classifier.classify(UUID.randomUUID(), RuntimeTurnFailureStage.RESOLUTION,
                new IllegalStateException("provider timeout"), UUID.randomUUID(), 1);
        var citation = classifier.classify(UUID.randomUUID(), RuntimeTurnFailureStage.PRESENTATION,
                new IllegalStateException("citation invalid"), UUID.randomUUID(), 1);

        assertEquals(RuntimeTurnFailureCode.PROVIDER_TIMEOUT, timeout.failureCode());
        assertTrue(timeout.retryable());
        assertTrue(classifier.allowsAutomaticRetry(timeout));
        assertFalse(citation.retryable());
        assertFalse(classifier.allowsAutomaticRetry(citation));
    }

    @Test
    void safety_failure_is_not_shadowed_by_the_narration_keyword() {
        var failure = new RuntimeTurnFailureClassifier().classify(UUID.randomUUID(), RuntimeTurnFailureStage.SAFETY,
                new IllegalStateException("narration safety rejected"), UUID.randomUUID(), 1);
        assertEquals(RuntimeTurnFailureCode.SAFETY_FAILURE, failure.failureCode());
        assertFalse(failure.retryable());
    }
}
