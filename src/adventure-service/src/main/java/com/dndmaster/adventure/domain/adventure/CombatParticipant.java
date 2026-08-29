package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;

public record CombatParticipant(String participantId, Role role, String name,
        int minimumCount, int maximumCount, List<String> citationKeys) {
    public enum Role { ENEMY, BOSS }

    public CombatParticipant {
        participantId = required(participantId, "participant id");
        role = Objects.requireNonNull(role, "participant role");
        name = required(name, "participant name");
        if (minimumCount < 1 || maximumCount < minimumCount) {
            throw new IllegalArgumentException("participant count range is invalid");
        }
        citationKeys = citationKeys == null ? List.of() : citationKeys.stream()
                .map(value -> required(value, "participant citation key")).distinct().toList();
    }

    public CombatParticipant(String participantId, Role role, String name, int count, List<String> citationKeys) {
        this(participantId, role, name, count, count, citationKeys);
    }

    public static CombatParticipant enemy(String participantId, String name, int minimumCount, int maximumCount,
            List<String> citationKeys) {
        return new CombatParticipant(participantId, Role.ENEMY, name, minimumCount, maximumCount, citationKeys);
    }

    public static CombatParticipant boss(String participantId, String name, List<String> citationKeys) {
        return new CombatParticipant(participantId, Role.BOSS, name, 1, 1, citationKeys);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
