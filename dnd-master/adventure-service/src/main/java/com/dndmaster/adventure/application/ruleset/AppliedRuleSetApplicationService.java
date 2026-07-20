package com.dndmaster.adventure.application.ruleset;

import com.dndmaster.adventure.domain.ruleset.AppliedRuleSet;
import com.dndmaster.adventure.domain.ruleset.RegisteredRulebookReference;
import com.dndmaster.adventure.domain.ruleset.RuleApplicationRequest;
import com.dndmaster.adventure.domain.ruleset.RuleSetId;
import com.dndmaster.adventure.domain.ruleset.SelectedRulebooks;
import java.util.Objects;

public final class AppliedRuleSetApplicationService {
    private final AppliedRuleSetRepository repository;
    private final RulebookOwnershipHttpPort ownershipHttpPort;

    public AppliedRuleSetApplicationService(
            AppliedRuleSetRepository repository, RulebookOwnershipHttpPort ownershipHttpPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.ownershipHttpPort = Objects.requireNonNull(ownershipHttpPort, "ownership HTTP port must not be null");
    }

    public AppliedRuleSet saveRuleSet(CreateAppliedRuleSetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var references = command.rulebookIds().stream()
                .map(rulebookId -> {
                    if (!ownershipHttpPort.isOwnedBy(rulebookId, command.ownerPlayerId())) {
                        throw new RulebookOwnershipDeniedException();
                    }
                    return new RegisteredRulebookReference(rulebookId, command.ownerPlayerId());
                })
                .toList();
        var ruleSet = new AppliedRuleSet(
                RuleSetId.generate(),
                command.adventureId(),
                command.ownerPlayerId(),
                command.edition(),
                new SelectedRulebooks(references));
        repository.save(ruleSet);
        return ruleSet;
    }

    public AppliedRuleSet useRuleSet(
            RuleSetId ruleSetId,
            com.dndmaster.adventure.domain.ruleset.OwnerPlayerId requestingOwner,
            RuleApplicationRequest request) {
        AppliedRuleSet ruleSet = repository.findById(
                        Objects.requireNonNull(ruleSetId, "rule set id must not be null"))
                .orElseThrow(AppliedRuleSetNotFoundException::new);
        ruleSet.authorizeApplication(requestingOwner, request);
        return ruleSet;
    }
}
