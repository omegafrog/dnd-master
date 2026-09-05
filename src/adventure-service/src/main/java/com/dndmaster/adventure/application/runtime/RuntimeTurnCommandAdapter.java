package com.dndmaster.adventure.application.runtime;

@FunctionalInterface
public interface RuntimeTurnCommandAdapter {
    RuntimeTurnCommandExecution execute(RuntimeTurnCommand command);
}
