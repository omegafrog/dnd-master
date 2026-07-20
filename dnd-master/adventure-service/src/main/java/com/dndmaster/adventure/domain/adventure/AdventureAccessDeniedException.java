package com.dndmaster.adventure.domain.adventure;

public final class AdventureAccessDeniedException extends RuntimeException {
    public AdventureAccessDeniedException() { super("adventure is owned by another player"); }
}
