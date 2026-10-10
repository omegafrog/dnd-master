package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuntimeTurnRepository {
    Optional<RuntimeTurn> findByTurnId(UUID turnId);
    Optional<RuntimeTurn> findByCommandId(UUID commandId);
    List<RuntimeTurn> findAllByAdventureId(AdventureId adventureId);
    default List<String> findKnownImmutableEvidenceIds(AdventureId adventureId) {
        return findAllByAdventureId(adventureId).stream().filter(turn -> turn.lifecycle().isCommitted())
                .flatMap(turn -> turn.evidencePack().all().stream()).map(RuntimeEvidence::immutableEvidenceId)
                .filter(java.util.Objects::nonNull).distinct().toList();
    }
    default List<RuntimeTurn> findAllByLifecycle(RuntimeTurnLifecycle lifecycle) { return List.of(); }
    default List<RuntimeTurn> findAllBySessionId(UUID sessionId) { return List.of(); }
    void save(RuntimeTurn turn);
}
