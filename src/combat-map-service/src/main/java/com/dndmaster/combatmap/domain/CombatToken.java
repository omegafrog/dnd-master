package com.dndmaster.combatmap.domain;
import java.util.Objects; import java.util.Optional;
public final class CombatToken {
    private final TokenId id; private final TokenType type; private final TokenController controller; private final PlayerId ownerPlayerId; private final HostileObservationRule hostileObservationRule; private int elevation; private GridPosition position; private TokenDiscovery discovery;
    public CombatToken(TokenId id, TokenType type, GridPosition position, TokenController controller, PlayerId ownerPlayerId) {
        this(id, type, position, controller, ownerPlayerId, type == TokenType.PLAYER ? TokenDiscovery.REVEALED : TokenDiscovery.DISCOVERED, null);
    }
    public CombatToken(TokenId id, TokenType type, GridPosition position, TokenController controller, PlayerId ownerPlayerId, TokenDiscovery discovery) {
        this(id, type, position, controller, ownerPlayerId, discovery, null);
    }
    public CombatToken(TokenId id, TokenType type, GridPosition position, TokenController controller, PlayerId ownerPlayerId,
            HostileObservationRule hostileObservationRule) {
        this(id, type, position, controller, ownerPlayerId, type == TokenType.PLAYER ? TokenDiscovery.REVEALED : TokenDiscovery.DISCOVERED,
                hostileObservationRule);
    }
    public CombatToken(TokenId id, TokenType type, GridPosition position, TokenController controller, PlayerId ownerPlayerId,
            TokenDiscovery discovery, HostileObservationRule hostileObservationRule) {
        this(id, type, position, controller, ownerPlayerId, discovery, hostileObservationRule, 0);
    }
    public CombatToken(TokenId id, TokenType type, GridPosition position, TokenController controller, PlayerId ownerPlayerId,
            int elevation) {
        this(id, type, position, controller, ownerPlayerId, type == TokenType.PLAYER ? TokenDiscovery.REVEALED : TokenDiscovery.DISCOVERED,
                null, elevation);
    }
    public CombatToken(TokenId id, TokenType type, GridPosition position, TokenController controller, PlayerId ownerPlayerId,
            TokenDiscovery discovery, HostileObservationRule hostileObservationRule, int elevation) {
        this.id=Objects.requireNonNull(id); this.type=Objects.requireNonNull(type); this.position=Objects.requireNonNull(position); this.controller=Objects.requireNonNull(controller); this.discovery=Objects.requireNonNull(discovery);
        if (type == TokenType.PLAYER && (ownerPlayerId == null || controller != TokenController.PLAYER)) throw new IllegalArgumentException("PLAYER token requires player owner and controller");
        if (type != TokenType.PLAYER && (ownerPlayerId != null || controller != TokenController.AI_GAME_MASTER)) throw new IllegalArgumentException("NPC and ENEMY tokens require AI controller and no player owner");
        if (hostileObservationRule != null && type != TokenType.ENEMY && type != TokenType.BOSS) {
            throw new IllegalArgumentException("hostile observation rule requires an enemy token");
        }
        if (elevation < 0) throw new IllegalArgumentException("elevation must not be negative");
        this.ownerPlayerId=ownerPlayerId; this.hostileObservationRule = hostileObservationRule; this.elevation = elevation;
    }
    void moveTo(GridPosition position) { this.position=Objects.requireNonNull(position); }
    void setElevation(int elevation) { if (elevation < 0) throw new IllegalArgumentException("elevation must not be negative"); this.elevation=elevation; }
    public void discover() { discovery = TokenDiscovery.DISCOVERED; }
    public void reveal() { discovery = TokenDiscovery.REVEALED; }
    public TokenId id(){return id;} public TokenType type(){return type;} public GridPosition position(){return position;} public TokenDiscovery discovery(){return discovery;} public int elevation(){return elevation;}
    public java.util.Optional<HostileObservationRule> hostileObservationRule(){return java.util.Optional.ofNullable(hostileObservationRule);}
    public TokenController controller(){return controller;} public Optional<PlayerId> ownerPlayerId(){return Optional.ofNullable(ownerPlayerId);}
}
