package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

/** Adventure Runtime이 지도 이동 사실을 다음 진행으로 넘기는 typed 명령이다. */
public record MovementFollowUpCommand(UUID commandId, UUID operationId, UUID hostileTokenId, UUID turnId,
        Kind kind, String trigger, long mapVersion,
        java.util.List<CombatMapPreviewPosition> traversedPath,
        CombatMapPreviewPosition finalPosition, java.util.List<String> publicEvents,
        com.dndmaster.adventure.application.runtime.RuntimeCheckProposal playerCheck, Integer playerRoll) {
    public enum Kind { COMBAT }
    public MovementFollowUpCommand {
        Objects.requireNonNull(commandId, "follow-up command id must not be null");
        Objects.requireNonNull(operationId, "movement operation id must not be null");
        Objects.requireNonNull(turnId, "turn id must not be null");
        Objects.requireNonNull(kind, "follow-up kind must not be null");
        if (trigger == null || trigger.isBlank()) throw new IllegalArgumentException("follow-up trigger must not be blank");
        if (mapVersion < 0) throw new IllegalArgumentException("follow-up map version must not be negative");
        traversedPath = java.util.List.copyOf(traversedPath == null ? java.util.List.of() : traversedPath);
        publicEvents = java.util.List.copyOf(publicEvents == null ? java.util.List.of() : publicEvents);
        if ((playerCheck == null) != (playerRoll == null)
                || playerCheck != null && (!playerCheck.required()
                    || playerCheck.rollMethod() != com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.RollMethod.PLAYER
                    || playerRoll < 1 || playerRoll > 20 || !"1d20".equalsIgnoreCase(playerCheck.diceExpression()))) {
            throw new IllegalArgumentException("movement follow-up player roll does not match its required check");
        }
    }
    public MovementFollowUpCommand(UUID commandId, UUID operationId, UUID hostileTokenId, UUID turnId,
            Kind kind, String trigger, long mapVersion, java.util.List<CombatMapPreviewPosition> traversedPath,
            CombatMapPreviewPosition finalPosition, java.util.List<String> publicEvents) {
        this(commandId, operationId, hostileTokenId, turnId, kind, trigger, mapVersion, traversedPath, finalPosition,
                publicEvents, null, null);
    }
    public MovementFollowUpCommand(UUID commandId, UUID operationId, UUID hostileTokenId, UUID turnId,
            Kind kind, String trigger) {
        this(commandId, operationId, hostileTokenId, turnId, kind, trigger, 0, java.util.List.of(), null, java.util.List.of(), null, null);
    }
    public static MovementFollowUpCommand hostileObserved(UUID operationId, UUID turnId, UUID hostileTokenId) {
        return forKind(operationId, turnId, hostileTokenId, "HOSTILE_OBSERVED", Kind.COMBAT);
    }
    public static MovementFollowUpCommand forTrigger(UUID operationId, UUID turnId, UUID hostileTokenId, String trigger,
            com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy policy) {
        return forTrigger(operationId, turnId, hostileTokenId, trigger, policy, 0, java.util.List.of(), null, java.util.List.of());
    }
    public static MovementFollowUpCommand forTrigger(UUID operationId, UUID turnId, UUID hostileTokenId, String trigger,
            com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy policy, long mapVersion,
            java.util.List<CombatMapPreviewPosition> traversedPath, CombatMapPreviewPosition finalPosition,
            java.util.List<String> publicEvents) {
        Objects.requireNonNull(policy, "follow-up policy must not be null");
        return new MovementFollowUpCommand(UUID.nameUUIDFromBytes(("movement-follow-up:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), operationId, hostileTokenId,
                turnId, policy.determine(trigger), trigger, mapVersion, traversedPath, finalPosition, publicEvents);
    }

    public MovementFollowUpCommand afterPlayerRoll(
            com.dndmaster.adventure.application.runtime.RuntimeCheckProposal check, int result) {
        return new MovementFollowUpCommand(UUID.nameUUIDFromBytes(("movement-follow-up-final:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), operationId, hostileTokenId, turnId, kind,
                trigger, mapVersion, traversedPath, finalPosition, publicEvents, check, result);
    }

    public static MovementFollowUpCommand forKind(UUID operationId, UUID turnId, UUID hostileTokenId,
            String trigger, Kind kind) {
        return new MovementFollowUpCommand(UUID.nameUUIDFromBytes(("movement-follow-up:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), operationId, hostileTokenId, turnId,
                kind, trigger);
    }
}
