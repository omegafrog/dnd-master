package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import java.util.UUID;

public record CombatContinuationCommand(UUID commandId, UUID turnId, UUID operationId,
        UUID hostileTokenId, String trigger, MovementFollowUpCommand.Kind kind,
        String judgment, String narration, RuntimeCheckProposal playerInput,
        boolean waitingForPlayerInput, boolean combatStartRequested)
        implements RuntimeContinuationCommandOutcome {
    public CombatContinuationCommand {
        java.util.Objects.requireNonNull(commandId, "continuation command id must not be null");
        java.util.Objects.requireNonNull(turnId, "continuation turn id must not be null");
        java.util.Objects.requireNonNull(operationId, "continuation operation id must not be null");
        java.util.Objects.requireNonNull(kind, "continuation kind must not be null");
        if (trigger == null || trigger.isBlank() || judgment == null || judgment.isBlank()
                || narration == null || narration.isBlank() || playerInput == null) {
            throw new IllegalArgumentException("movement adjudication response is incomplete");
        }
        if (waitingForPlayerInput != (playerInput.required()
                && playerInput.rollMethod() == RuntimeCheckProposal.RollMethod.PLAYER)) {
            throw new IllegalArgumentException("movement adjudication waiting state does not match its player input");
        }
        if ("HOSTILE_OBSERVED".equals(trigger) != (hostileTokenId != null)) {
            throw new IllegalArgumentException("movement adjudication token identity does not match trigger");
        }
    }
    public CombatContinuationCommand(UUID commandId, UUID turnId, UUID operationId,
            UUID hostileTokenId, String trigger, MovementFollowUpCommand.Kind kind,
            String judgment, String narration, RuntimeCheckProposal playerInput,
            boolean combatStartRequested) {
        this(commandId, turnId, operationId, hostileTokenId, trigger, kind, judgment, narration,
                playerInput, playerInput != null && playerInput.required()
                        && playerInput.rollMethod() == RuntimeCheckProposal.RollMethod.PLAYER,
                combatStartRequested);
    }
    public CombatContinuationCommand(UUID commandId, UUID turnId, UUID operationId,
            UUID hostileTokenId, String trigger, MovementFollowUpCommand.Kind kind) {
        this(commandId, turnId, operationId, hostileTokenId, trigger, kind,
                "판정 완료", "사건을 확인했습니다. 다음 행동을 선택해 주세요.", RuntimeCheckProposal.none(), false, false);
    }
    public CombatContinuationCommand(UUID commandId, UUID turnId, UUID operationId,
            UUID hostileTokenId, String trigger) {
        this(commandId, turnId, operationId, hostileTokenId, trigger, MovementFollowUpCommand.Kind.COMBAT,
                "판정 완료", "사건을 확인했습니다. 다음 행동을 선택해 주세요.", RuntimeCheckProposal.none(), false, false);
    }
}
