package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import java.util.List;
import java.util.Objects;

/** Shared party turn cursor. Direct turns wait; agent turns may advance automatically. */
public record TurnCursor(List<AdventurePartyMember> party, int index) {
    public TurnCursor {
        party = List.copyOf(Objects.requireNonNull(party, "party must not be null"));
        if (party.isEmpty()) throw new IllegalArgumentException("party must not be empty");
        if (index < 0 || index >= party.size()) throw new IllegalArgumentException("turn cursor index out of range");
    }

    public static TurnCursor start(List<AdventurePartyMember> party) {
        return new TurnCursor(party, 0);
    }

    public AdventurePartyMember current() {
        return party.get(index);
    }

    public boolean waitingForDirectInput() {
        return current().controlMode() == ControlMode.DIRECT;
    }

    public TurnCursor advanceAfterDirectInput(CharacterSheetId characterSheetId) {
        requireCurrent(ControlMode.DIRECT, characterSheetId);
        return next();
    }

    public TurnCursor advanceAfterAgentTurn(CharacterSheetId characterSheetId) {
        requireCurrent(ControlMode.AGENT, characterSheetId);
        return next();
    }

    private void requireCurrent(ControlMode mode, CharacterSheetId characterSheetId) {
        if (current().controlMode() != mode || !current().characterSheetId().equals(characterSheetId)) {
            throw new IllegalStateException("turn does not belong to current " + mode + " character");
        }
    }

    private TurnCursor next() {
        return new TurnCursor(party, (index + 1) % party.size());
    }
}
