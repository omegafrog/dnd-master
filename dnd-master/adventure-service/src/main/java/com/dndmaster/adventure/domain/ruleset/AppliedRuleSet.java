package com.dndmaster.adventure.domain.ruleset;

import java.util.Objects;

public final class AppliedRuleSet {
    private final RuleSetId id;
    private final AdventureId adventureId;
    private final OwnerPlayerId ownerPlayerId;
    private final DndEdition edition;
    private final SelectedRulebooks selectedRulebooks;

    public AppliedRuleSet(
            RuleSetId id,
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            DndEdition edition,
            SelectedRulebooks selectedRulebooks) {
        this.id = Objects.requireNonNull(id, "rule set id must not be null");
        this.adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        this.edition = Objects.requireNonNull(edition, "edition must not be null");
        this.selectedRulebooks = Objects.requireNonNull(selectedRulebooks, "selected rulebooks must not be null");
        if (selectedRulebooks.values().stream()
                .anyMatch(reference -> !reference.ownerPlayerId().equals(ownerPlayerId))) {
            throw new IllegalArgumentException("all selected rulebooks must have the rule set owner");
        }
    }

    public void authorizeApplication(OwnerPlayerId requestingOwner, RuleApplicationRequest request) {
        Objects.requireNonNull(requestingOwner, "requesting owner must not be null");
        Objects.requireNonNull(request, "rule application request must not be null");
        if (!ownerPlayerId.equals(requestingOwner)) {
            throw new RuleApplicationDeniedException("rule set is owned by another player");
        }
        if (!edition.equals(request.edition())) {
            throw new RuleApplicationDeniedException("rule edition is outside the applied rule set");
        }
        if (!selectedRulebooks.contains(request.rulebookId())) {
            throw new RuleApplicationDeniedException("rulebook is outside the applied rule set");
        }
    }

    public RuleSetId id() {
        return id;
    }

    public AdventureId adventureId() {
        return adventureId;
    }

    public OwnerPlayerId ownerPlayerId() {
        return ownerPlayerId;
    }

    public DndEdition edition() {
        return edition;
    }

    public SelectedRulebooks selectedRulebooks() {
        return selectedRulebooks;
    }
}
