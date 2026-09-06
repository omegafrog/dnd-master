package com.dndmaster.adventure.application.combat;

import java.util.List;

public final class CombatCommandRejectedException extends RuntimeException {
    private final String code;
    private final List<String> violations;

    public CombatCommandRejectedException(String code, List<String> violations) {
        super(code);
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public String code() { return code; }
    public List<String> violations() { return violations; }
}
