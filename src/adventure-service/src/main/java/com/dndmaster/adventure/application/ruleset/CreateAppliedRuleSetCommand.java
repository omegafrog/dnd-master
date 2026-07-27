package com.dndmaster.adventure.application.ruleset;

import com.dndmaster.adventure.domain.ruleset.AdventureId;
import com.dndmaster.adventure.domain.ruleset.DndEdition;
import com.dndmaster.adventure.domain.ruleset.OwnerPlayerId;
import com.dndmaster.adventure.domain.ruleset.RulebookId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record CreateAppliedRuleSetCommand(
        AdventureId adventureId,
        OwnerPlayerId ownerPlayerId,
        DndEdition edition,
        List<RulebookId> rulebookIds) {
    public CreateAppliedRuleSetCommand(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            DndEdition edition,
            Collection<RulebookId> rulebookIds) {
        this(adventureId, ownerPlayerId, edition, List.copyOf(rulebookIds));
    }

    public CreateAppliedRuleSetCommand {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(edition, "edition must not be null");
        rulebookIds = List.copyOf(Objects.requireNonNull(rulebookIds, "rulebook ids must not be null"));
    }
}
