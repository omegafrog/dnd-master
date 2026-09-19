package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;

@FunctionalInterface
public interface MovementFollowUpPolicy {
    MovementFollowUpCommand.Kind determine(String trigger);

}
