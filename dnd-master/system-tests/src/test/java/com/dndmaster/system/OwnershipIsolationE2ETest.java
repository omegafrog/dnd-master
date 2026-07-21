package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.saved.CreateAdventureCommand;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.domain.adventure.AdventureAccessDeniedException;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.combatmap.application.view.CombatMapAccessDeniedException;
import com.dndmaster.combatmap.application.view.CombatMapViewService;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.PreparedMapData;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapLayer;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import com.dndmaster.combatmap.infrastructure.persistence.PostgresCombatMapViewStore;
import com.dndmaster.identityaccess.application.PlayerAccessApplicationService;
import com.dndmaster.identityaccess.domain.access.OwnershipAccessPolicy;
import com.dndmaster.identityaccess.domain.access.OwnershipMismatchException;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.IndexId;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.infrastructure.persistence.EmbeddedRulebookChunk;
import com.dndmaster.ruleknowledge.infrastructure.persistence.IndexMetadata;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PgvectorRuleSearchRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OwnershipIsolationE2ETest {
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @BeforeEach
    void resetDatabase() {
        SystemE2EFixture.reset();
    }

    @Test
    void isolatesRelationalVectorAndMapResourcesForSecondPlayer() {
        var access = new PlayerAccessApplicationService(new OwnershipAccessPolicy());
        var firstSession = access.login(Optional.of(first.toString()));
        var loaded = new AtomicBoolean();
        assertThrows(OwnershipMismatchException.class, () -> access.provideOwnedResource(
                Optional.of(firstSession),
                com.dndmaster.identityaccess.domain.player.OwnerPlayerId.fromStoredValue(second.toString()),
                () -> { loaded.set(true); return "second-player-secret"; }));
        assertEquals(false, loaded.get());

        var saved = new SavedAdventureApplicationService(
                new PostgresAdventureRepository(SystemE2EFixture.dataSource()));
        var firstAdventure = saved.createAdventure(command(first, "first camp"));
        var secondAdventure = saved.createAdventure(command(second, "second camp"));
        assertEquals(List.of(firstAdventure.id()), saved.listSavedAdventures(new OwnerPlayerId(first)).stream()
                .map(adventure -> adventure.id()).toList());
        assertThrows(AdventureAccessDeniedException.class,
                () -> saved.reopenAdventure(secondAdventure.id(), new OwnerPlayerId(first)));

        RulebookId sharedRulebookId = RulebookId.generate();
        var vectors = new PgvectorRuleSearchRepository(SystemE2EFixture.dataSource());
        storeVector(vectors, first, sharedRulebookId, "first player's rule", "page 1");
        storeVector(vectors, second, sharedRulebookId, "second player's private rule", "page 99");
        var firstHits = vectors.search(
                new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(first),
                List.of(sharedRulebookId), new float[] {1, 0, 0}, 10);
        assertEquals(List.of("first player's rule"), firstHits.stream().map(hit -> hit.content()).toList());

        var mapService = new CombatMapViewService(
                new PostgresCombatMapViewStore(SystemE2EFixture.dataSource()),
                ignored -> mapData(second), ignored -> mapData(second));
        var secondMap = mapService.prepareGenerated(
                new MapOwnerId(second),
                new com.dndmaster.combatmap.domain.AdventureId(secondAdventure.id().value()),
                new com.dndmaster.combatmap.domain.RuleSetId(secondAdventure.ruleSetId().value()), "private map");
        assertThrows(CombatMapAccessDeniedException.class,
                () -> mapService.displayForPlayer(secondMap.id(), new MapOwnerId(first)));
        assertEquals(List.of("PUBLIC"), mapService.displayForPlayer(secondMap.id(), new MapOwnerId(second))
                .layers().stream().map(MapLayer::type).toList());
    }

    private static CreateAdventureCommand command(UUID owner, String scene) {
        return new CreateAdventureCommand(
                new OwnerPlayerId(owner), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext(scene, null, null, null));
    }

    private static void storeVector(
            PgvectorRuleSearchRepository repository, UUID owner, RulebookId rulebookId,
            String content, String locator) {
        var chunk = new RulebookChunk(
                rulebookId, new ChunkId(UUID.randomUUID()), 0,
                new ExtractedContentRange(0, content.length()), content, null, null);
        repository.storeReadyIndex(
                new IndexMetadata(
                        IndexId.generate(), rulebookId,
                        new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(owner),
                        "fake-e2e", 3, "v1"),
                List.of(new EmbeddedRulebookChunk(chunk, locator, new float[] {1, 0, 0})));
    }

    private static PreparedMapData mapData(UUID owner) {
        return new PreparedMapData(
                new GridSpec(8, 8, 50, 5),
                List.of(new CombatToken(
                        new TokenId(UUID.randomUUID()), TokenType.PLAYER, new GridPosition(1, 1),
                        TokenController.PLAYER, new PlayerId(owner))),
                Set.of(),
                List.of(
                        new MapLayer("PUBLIC", "visible floor", LayerVisibility.PLAYER_VISIBLE),
                        new MapLayer("PRIVATE", "hidden trap", LayerVisibility.AI_ONLY)));
    }
}
