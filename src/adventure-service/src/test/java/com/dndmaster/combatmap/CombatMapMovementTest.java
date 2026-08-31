package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.application.movement.*;
import com.dndmaster.combatmap.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CombatMapMovementTest {
    @Test
    void moves_only_owned_player_token_within_applied_edition_allowance() {
        Fixture fixture = new Fixture();
        CombatMapMovementService service = fixture.service(10);
        MovementPath path = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1)), 10);

        CombatMap moved = service.movePlayerToken(command(fixture, path, 0));

        assertEquals(new GridPosition(3, 1), moved.tokens().stream().filter(t -> t.id().equals(fixture.playerToken.id())).findFirst().orElseThrow().position());
        assertEquals(1, fixture.saves);
    }

    @Test
    void movement_refreshes_visibility_snapshot() {
        Fixture fixture = new Fixture();
        fixture.map.refreshVisibility(0);
        CombatMap moved = fixture.service(10).movePlayerToken(command(fixture,
                new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1)), 5), 0));
        assertTrue(moved.visibilitySnapshot().current().contains(new GridPosition(2, 1)));
    }

    @Test
    void replays_the_same_movement_command_without_double_applying() {
        Fixture fixture = new Fixture();
        CombatMapMovementService service = fixture.service(10);
        MovementPath path = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1)), 5);
        MovePlayerTokenCommand command = command(fixture, path, 0);

        CombatMap first = service.movePlayerToken(command);
        CombatMap second = service.movePlayerToken(command);

        assertEquals(new GridPosition(2, 1), first.tokens().stream().filter(t -> t.id().equals(fixture.playerToken.id())).findFirst().orElseThrow().position());
        assertEquals(1, second.version());
        assertEquals(fixture.commandId, second.operationKey());
        assertEquals(1, fixture.saves);
    }

    @Test
    void replays_an_older_movement_command_from_history_even_after_a_later_move() {
        Fixture fixture = new Fixture();
        CombatMapMovementService service = fixture.service(10);
        MovementPath firstPath = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1)), 5);
        MovePlayerTokenCommand firstCommand = command(fixture, firstPath, 0);
        CombatMap first = service.movePlayerToken(firstCommand);

        MovementPath secondPath = new MovementPath(List.of(new GridPosition(2, 1), new GridPosition(3, 1)), 5);
        service.movePlayerToken(new MovePlayerTokenCommand(
                fixture.map.id(), fixture.player, fixture.playerToken.id(), secondPath, "5E", UUID.randomUUID(), 1));

        CombatMap replay = service.movePlayerToken(firstCommand);

        assertEquals(new GridPosition(2, 1), first.tokens().stream().filter(t -> t.id().equals(fixture.playerToken.id())).findFirst().orElseThrow().position());
        assertEquals(new GridPosition(2, 1), replay.tokens().stream().filter(t -> t.id().equals(fixture.playerToken.id())).findFirst().orElseThrow().position());
        assertEquals(1, replay.version());
        assertEquals(fixture.commandId, replay.operationKey());
    }

    @Test
    void rejects_stale_movement_when_version_has_moved_on() {
        Fixture fixture = new Fixture();
        CombatMapMovementService service = fixture.service(10);
        MovementPath path = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1)), 5);
        service.movePlayerToken(command(fixture, path, 0));

        assertThrows(IllegalStateException.class, () -> service.movePlayerToken(new MovePlayerTokenCommand(
                fixture.map.id(), fixture.player, fixture.playerToken.id(), path, "5E", UUID.randomUUID(), 0)));
    }

    @Test
    void rejects_reusing_a_movement_command_id_for_different_payload() {
        Fixture fixture = new Fixture();
        CombatMapMovementService service = fixture.service(10);
        MovementPath firstPath = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1)), 5);
        UUID commandId = fixture.commandId;

        service.movePlayerToken(new MovePlayerTokenCommand(
                fixture.map.id(), fixture.player, fixture.playerToken.id(), firstPath, "5E", commandId, 0));

        MovementPath differentPath = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(1, 2)), 5);
        assertThrows(IllegalStateException.class, () -> service.movePlayerToken(new MovePlayerTokenCommand(
                fixture.map.id(), fixture.player, fixture.playerToken.id(), differentPath, "5E", commandId, 1)));
    }

    @Test
    void rejects_other_players_and_ai_controlled_tokens() {
        Fixture fixture = new Fixture();
        MovementPath path = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1)), 5);
        assertThrows(CombatMapMovementDeniedException.class,
                () -> fixture.service(30).movePlayerToken(new MovePlayerTokenCommand(
                        fixture.map.id(), new PlayerId(UUID.randomUUID()), fixture.playerToken.id(), path,
                        "5E", UUID.randomUUID(), 0)));
        MovementPath aiPath = new MovementPath(List.of(new GridPosition(4, 4), new GridPosition(5, 4)), 5);
        assertThrows(CombatMapMovementDeniedException.class,
                () -> fixture.service(30).movePlayerToken(new MovePlayerTokenCommand(
                        fixture.map.id(), fixture.player, fixture.enemyToken.id(), aiPath,
                        "5E", UUID.randomUUID(), 0)));
    }

    @Test
    void rejects_path_beyond_edition_allowance_or_across_obstacle() {
        Fixture fixture = new Fixture();
        MovementPath tooFar = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1)), 10);
        assertThrows(CombatMapMovementDeniedException.class, () -> fixture.service(5).movePlayerToken(command(fixture, tooFar, 0)));
        MovementPath blocked = new MovementPath(List.of(new GridPosition(1, 1), new GridPosition(1, 2)), 5);
        assertThrows(CombatMapMovementDeniedException.class, () -> fixture.service(30).movePlayerToken(command(fixture, blocked, 0)));
    }

    @Test
    void enforces_controller_and_layer_visibility_model() {
        PlayerId player = new PlayerId(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new CombatToken(
                new TokenId(UUID.randomUUID()), TokenType.PLAYER, new GridPosition(0, 0), TokenController.AI_GAME_MASTER, player));
        assertThrows(IllegalArgumentException.class, () -> new CombatToken(
                new TokenId(UUID.randomUUID()), TokenType.NPC, new GridPosition(0, 0), TokenController.PLAYER, null));
        Fixture fixture = new Fixture();
        assertEquals(List.of(LayerVisibility.PLAYER_VISIBLE, LayerVisibility.AI_ONLY),
                fixture.map.layers().stream().map(MapLayer::visibility).toList());
    }

    private static MovePlayerTokenCommand command(Fixture fixture, MovementPath path, long expectedVersion) {
        return new MovePlayerTokenCommand(
                fixture.map.id(), fixture.player, fixture.playerToken.id(), path, "5E", fixture.commandId, expectedVersion);
    }

    private static final class Fixture implements CombatMapRepository {
        final UUID commandId = UUID.randomUUID();
        final PlayerId player = new PlayerId(UUID.randomUUID());
        final CombatToken playerToken = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                new GridPosition(1, 1), TokenController.PLAYER, player);
        final CombatToken enemyToken = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.ENEMY,
                new GridPosition(4, 4), TokenController.AI_GAME_MASTER, null);
        CombatMap map = new CombatMap(
                new MapId(UUID.randomUUID()),
                new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()),
                new GridSpec(10, 10, 50, 5),
                List.of(playerToken, enemyToken),
                List.of(new GridPosition(1, 2)),
                List.of(new MapLayer("LIGHT", "bright", LayerVisibility.PLAYER_VISIBLE),
                        new MapLayer("FOG", "hidden", LayerVisibility.AI_ONLY)));
        int saves;
        final Map<UUID, CombatMap> history = new HashMap<>();

        CombatMapMovementService service(int allowance) {
            return new CombatMapMovementService(this, (ruleSet, edition) -> allowance);
        }

        @Override
        public Optional<CombatMap> findById(MapId id) {
            return id.equals(map.id()) ? Optional.of(cloneMap(map)) : Optional.empty();
        }

        @Override
        public Optional<CombatMap> findByCommandId(UUID commandId) {
            return Optional.ofNullable(history.get(commandId)).map(Fixture::cloneMap);
        }

        @Override
        public void save(CombatMap map) {
            this.map = map;
            saves++;
        }

        @Override
        public void save(CombatMap map, long persistedVersion, UUID operationKey, String operationFingerprint) {
            map.markPersisted(persistedVersion, operationKey, operationFingerprint);
            if (operationKey != null) {
                history.put(operationKey, cloneMap(map));
            }
            save(map);
        }

        private static CombatMap cloneMap(CombatMap source) {
            List<CombatToken> tokens = source.tokens().stream()
                    .map(token -> new CombatToken(
                            token.id(), token.type(), token.position(), token.controller(), token.ownerPlayerId().orElse(null)))
                    .toList();
            CombatMap copy = new CombatMap(
                    source.id(),
                    source.adventureId(),
                    source.ruleSetId(),
                    source.grid(),
                    source.ownerPlayerId(),
                    tokens,
                    source.obstacles(),
                    source.layers(),
                    source.version(),
                    source.operationKey(),
                    source.operationFingerprint());
            if (source.visibilitySnapshot() != null) copy.replaceVisibility(source.visibilitySnapshot());
            copy.replaceDoors(source.doors());
            return copy;
        }
    }
}
