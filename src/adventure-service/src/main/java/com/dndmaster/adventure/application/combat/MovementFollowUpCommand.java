package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

/** Adventure Runtime이 지도 이동 사실을 다음 진행으로 넘기는 typed 명령이다. */
public record MovementFollowUpCommand(UUID commandId, UUID operationId, Kind kind, String trigger) {
    public enum Kind { COMBAT, WARNING, CONTINUATION }
    public MovementFollowUpCommand {
        Objects.requireNonNull(commandId); Objects.requireNonNull(operationId); Objects.requireNonNull(kind);
        if (trigger == null || trigger.isBlank()) throw new IllegalArgumentException("follow-up trigger must not be blank");
    }
    public static MovementFollowUpCommand hostileObserved(UUID operationId) {
        return new MovementFollowUpCommand(UUID.nameUUIDFromBytes(("movement-follow-up:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), operationId, Kind.CONTINUATION, "HOSTILE_OBSERVED");
    }
}
