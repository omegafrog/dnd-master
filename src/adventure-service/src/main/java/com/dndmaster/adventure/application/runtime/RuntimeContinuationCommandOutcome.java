package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

/** A typed command payload that the Adventure Runtime can consume after movement interruption. */
public sealed interface RuntimeContinuationCommandOutcome
        permits CombatContinuationCommand, WarningContinuationCommand,
        DialogueContinuationCommand, ChaseContinuationCommand {
    UUID commandId();
    UUID turnId();
    UUID operationId();
    UUID hostileTokenId();
    String trigger();
}
