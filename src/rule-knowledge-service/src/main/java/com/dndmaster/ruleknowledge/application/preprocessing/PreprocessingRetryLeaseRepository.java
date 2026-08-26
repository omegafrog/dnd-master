package com.dndmaster.ruleknowledge.application.preprocessing;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface PreprocessingRetryLeaseRepository {
    RetryClaim claim(RulebookId documentId, String requestId, String candidateVersion, List<Integer> pages, Duration lease);

    boolean complete(RulebookId documentId, String requestId, String leaseToken, String candidateVersion, String resultVersion);

    default void release(RulebookId documentId, String requestId, String leaseToken) {
    }

    default Optional<String> completedResult(RulebookId documentId, String requestId) {
        return Optional.empty();
    }

    record RetryClaim(boolean acquired, boolean completed, String leaseToken, String resultVersion) {
        public Optional<String> completedResult() {
            return completed && resultVersion != null ? Optional.of(resultVersion) : Optional.empty();
        }
    }
}
