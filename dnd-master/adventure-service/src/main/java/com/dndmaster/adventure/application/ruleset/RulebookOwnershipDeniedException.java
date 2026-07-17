package com.dndmaster.adventure.application.ruleset;

public final class RulebookOwnershipDeniedException extends RuntimeException {
    public RulebookOwnershipDeniedException() {
        super("one or more selected rulebooks are owned by another player");
    }
}
