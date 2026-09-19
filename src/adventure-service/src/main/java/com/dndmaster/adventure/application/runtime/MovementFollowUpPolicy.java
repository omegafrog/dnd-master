package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;

@FunctionalInterface
public interface MovementFollowUpPolicy {
    MovementFollowUpCommand.Kind determine(String trigger);

    static MovementFollowUpPolicy defaultPolicy() {
        return trigger -> switch (trigger) {
            case "HOSTILE_OBSERVED" -> MovementFollowUpCommand.Kind.COMBAT;
            case "FEATURE_REVEALED", "DANGER_WARNING" -> MovementFollowUpCommand.Kind.WARNING;
            case "NPC_CONTACT" -> MovementFollowUpCommand.Kind.DIALOGUE;
            case "CHASE_STARTED" -> MovementFollowUpCommand.Kind.CHASE;
            default -> MovementFollowUpCommand.Kind.CONTINUATION;
        };
    }
}
