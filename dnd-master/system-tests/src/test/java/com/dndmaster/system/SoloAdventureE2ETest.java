package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.progress.ActionJudgment;
import com.dndmaster.adventure.application.progress.ActionJudgmentRequest;
import com.dndmaster.adventure.application.progress.AdventureProgressApplicationService;
import com.dndmaster.adventure.application.progress.AdventureReadiness;
import com.dndmaster.adventure.application.progress.AiGameMasterPort;
import com.dndmaster.adventure.application.progress.ProgressAdventureCommand;
import com.dndmaster.adventure.application.progress.SceneProgress;
import com.dndmaster.adventure.application.progress.SceneProgressRequest;
import com.dndmaster.adventure.application.saved.CreateAdventureCommand;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
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
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.IndexId;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.infrastructure.persistence.EmbeddedRulebookChunk;
import com.dndmaster.ruleknowledge.infrastructure.persistence.IndexMetadata;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PgvectorRuleSearchRepository;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoloAdventureE2ETest {
    private final UUID playerId = UUID.randomUUID();
    private final OwnerPlayerId adventureOwner = new OwnerPlayerId(playerId);
    private final ScenarioId scenarioId = new ScenarioId(UUID.randomUUID());
    private final RuleSetId ruleSetId = new RuleSetId(UUID.randomUUID());

    @BeforeEach
    void resetDatabase() {
        SystemE2EFixture.reset();
    }

    @Test
    void completesLoginUploadIndexPlayMapSaveResumeAndDeleteJourney() {
        var access = new PlayerAccessApplicationService(new OwnershipAccessPolicy());
        var authenticated = access.login(Optional.of(playerId.toString()));
        assertEquals(playerId.toString(), access.establishPlayer(authenticated).id().value());

        var files = new FakeFileStore();
        var embedding = new FakeEmbedding();
        byte[] scenario = "The sealed crypt and its guardian".getBytes(StandardCharsets.UTF_8);
        byte[] rulebook = "A Dexterity check resolves stealth.".getBytes(StandardCharsets.UTF_8);
        files.store("scenario.txt", scenario);
        files.store("rules.txt", rulebook);

        RulebookId rulebookId = RulebookId.generate();
        var vectorRepository = new PgvectorRuleSearchRepository(SystemE2EFixture.dataSource());
        storeRule(vectorRepository, playerId, rulebookId, new String(rulebook, StandardCharsets.UTF_8), embedding);
        var evidence = vectorRepository.search(
                new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(playerId),
                List.of(rulebookId), embedding.embed("stealth"), 5);
        assertEquals("page 12", evidence.getFirst().locator());

        var adventureRepository = new PostgresAdventureRepository(SystemE2EFixture.dataSource());
        var saved = new SavedAdventureApplicationService(adventureRepository);
        var adventure = saved.createAdventure(new CreateAdventureCommand(
                adventureOwner, scenarioId, ruleSetId, new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("At the sealed crypt", "guardian waits", null, null)));
        var ai = new FakeAiGameMaster();
        var progress = new AdventureProgressApplicationService(
                adventureRepository, ignored -> new AdventureReadiness(true, true, true), ai);
        var progressed = progress.progressAdventure(
                new ProgressAdventureCommand(adventure.id(), adventureOwner, "I move silently past the guardian"));
        assertEquals(3, progressed.conversation().size());
        assertEquals("The crypt door opens", progressed.context().currentScene());
        assertEquals(2, ai.calls);

        var mapStore = new PostgresCombatMapViewStore(SystemE2EFixture.dataSource());
        var mapGenerator = new FakeMapGenerator(playerId);
        var maps = new CombatMapViewService(mapStore, ignored -> mapGenerator.generate(), ignored -> mapGenerator.generate());
        var combatMap = maps.prepareGenerated(
                new MapOwnerId(playerId),
                new com.dndmaster.combatmap.domain.AdventureId(adventure.id().value()),
                new com.dndmaster.combatmap.domain.RuleSetId(ruleSetId.value()), "sealed crypt");
        var playerMap = maps.displayForPlayer(combatMap.id(), new MapOwnerId(playerId));
        assertTrue(playerMap.layers().stream().allMatch(layer -> layer.visibility() == LayerVisibility.PLAYER_VISIBLE));
        assertFalse(playerMap.layers().stream().anyMatch(layer -> layer.value().contains("secret")));

        assertEquals(adventure.id(), saved.reopenAdventure(adventure.id(), adventureOwner).id());
        assertEquals(1, saved.listSavedAdventures(adventureOwner).size());
        saved.deleteAdventure(adventure.id(), adventureOwner, progressed.version());
        assertTrue(saved.listSavedAdventures(adventureOwner).isEmpty());
        assertEquals(2, files.size());
        assertTrue(embedding.calls >= 2);
    }

    private static void storeRule(
            PgvectorRuleSearchRepository repository, UUID owner, RulebookId rulebookId,
            String content, FakeEmbedding embedding) {
        var chunk = new RulebookChunk(
                rulebookId, new ChunkId(UUID.randomUUID()), 0,
                new ExtractedContentRange(0, content.length()), content);
        repository.storeReadyIndex(
                new IndexMetadata(
                        IndexId.generate(), rulebookId,
                        new com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId(owner),
                        "fake-e2e", 3, "v1"),
                List.of(new EmbeddedRulebookChunk(chunk, "page 12", embedding.embed(content))));
    }

    private final class FakeAiGameMaster implements AiGameMasterPort {
        private int calls;

        @Override
        public SceneProgress advanceScene(SceneProgressRequest request) {
            calls++;
            return new SceneProgress(scenarioId, "The crypt door opens", "guardian remains unaware");
        }

        @Override
        public ActionJudgment adjudicate(ActionJudgmentRequest request) {
            calls++;
            return new ActionJudgment(ruleSetId, "Stealth succeeds using the indexed Dexterity rule");
        }
    }

    private static final class FakeFileStore {
        private final Map<String, byte[]> files = new HashMap<>();
        void store(String name, byte[] content) { files.put(name, content.clone()); }
        int size() { return files.size(); }
    }

    private static final class FakeEmbedding {
        private int calls;
        float[] embed(String text) {
            calls++;
            return text.toLowerCase().contains("stealth") ? new float[] {1, 0, 0} : new float[] {0.9f, 0.1f, 0};
        }
    }

    private record FakeMapGenerator(UUID playerId) {
        PreparedMapData generate() {
            return new PreparedMapData(
                    new GridSpec(10, 10, 50, 5),
                    List.of(
                            new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                                    new GridPosition(1, 1), TokenController.PLAYER, new PlayerId(playerId)),
                            new CombatToken(new TokenId(UUID.randomUUID()), TokenType.ENEMY,
                                    new GridPosition(4, 4), TokenController.AI_GAME_MASTER, null)),
                    Set.of(new GridPosition(3, 3)),
                    List.of(
                            new MapLayer("FLOOR", "stone floor", LayerVisibility.PLAYER_VISIBLE),
                            new MapLayer("SECRET", "secret guardian route", LayerVisibility.AI_ONLY)));
        }
    }
}
