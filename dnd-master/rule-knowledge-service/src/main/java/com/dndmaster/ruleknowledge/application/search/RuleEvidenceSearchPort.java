package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;

import java.util.Collection;
import java.util.List;

public interface RuleEvidenceSearchPort {
    List<RuleSearchHit> search(
            OwnerPlayerId ownerPlayerId,
            Collection<RulebookId> selectedRulebookIds,
            float[] queryEmbedding,
            int limit);
}
