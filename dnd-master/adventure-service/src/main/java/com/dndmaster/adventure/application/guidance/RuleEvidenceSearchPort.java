package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;

public interface RuleEvidenceSearchPort {
    List<RuleEvidence> search(
            OwnerPlayerId owner,
            List<KnowledgeDocumentId> selectedKnowledgeDocuments,
            String situation,
            RuleQueryIntent queryIntent);
}
