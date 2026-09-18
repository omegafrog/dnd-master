package com.dndmaster.adventure.application.combat;

public final class CombatMapMovementPreviewRejectedException extends RuntimeException {
    private final int status;
    private final String code;

    public CombatMapMovementPreviewRejectedException(int status, String code) {
        super("combat map movement preview was rejected");
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
