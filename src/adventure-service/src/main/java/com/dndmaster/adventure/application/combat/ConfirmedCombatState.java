package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Private, versioned combat state supplied to GM narration after an action commits. */
public record ConfirmedCombatState(long encounterVersion, List<Enemy> enemies) {
    public ConfirmedCombatState {
        if (encounterVersion < 1) throw new IllegalArgumentException("encounter version must be positive");
        enemies = List.copyOf(Objects.requireNonNull(enemies, "enemy state must not be null"));
    }

    public static ConfirmedCombatState from(CombatEncounter encounter) {
        Objects.requireNonNull(encounter, "combat encounter must not be null");
        List<Enemy> enemies = encounter.participants().stream()
                .filter(participant -> participant.controller() == CombatParticipant.Controller.AI)
                .filter(participant -> participant.statBlock() != null)
                .map(participant -> new Enemy(participant.participantId(), participant.displayName(),
                        participant.currentHitPoints(), participant.statBlock().hitPointMaximum(), participant.isDefeated()))
                .toList();
        return new ConfirmedCombatState(encounter.version(), enemies);
    }

    public record Enemy(UUID participantId, String displayName, int currentHitPoints, int maximumHitPoints,
                        boolean defeated) {
        public Enemy {
            Objects.requireNonNull(participantId, "enemy participant id must not be null");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("enemy name must not be blank");
            if (maximumHitPoints < 1 || currentHitPoints < 0 || currentHitPoints > maximumHitPoints) {
                throw new IllegalArgumentException("enemy hit points are invalid");
            }
            if (defeated != (currentHitPoints == 0)) throw new IllegalArgumentException("enemy defeat state must match hit points");
        }
    }
}
