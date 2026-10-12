package com.dndmaster.ruleknowledge.application.definition;

import java.util.Optional;
import java.util.UUID;

public interface PublishedRulebookActionSourceRepository {
    Optional<PublishedRulebookActionSource> findCurrent(UUID documentId);
}
