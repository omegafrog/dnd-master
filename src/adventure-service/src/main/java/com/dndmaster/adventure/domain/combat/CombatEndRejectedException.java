package com.dndmaster.adventure.domain.combat;

public final class CombatEndRejectedException extends IllegalStateException {
    public CombatEndRejectedException(String code) { super(code); }
}
