package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CombatOperation {
    private final UUID id;
    private final String fingerprint;
    private boolean characterVerified;
    private Integer diceTotal;
    private boolean movementCompleted;
    private boolean aiStateControlled;
    private String judgment;
    private CombatCharacterMutation mutation = CombatCharacterMutation.none();
    private boolean combatEnded;
    private boolean outcomeApplied;

    public CombatOperation(UUID id, String fingerprint) {
        this.id = Objects.requireNonNull(id);
        this.fingerprint = Objects.requireNonNull(fingerprint);
    }

    public void requireSame(String candidate) {
        if (!fingerprint.equals(candidate)) throw new CombatIdempotencyConflictException();
    }
    public void characterVerified() { characterVerified = true; }
    public void diceRolled(int total) { diceTotal = total; }
    public void movementCompleted() { movementCompleted = true; }
    public void aiStateControlled() { aiStateControlled = true; }
    public void adjudicated(String value) {
        judgment = Objects.requireNonNull(value);
        combatEnded = false;
    }
    public void adjudicated(CombatOutcome outcome) {
        Objects.requireNonNull(outcome, "combat outcome must not be null");
        judgment = outcome.judgment();
        mutation = outcome.mutation();
        combatEnded = outcome.combatEnded();
    }
    public void outcomeApplied() { outcomeApplied = true; }
    public UUID id() { return id; }
    public boolean isCharacterVerified() { return characterVerified; }
    public Optional<Integer> diceTotal() { return Optional.ofNullable(diceTotal); }
    public boolean isMovementCompleted() { return movementCompleted; }
    public boolean isAiStateControlled() { return aiStateControlled; }
    public Optional<String> judgment() { return Optional.ofNullable(judgment); }
    public CombatCharacterMutation mutation() { return mutation; }
    public boolean isCombatEnded() { return combatEnded; }
    public boolean isOutcomeApplied() { return outcomeApplied; }
}
