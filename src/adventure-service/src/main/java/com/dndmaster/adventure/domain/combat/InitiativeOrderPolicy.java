package com.dndmaster.adventure.domain.combat;

import java.util.Comparator;
import java.util.List;

public final class InitiativeOrderPolicy {
    private InitiativeOrderPolicy() {}
    public static InitiativeOrder order(List<CombatParticipant> participants) {
        if (participants == null || participants.isEmpty()) throw new IllegalArgumentException("participants required");
        var ordered = participants.stream().sorted(Comparator.comparingInt(CombatParticipant::initiative)
                .reversed().thenComparing(p -> p.participantId().toString())).toList();
        return new InitiativeOrder(ordered.stream().map(CombatParticipant::participantId).toList());
    }
}
