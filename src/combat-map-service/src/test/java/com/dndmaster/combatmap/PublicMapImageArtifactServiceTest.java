package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PublicMapImageArtifactServiceTest {
    @Test void storesOneImageForAnAuthoritativeObservationAndNeverRegeneratesItForLaterAlignment() {
        Fixture fixture = new Fixture();
        PublicMapImageArtifact first = fixture.service.observe(fixture.map.id(), fixture.owner, 7).orElseThrow();
        fixture.alignments.value = new MapGridAlignment(fixture.map.id(), first.imageRevision(), 2, 0, 2, 1);

        PublicMapImageArtifact repeated = fixture.service.observe(fixture.map.id(), fixture.owner, 7).orElseThrow();

        assertEquals(first, repeated);
        assertEquals(1, fixture.store.saved.size());
        assertEquals(7, first.observationVersion());
        assertEquals(1, first.publicAreaRevision());
        assertTrue(first.reference().contains(fixture.owner.value().toString()));
        assertTrue(first.reference().contains(fixture.map.id().value().toString()));
        assertTrue(first.reference().contains(first.imageRevision()));
    }

    @Test void retainsLastSafeArtifactWhenGenerationFails() {
        Fixture fixture = new Fixture();
        PublicMapImageArtifact safe = fixture.service.observe(fixture.map.id(), fixture.owner, 7).orElseThrow();
        fixture.failRendering = true;

        PublicMapImageArtifact fallback = fixture.service.observe(fixture.map.id(), fixture.owner, 8).orElseThrow();

        assertEquals(safe, fallback);
        assertEquals(1, fixture.store.saved.size());
    }

    @Test void alignmentChangeCannotAddPixelsForCellsAlreadyObservedOnALaterMapVersion() {
        Fixture fixture = new Fixture();
        PublicMapImageArtifact first = fixture.service.observe(fixture.map.id(), fixture.owner, 7).orElseThrow();
        fixture.alignments.value = new MapGridAlignment(fixture.map.id(), first.imageRevision(), 1, 0, 2, 1);
        fixture.maps.version = 8;

        PublicMapImageArtifact later = fixture.service.observe(fixture.map.id(), fixture.owner, 8).orElseThrow();

        assertEquals(first, later);
        assertEquals(1, fixture.store.saved.size());
    }

    @Test void deniesImageWhenNoPersistedVisibilityExists() {
        Fixture fixture = new Fixture();
        fixture.map = fixture.mapWithVisibility(null);

        assertTrue(fixture.service.observe(fixture.map.id(), fixture.owner, 7).isEmpty());
        assertTrue(fixture.store.saved.isEmpty());
    }

    @Test void downloadsOnlyTheExactStoredArtifactBoundToItsOwnerMapAndReference() {
        Fixture fixture = new Fixture();
        PublicMapImageArtifact artifact = fixture.service.observe(fixture.map.id(), fixture.owner, 7).orElseThrow();

        assertArrayEquals(artifact.png(), fixture.service.download(fixture.map.id(), fixture.owner, artifact.reference()).orElseThrow().png());
        assertTrue(fixture.service.download(fixture.map.id(), fixture.owner, artifact.reference() + ":changed").isEmpty());
        assertTrue(fixture.service.download(new MapId(UUID.randomUUID()), fixture.owner, artifact.reference()).isEmpty());
        assertTrue(fixture.service.download(fixture.map.id(), new MapOwnerId(UUID.randomUUID()), artifact.reference()).isEmpty());
    }

    private static final class Fixture {
        final MapOwnerId owner = new MapOwnerId(UUID.randomUUID());
        CombatMap map = mapWithVisibility(new VisibilitySnapshot(Set.of(new GridPosition(0, 0)), Set.of(new GridPosition(0, 0)), Set.of(), List.of(), 0));
        final InMemoryMaps maps = new InMemoryMaps(this);
        final Alignments alignments = new Alignments();
        final Artifacts store = new Artifacts();
        boolean failRendering;
        final PublicMapImageArtifactService service = new PublicMapImageArtifactService(maps, alignments, store,
                (source, x, y, size, explored) -> { if (failRendering) throw new IllegalStateException("render failed"); return PlayerMapImageService.maskedPng(source, x, y, size, explored); });

        CombatMap mapWithVisibility(VisibilitySnapshot visibility) {
            CombatMap result = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                    new GridSpec(2, 1, 2, 5), new PlayerId(owner.value()), List.of(), Set.of(),
                    List.of(new MapLayer("MAP_IMAGE", MapImageTestFixture.dataUri(4, 2), LayerVisibility.PLAYER_VISIBLE)), 0, null);
            if (visibility != null) result.replaceVisibility(visibility);
            return result;
        }
        CombatMap mapWithImage(String image) {
            CombatMap result = new CombatMap(map.id(), map.adventureId(), map.ruleSetId(), map.grid(), map.ownerPlayerId(), map.tokens(), map.obstacles(),
                    List.of(new MapLayer("MAP_IMAGE", image, LayerVisibility.PLAYER_VISIBLE)), 0, null);
            if (map.visibilitySnapshot() != null) result.replaceVisibility(map.visibilitySnapshot());
            return result;
        }
    }
    private static final class InMemoryMaps implements CombatMapViewStore {
        private final Fixture fixture;
        long version = 7;
        InMemoryMaps(Fixture fixture) { this.fixture = fixture; }
        public void insert(MapOwnerId owner, CombatMap map) { }
        public Optional<VersionedOwnedCombatMap> find(MapId id) { return Optional.of(new VersionedOwnedCombatMap(fixture.map, fixture.owner, version)); }
        public Optional<VersionedOwnedCombatMap> findByAdventureId(AdventureId id, MapOwnerId owner) { return Optional.empty(); }
        public Optional<VersionedOwnedCombatMap> findByCommandId(UUID id) { return Optional.empty(); }
        public long update(MapOwnerId owner, CombatMap map, long expected) { return 0; }
        public long update(MapOwnerId owner, CombatMap map, long expected, long persisted, UUID key, String fingerprint) { return 0; }
    }
    private static final class Alignments implements MapGridAlignmentStore {
        MapGridAlignment value;
        public Optional<MapGridAlignment> find(MapId id) { return Optional.ofNullable(value); }
        public MapGridAlignment apply(MapOwnerId owner, MapId id, MapGridAlignmentRequest request) { throw new UnsupportedOperationException(); }
    }
    private static final class Artifacts implements PublicMapImageArtifactStore {
        final List<PublicMapImageArtifact> saved = new ArrayList<>();
        public Optional<PublicMapImageArtifact> findByObservation(MapOwnerId owner, MapId map, String image, long observation) { return saved.stream().filter(value -> value.owner().equals(owner) && value.mapId().equals(map) && value.imageRevision().equals(image) && value.observationVersion() == observation).findFirst(); }
        public Optional<PublicMapImageArtifact> findLatest(MapOwnerId owner, MapId map, String image) { return saved.stream().filter(value -> value.owner().equals(owner) && value.mapId().equals(map) && value.imageRevision().equals(image)).reduce((a, b) -> b); }
        public Optional<PublicMapImageArtifact> findByPublicAreaRevision(MapOwnerId owner, MapId map, String image, long revision) { return saved.stream().filter(value -> value.owner().equals(owner) && value.mapId().equals(map) && value.imageRevision().equals(image) && value.publicAreaRevision() == revision).findFirst(); }
        public PublicMapImageArtifact save(PublicMapImageArtifact artifact) { saved.add(artifact); return artifact; }
    }
}
