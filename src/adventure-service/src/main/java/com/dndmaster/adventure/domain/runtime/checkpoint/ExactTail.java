package com.dndmaster.adventure.domain.runtime.checkpoint;

import java.util.Objects;

public record ExactTail(String playerInput, String precedingScene, String lastGmResponse, String currentTurn,
                        String currentRound, String location, String mapState, String fogOfWar, String unresolvedChoice) {
    public ExactTail {
        playerInput = required(playerInput, "player input"); precedingScene = required(precedingScene, "preceding scene");
        lastGmResponse = required(lastGmResponse, "last GM response"); currentTurn = required(currentTurn, "current turn");
        currentRound = required(currentRound, "current round"); location = required(location, "location");
        mapState = required(mapState, "map state"); fogOfWar = required(fogOfWar, "fog of war");
        unresolvedChoice = required(unresolvedChoice, "unresolved choice");
    }
    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        return value;
    }
}
