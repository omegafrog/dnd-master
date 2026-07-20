package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.inquiry.RulebookId;
import java.util.List;

public interface RuleEvidenceSearchPort {
    List<RuleEvidence> search(OwnerPlayerId owner, List<RulebookId> selectedRulebooks, String situation);
}
