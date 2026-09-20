package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

/** Adventure Runtime이 지도 이동 사실을 다음 진행으로 넘기는 typed 명령이다. */
public record MovementFollowUpCommand(UUID commandId, UUID operationId, UUID hostileTokenId, UUID turnId,
        Kind kind, String trigger) {
    public enum Kind { COMBAT }
    public MovementFollowUpCommand {
        Objects.requireNonNull(commandId, "follow-up command id must not be null");
        Objects.requireNonNull(operationId, "movement operation id must not be null");
        Objects.requireNonNull(hostileTokenId, "hostile token id must not be null");
        Objects.requireNonNull(turnId, "turn id must not be null");
        Objects.requireNonNull(kind, "follow-up kind must not be null");
        if (trigger == null || trigger.isBlank()) throw new IllegalArgumentException("follow-up trigger must not be blank");
    }
    public static MovementFollowUpCommand hostileObserved(UUID operationId, UUID turnId, UUID hostileTokenId) {
        return forKind(operationId, turnId, hostileTokenId, "HOSTILE_OBSERVED", Kind.COMBAT);
    }
    public static MovementFollowUpCommand forTrigger(UUID operationId, UUID turnId, UUID hostileTokenId, String trigger,
            com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy policy) {
        return new MovementFollowUpCommand(UUID.nameUUIDFromBytes(("movement-follow-up:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), operationId, hostileTokenId,
                turnId, policy.determine(trigger), trigger);
    }

    public static MovementFollowUpCommand forKind(UUID operationId, UUID turnId, UUID hostileTokenId,
            String trigger, Kind kind) {
        return new MovementFollowUpCommand(UUID.nameUUIDFromBytes(("movement-follow-up:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), operationId, hostileTokenId, turnId,
                kind, trigger);
    }
}
