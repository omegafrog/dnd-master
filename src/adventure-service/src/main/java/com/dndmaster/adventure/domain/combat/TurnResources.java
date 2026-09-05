package com.dndmaster.adventure.domain.combat;
public record TurnResources(int movement, boolean actionAvailable, boolean bonusActionAvailable, boolean reactionAvailable) {
    public static TurnResources initial() { return new TurnResources(30, true, true, true); }
}
