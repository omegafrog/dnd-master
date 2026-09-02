package com.dndmaster.adventure.application.ruleset;

import com.dndmaster.adventure.domain.ruleset.AppliedRuleSet;
import com.dndmaster.adventure.domain.ruleset.RegisteredRulebookReference;
import com.dndmaster.adventure.domain.ruleset.RuleApplicationRequest;
import com.dndmaster.adventure.domain.ruleset.RuleSetId;
import com.dndmaster.adventure.domain.ruleset.RuleApplicationDeniedException;
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
        return saveRuleSet(RuleSetId.generate(), command);
    }

    public AppliedRuleSet saveRuleSet(RuleSetId ruleSetId, CreateAppliedRuleSetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var existing = repository.findById(Objects.requireNonNull(ruleSetId, "rule set id must not be null"));
        if (existing.isPresent()) {
            // Rule sets are shared reference data.  Reusing an existing id must
            // not make it exclusive to the adventure that first applied it.
            // Keep the original immutable snapshot and let normal authorization
            // checks govern who may use it.
            return existing.get();
        }
        var references = command.rulebookIds().stream()
                .map(rulebookId -> {
                    if (!ownershipHttpPort.isOwnedBy(rulebookId, command.ownerPlayerId())) {
                        throw new RulebookOwnershipDeniedException();
                    }
                    return new RegisteredRulebookReference(rulebookId, command.ownerPlayerId());
                })
                .toList();
        var ruleSet = new AppliedRuleSet(
                ruleSetId,
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

    public AppliedRuleSet readRuleSet(RuleSetId ruleSetId, com.dndmaster.adventure.domain.ruleset.OwnerPlayerId requestingOwner) {
        AppliedRuleSet ruleSet = repository.findById(Objects.requireNonNull(ruleSetId, "rule set id must not be null"))
                .orElseThrow(AppliedRuleSetNotFoundException::new);
        if (!ruleSet.ownerPlayerId().equals(requestingOwner)) throw new RuleApplicationDeniedException("rule set is owned by another player");
        return ruleSet;
    }
}
