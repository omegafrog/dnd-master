package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.Collection;
import java.util.List;

public interface RuleEvidenceSearchPort {
    List<RuleSearchHit> search(
            OwnerPlayerId ownerPlayerId,
            Collection<KnowledgeDocumentId> selectedKnowledgeDocumentIds,
            float[] queryEmbedding,
            QueryIntent queryIntent,
            int limit);
}
