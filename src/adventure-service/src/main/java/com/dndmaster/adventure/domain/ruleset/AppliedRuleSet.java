package com.dndmaster.adventure.domain.ruleset;

import java.util.Objects;

public final class AppliedRuleSet {
    private final RuleSetId id;
    private final DndEdition edition;
    private final SelectedRulebooks selectedRulebooks;

    public AppliedRuleSet(
            RuleSetId id,
            DndEdition edition,
            SelectedRulebooks selectedRulebooks) {
        this.id = Objects.requireNonNull(id, "rule set id must not be null");
        this.edition = Objects.requireNonNull(edition, "edition must not be null");
        this.selectedRulebooks = Objects.requireNonNull(selectedRulebooks, "selected rulebooks must not be null");
    }

    public void authorizeApplication(OwnerPlayerId requestingOwner, RuleApplicationRequest request) {
        Objects.requireNonNull(requestingOwner, "requesting owner must not be null");
        Objects.requireNonNull(request, "rule application request must not be null");
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

    public DndEdition edition() {
        return edition;
    }

    public SelectedRulebooks selectedRulebooks() {
        return selectedRulebooks;
    }
}
