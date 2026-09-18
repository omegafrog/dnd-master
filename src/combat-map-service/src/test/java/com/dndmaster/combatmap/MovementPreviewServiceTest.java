package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.CombatMapRepository;
import com.dndmaster.combatmap.application.movement.MovementPreview;
import com.dndmaster.combatmap.application.movement.MovementPreviewRequest;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapBoundary;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureProvenance;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovementPreviewServiceTest {
    @Test
    void returns_deterministic_minimum_cost_preview_without_persisting_or_using_hidden_features() {
        Fixture fixture = new Fixture();
        CombatMapMovementService service = fixture.service();
        MovementPreviewRequest request = fixture.request(List.of());

        MovementPreview first = service.preview(request);
        MovementPreview second = service.preview(request);

        assertEquals(first, second);
        assertEquals(List.of(new GridPosition(1, 1), new GridPosition(2, 0), new GridPosition(3, 1)), first.orderedPositions());
        assertEquals(10, first.distance());
        assertEquals(0, first.baseMapVersion());
        assertEquals(0, fixture.saves);
        assertEquals(new GridPosition(1, 1), fixture.map.tokens().getFirst().position());
    }

    @Test
    void avoids_public_blockers_but_hidden_enemy_and_feature_do_not_change_preview() {
        Fixture fixture = new Fixture();
        MovementPreview visibleRoute = fixture.service().preview(fixture.request(List.of()));
        fixture.map = fixture.mapWithHiddenChanges();

        MovementPreview hiddenRoute = fixture.service().preview(fixture.request(List.of()));

        assertEquals(visibleRoute.orderedPositions(), hiddenRoute.orderedPositions());
        assertEquals(visibleRoute.distance(), hiddenRoute.distance());
        assertEquals(visibleRoute.fingerprint(), hiddenRoute.fingerprint());
    }

    @Test
    void routes_each_waypoint_in_order_and_rejects_stale_or_invalid_requests() {
        Fixture fixture = new Fixture();
        MovementPreview preview = fixture.service().preview(fixture.request(List.of(new GridPosition(2, 3))));

        assertEquals(true, preview.orderedPositions().contains(new GridPosition(2, 3)));
        assertEquals(true, preview.orderedPositions().indexOf(new GridPosition(2, 3)) < preview.orderedPositions().size() - 1);
        assertEquals(new GridPosition(3, 1), preview.orderedPositions().getLast());
        assertThrows(IllegalStateException.class, () -> fixture.service().preview(fixture.request(1, List.of())));
        assertThrows(RuntimeException.class, () -> fixture.service().preview(fixture.request(new GridPosition(2, 2), List.of())));
    }

    private static final class Fixture implements CombatMapRepository {
        final PlayerId player = new PlayerId(UUID.randomUUID());
        final TokenId tokenId = new TokenId(UUID.randomUUID());
        CombatMap map = baseMap();
        int saves;

        CombatMapMovementService service() {
            return new CombatMapMovementService(this, (ruleSet, edition) -> 30);
        }

        MovementPreviewRequest request(List<GridPosition> waypoints) {
            return request(map.version(), new GridPosition(3, 1), waypoints);
        }

        MovementPreviewRequest request(int version, List<GridPosition> waypoints) {
            return request(version, new GridPosition(3, 1), waypoints);
        }

        MovementPreviewRequest request(GridPosition destination, List<GridPosition> waypoints) {
            return request(map.version(), destination, waypoints);
        }

        MovementPreviewRequest request(long version, GridPosition destination, List<GridPosition> waypoints) {
            return new MovementPreviewRequest(map.id(), player, tokenId, destination, waypoints, "5E", version);
        }

        CombatMap mapWithHiddenChanges() {
            CombatToken hiddenEnemy = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.ENEMY,
                    new GridPosition(3, 3), TokenController.AI_GAME_MASTER, null);
            SpatialFeature hiddenFeature = SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.TRAP,
                    List.of(new GridPosition(2, 2)), null, List.of(), SpatialFeatureProvenance.storyPlan("story:secret", 1, 0));
            map = new CombatMap(map.id(), map.adventureId(), map.ruleSetId(), map.grid(), player,
                    List.of(map.tokens().getFirst(), hiddenEnemy), map.obstacles(), map.layers(), map.version(), null, null,
                    List.of(hiddenFeature));
            return map;
        }

        private CombatMap baseMap() {
            return new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                    new GridSpec(5, 5, 50, 5), player,
                    List.of(new CombatToken(tokenId, TokenType.PLAYER,
                            new GridPosition(1, 1), TokenController.PLAYER, player)),
                    List.of(new GridPosition(2, 2)),
                    List.of(new MapLayer("MAP_BOUNDARIES", "2,2,VERTICAL,WALL,false", LayerVisibility.PLAYER_VISIBLE)),
                    0, null);
        }

        @Override public Optional<CombatMap> findById(MapId id) { return id.equals(map.id()) ? Optional.of(map) : Optional.empty(); }
        @Override public Optional<CombatMap> findByCommandId(UUID commandId) { return Optional.empty(); }
        @Override public void save(CombatMap map) { saves++; }
        @Override public void save(CombatMap map, long version, UUID key, String fingerprint) { saves++; }
    }
}
