package com.dndmaster.ruleknowledge.application.definition;

import com.dndmaster.ruleknowledge.domain.definition.GameSystemDefinitionRevision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameSystemDefinitionRepository {
    Optional<GameSystemDefinitionRevision> findPublished(UUID rulebookId);
    Optional<GameSystemDefinitionRevision> findPublished(UUID rulebookId, long version);
    List<GameSystemDefinitionRevision> history(UUID rulebookId);
    void save(GameSystemDefinitionRevision revision);
}
