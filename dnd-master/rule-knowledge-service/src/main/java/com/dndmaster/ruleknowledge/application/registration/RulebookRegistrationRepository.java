package com.dndmaster.ruleknowledge.application.registration;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.List;
import java.util.Optional;

public interface RulebookRegistrationRepository {
    Optional<StoredRulebookRegistration> findById(RulebookId id);
    Optional<StoredRulebookRegistration> findByOperationKey(String operationKey);
    List<StoredRulebookRegistration> findByOwner(OwnerPlayerId owner);
    List<StoredRulebookRegistration> findByProcessingStatuses(List<ProcessingStatus> statuses);
    void save(StoredRulebookRegistration registration);
}
