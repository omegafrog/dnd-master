package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StartAdventureCommand(AdventureId adventureId, SessionId sessionId, OwnerPlayerId ownerPlayerId,
        UUID scenarioPackageId, long scenarioPackageRevision, ScenarioId scenarioId, RuleSetId ruleSetId,
        List<AdventurePartyMember> party, AdventureContext initialContext, UUID requestId) {
    public StartAdventureCommand {
        Objects.requireNonNull(adventureId); Objects.requireNonNull(sessionId); Objects.requireNonNull(ownerPlayerId);
        Objects.requireNonNull(scenarioPackageId); Objects.requireNonNull(scenarioId); Objects.requireNonNull(ruleSetId);
        Objects.requireNonNull(party); Objects.requireNonNull(initialContext); Objects.requireNonNull(requestId);
        if (scenarioPackageRevision < 1) throw new IllegalArgumentException("scenario package revision must be positive");
        party = List.copyOf(party);
    }
}
