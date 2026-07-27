package com.dndmaster.adventure.application.knowledge;

import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import java.util.Optional;

public interface SessionKnowledgeSetRepository {
    Optional<SessionKnowledgeSet> findBySessionId(SessionId sessionId);

    void save(SessionKnowledgeSet set);
}
