package com.dndmaster.combatmap.domain;
import java.util.Objects; import java.util.Optional;
public final class CombatToken {
    private final TokenId id; private final TokenType type; private final TokenController controller; private final PlayerId ownerPlayerId; private GridPosition position;
    public CombatToken(TokenId id, TokenType type, GridPosition position, TokenController controller, PlayerId ownerPlayerId) {
        this.id=Objects.requireNonNull(id); this.type=Objects.requireNonNull(type); this.position=Objects.requireNonNull(position); this.controller=Objects.requireNonNull(controller);
        if (type == TokenType.PLAYER && (ownerPlayerId == null || controller != TokenController.PLAYER)) throw new IllegalArgumentException("PLAYER token requires player owner and controller");
        if (type != TokenType.PLAYER && (ownerPlayerId != null || controller != TokenController.AI_GAME_MASTER)) throw new IllegalArgumentException("NPC and ENEMY tokens require AI controller and no player owner");
        this.ownerPlayerId=ownerPlayerId;
    }
    void moveTo(GridPosition position) { this.position=Objects.requireNonNull(position); }
    public TokenId id(){return id;} public TokenType type(){return type;} public GridPosition position(){return position;}
    public TokenController controller(){return controller;} public Optional<PlayerId> ownerPlayerId(){return Optional.ofNullable(ownerPlayerId);}
}
