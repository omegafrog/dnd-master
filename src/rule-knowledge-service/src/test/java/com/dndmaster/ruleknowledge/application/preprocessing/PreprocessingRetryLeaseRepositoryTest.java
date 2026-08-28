package com.dndmaster.ruleknowledge.application.preprocessing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreprocessingRetryLeaseRepositoryTest {
    @Test
    void duplicateRequestIsIdempotentAndOnlyLeaseOwnerCanComplete() {
        InMemoryPreprocessingRetryLeaseRepository repository = new InMemoryPreprocessingRetryLeaseRepository();
        RulebookId document = RulebookId.generate();
        var first = repository.claim(document, "retry-1", "candidate-1", List.of(2), Duration.ofMinutes(1));
        var duplicate = repository.claim(document, "retry-1", "candidate-1", List.of(2), Duration.ofMinutes(1));

        assertTrue(first.acquired());
        assertFalse(duplicate.acquired());
        assertFalse(repository.complete(document, "retry-1", "stale-token", "candidate-1", "candidate-2"));
        assertTrue(repository.complete(document, "retry-1", first.leaseToken(), "candidate-1", "candidate-2"));
        assertTrue(repository.claim(document, "retry-1", "candidate-1", List.of(2), Duration.ofMinutes(1)).completedResult().isPresent());
    }

    @Test
    void failedOrReviewRetryCanReleaseItsLeaseWithoutReleasingAnotherOwner() {
        InMemoryPreprocessingRetryLeaseRepository repository = new InMemoryPreprocessingRetryLeaseRepository();
        RulebookId document = RulebookId.generate();
        var first = repository.claim(document, "retry-2", "candidate-1", List.of(2), Duration.ofMinutes(1));

        repository.release(document, "retry-2", "stale-token");
        assertFalse(repository.claim(document, "retry-2", "candidate-1", List.of(2), Duration.ofMinutes(1)).acquired());

        repository.release(document, "retry-2", first.leaseToken());
        assertTrue(repository.claim(document, "retry-2", "candidate-1", List.of(2), Duration.ofMinutes(1)).acquired());
    }
}
