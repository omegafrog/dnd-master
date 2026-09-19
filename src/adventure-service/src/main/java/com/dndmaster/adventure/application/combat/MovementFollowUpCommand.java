package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

/** Adventure Runtime이 지도 이동 사실을 다음 진행으로 넘기는 typed 명령이다. */
public record MovementFollowUpCommand(UUID commandId, UUID operationId, UUID hostileTokenId, Kind kind, String trigger) {
    public MovementFollowUpCommand(UUID commandId, UUID operationId, Kind kind, String trigger) {
        this(commandId, operationId, null, kind, trigger);
    }
    public enum Kind { COMBAT, WARNING, DIALOGUE, CHASE, CONTINUATION }
    public MovementFollowUpCommand {
        Objects.requireNonNull(commandId); Objects.requireNonNull(operationId); Objects.requireNonNull(kind);
        if (trigger == null || trigger.isBlank()) throw new IllegalArgumentException("follow-up trigger must not be blank");
    }
    public static MovementFollowUpCommand hostileObserved(UUID operationId) {
        return forTrigger(operationId, null, "HOSTILE_OBSERVED",
                com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy.defaultPolicy());
    }
    public static MovementFollowUpCommand hostileObserved(UUID operationId, UUID hostileTokenId) {
        return forTrigger(operationId, hostileTokenId, "HOSTILE_OBSERVED",
                com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy.defaultPolicy());
    }
    public static MovementFollowUpCommand forTrigger(UUID operationId, String trigger,
            com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy policy) {
        return forTrigger(operationId, null, trigger, policy);
    }
    public static MovementFollowUpCommand forTrigger(UUID operationId, UUID hostileTokenId, String trigger,
            com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy policy) {
        return new MovementFollowUpCommand(UUID.nameUUIDFromBytes(("movement-follow-up:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), operationId, hostileTokenId,
                policy.determine(trigger), trigger);
    }
}
