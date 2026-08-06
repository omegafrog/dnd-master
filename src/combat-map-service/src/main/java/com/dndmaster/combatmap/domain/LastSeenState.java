package com.dndmaster.combatmap.domain;

public record LastSeenState(TokenId tokenId, TokenType type, GridPosition position, long expiresAtTurn) {
    public LastSeenState {
        if (tokenId == null || type == null || position == null || expiresAtTurn < 0) {
            throw new IllegalArgumentException("last seen state is invalid");
        }
    }
}
