package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Collection;
import java.util.List;

public interface RuleEvidenceKeywordSearchPort {
    List<RuleSearchHit> searchKeyword(OwnerPlayerId owner, Collection<RulebookId> rulebooks, String query, int limit);
}
