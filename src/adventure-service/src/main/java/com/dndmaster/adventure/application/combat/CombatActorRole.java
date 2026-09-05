package com.dndmaster.adventure.application.combat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Stable combat role markers.
 *
 * <p>The first release exposed four enum values and callers still iterate that
 * legacy set. AI is therefore an additive application marker rather than an
 * enum constant: new code can use {@link #AI}, while {@link #values()} keeps
 * the legacy contract intact.</p>
 */
public final class CombatActorRole {
    public static final CombatActorRole PLAYER = new CombatActorRole("PLAYER");
    public static final CombatActorRole NPC = new CombatActorRole("NPC");
    public static final CombatActorRole ENEMY = new CombatActorRole("ENEMY");
    public static final CombatActorRole SECRET_CHECK = new CombatActorRole("SECRET_CHECK");
    public static final CombatActorRole AI = new CombatActorRole("AI");

    private static final CombatActorRole[] LEGACY_VALUES = {PLAYER, NPC, ENEMY, SECRET_CHECK};

    private final String name;

    private CombatActorRole(String name) {
        this.name = name;
    }

    /** Returns the pre-AI role set for legacy enum iteration callers. */
    public static CombatActorRole[] values() {
        return Arrays.copyOf(LEGACY_VALUES, LEGACY_VALUES.length);
    }

    @JsonCreator
    public static CombatActorRole valueOf(String name) {
        return switch (name) {
            case "PLAYER" -> PLAYER;
            case "NPC" -> NPC;
            case "ENEMY" -> ENEMY;
            case "SECRET_CHECK" -> SECRET_CHECK;
            case "AI" -> AI;
            default -> throw new IllegalArgumentException("No combat actor role constant " + name);
        };
    }

    @JsonValue
    public String name() {
        return name;
    }

    public boolean isAi() {
        return this == AI;
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
