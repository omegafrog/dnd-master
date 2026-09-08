package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MapGridAlignmentServiceTest {
    private final MapOwnerId owner = new MapOwnerId(UUID.randomUUID());
    private final MapId mapId = new MapId(UUID.randomUUID());

    @Test
    void savesDecimalAlignmentWithoutChangingTheMapAndReplaysTheSameCommand() {
        CombatMap map = map();
        InMemoryMapStore maps = new InMemoryMapStore(map, owner);
        InMemoryAlignmentStore alignments = new InMemoryAlignmentStore();
        MapGridAlignmentService service = new MapGridAlignmentService(maps, alignments);
        UUID commandId = UUID.randomUUID();
        MapGridAlignmentRequest request = new MapGridAlignmentRequest(commandId, 0, service.find(mapId, owner).imageRevision(), 12.25, 8.5, 31.75);

        MapGridAlignment saved = service.apply(mapId, owner, request);
        MapGridAlignment replay = service.apply(mapId, owner, request);

        assertEquals(1, saved.version());
        assertEquals(saved, replay);
        assertEquals(saved, service.find(mapId, owner));
        assertSame(map, maps.current);
        assertEquals(0, map.version());
        assertEquals(new GridPosition(2, 3), map.tokens().getFirst().position());
        assertEquals(Set.of(new GridPosition(4, 4)), map.obstacles());
    }

    @Test
    void acceptsAnIdenticalRetryAfterACommittedRequestButRejectsChangedGeometry() {
        InMemoryMapStore maps = new InMemoryMapStore(map(), owner);
        MapGridAlignmentService service = new MapGridAlignmentService(maps, new InMemoryAlignmentStore());
        String imageRevision = service.find(mapId, owner).imageRevision();
        UUID commandId = UUID.randomUUID();
        service.apply(mapId, owner, new MapGridAlignmentRequest(commandId, 0, imageRevision, 1.5, 2.5, 30.5));

        MapGridAlignment retry = service.apply(mapId, owner,
                new MapGridAlignmentRequest(UUID.randomUUID(), 0, imageRevision, 1.5, 2.5, 30.5));
        assertEquals(1, retry.version());
        assertThrows(MapGridAlignmentConflictException.class,
                () -> service.apply(mapId, owner, new MapGridAlignmentRequest(commandId, 0, imageRevision, 2.5, 2.5, 30.5)));
        assertThrows(MapGridAlignmentConflictException.class,
                () -> service.apply(mapId, owner, new MapGridAlignmentRequest(UUID.randomUUID(), 1, "old-image", 1.5, 2.5, 30.5)));
    }

    @Test
    void rejectsNonFiniteOrNonPositiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new MapGridAlignmentRequest(UUID.randomUUID(), 0, "image", Double.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MapGridAlignmentRequest(UUID.randomUUID(), 0, "image", 0, 0, 0));
    }

    @Test
    void rejectsAnAlignmentOutsideTheStoredMapImage() {
        InMemoryMapStore maps = new InMemoryMapStore(map(), owner);
        MapGridAlignmentService service = new MapGridAlignmentService(maps, new InMemoryAlignmentStore());

        assertThrows(IllegalArgumentException.class, () -> service.apply(mapId, owner,
                new MapGridAlignmentRequest(UUID.randomUUID(), 0, service.find(mapId, owner).imageRevision(), 900, 0, 20)));
    }

    @Test
    void rejectsAlignmentWhenTheMapHasNoReadableImage() {
        CombatMap withoutImage = new CombatMap(mapId, new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(10, 10, 50, 5), new PlayerId(owner.value()), List.of(), Set.of(), List.of(), 0, null);
        MapGridAlignmentService service = new MapGridAlignmentService(new InMemoryMapStore(withoutImage, owner), new InMemoryAlignmentStore());

        assertThrows(MapGridAlignmentImageUnavailableException.class, () -> service.find(mapId, owner));
    }

    private CombatMap map() {
        return new CombatMap(mapId, new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), new GridSpec(10, 10, 50, 5),
                new PlayerId(owner.value()), List.of(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                new GridPosition(2, 3), TokenController.PLAYER, new PlayerId(owner.value()))), Set.of(new GridPosition(4, 4)),
                List.of(new MapLayer("MAP_IMAGE", MapImageTestFixture.dataUri(1_000, 1_000), LayerVisibility.PLAYER_VISIBLE)), 0, null);
    }

    private static final class InMemoryMapStore implements CombatMapViewStore {
        private CombatMap current; private final MapOwnerId owner;
        InMemoryMapStore(CombatMap current, MapOwnerId owner) { this.current = current; this.owner = owner; }
        public void insert(MapOwnerId ignored, CombatMap map) { current = map; }
        public Optional<VersionedOwnedCombatMap> find(MapId id) { return current.id().equals(id) ? Optional.of(new VersionedOwnedCombatMap(current, owner, current.version())) : Optional.empty(); }
        public Optional<VersionedOwnedCombatMap> findByAdventureId(AdventureId id, MapOwnerId ignored) { return Optional.empty(); }
        public Optional<VersionedOwnedCombatMap> findByCommandId(UUID id) { return Optional.empty(); }
        public long update(MapOwnerId ignored, CombatMap map, long expected) { current = map; return expected + 1; }
        public long update(MapOwnerId ignored, CombatMap map, long expected, long version, UUID key, String fingerprint) { current = map; return version; }
    }

    private static final class InMemoryAlignmentStore implements MapGridAlignmentStore {
        private final Map<UUID, MapGridAlignment> saved = new HashMap<>();
        private final Map<UUID, MapGridAlignmentCommand> commands = new HashMap<>();
        public Optional<MapGridAlignment> find(MapId id) { return Optional.ofNullable(saved.get(id.value())); }
        public MapGridAlignment apply(MapOwnerId owner, MapId id, MapGridAlignmentRequest request) {
            MapGridAlignmentCommand replay = commands.get(request.commandId());
            if (replay != null) {
                if (!replay.request().equals(request)) throw new MapGridAlignmentConflictException();
                return replay.result();
            }
            MapGridAlignment current = saved.get(id.value());
            long version = current == null ? 0 : current.version();
            if (version != request.expectedVersion()) throw new MapGridAlignmentConflictException();
            MapGridAlignment result = new MapGridAlignment(id, request.imageRevision(), request.originX(), request.originY(), request.cellSize(), version + 1);
            saved.put(id.value(), result); commands.put(request.commandId(), new MapGridAlignmentCommand(request, result)); return result;
        }
    }
}
