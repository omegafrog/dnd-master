package com.dndmaster.adventure.application.ruleset;

import com.dndmaster.adventure.domain.ruleset.AppliedRuleSet;
import com.dndmaster.adventure.domain.ruleset.RuleSetId;
import java.util.Optional;

public interface AppliedRuleSetRepository {
    Optional<AppliedRuleSet> findById(RuleSetId ruleSetId);

    void save(AppliedRuleSet ruleSet);
}
