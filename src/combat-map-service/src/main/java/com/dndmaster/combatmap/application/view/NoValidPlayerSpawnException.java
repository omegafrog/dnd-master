package com.dndmaster.combatmap.application.view;

public final class NoValidPlayerSpawnException extends IllegalStateException {
    public NoValidPlayerSpawnException() { super("NO_VALID_PLAYER_SPAWN"); }
}
