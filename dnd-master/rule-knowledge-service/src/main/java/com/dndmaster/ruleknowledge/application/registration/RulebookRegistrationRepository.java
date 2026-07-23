package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RulebookRegistrationRepository {
    Optional<StoredRulebookRegistration> findById(RulebookId id);
    Optional<StoredRulebookRegistration> findByOperationKey(String operationKey);
    Optional<StoredRulebookRegistration> findByOwnerAndContentHash(OwnerPlayerId owner, String contentHash);
    List<StoredRulebookRegistration> findByOwner(OwnerPlayerId owner);
    List<StoredRulebookRegistration> findByProcessingStatuses(List<ProcessingStatus> statuses);
    List<StoredRulebookRegistration> claimPending(Instant processingLeaseCutoff, int limit);
    StoredRulebookRegistration save(StoredRulebookRegistration registration);
}
