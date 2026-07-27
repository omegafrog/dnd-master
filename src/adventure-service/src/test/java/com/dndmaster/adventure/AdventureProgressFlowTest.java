package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.progress.ActionJudgment;
import com.dndmaster.adventure.application.progress.ActionJudgmentRequest;
import com.dndmaster.adventure.application.progress.AdventureProgressApplicationService;
import com.dndmaster.adventure.application.progress.AdventureProgressNotReadyException;
import com.dndmaster.adventure.application.progress.AdventureReadiness;
import com.dndmaster.adventure.application.progress.AiGameMasterPort;
import com.dndmaster.adventure.application.progress.CrossContextTimeoutException;
import com.dndmaster.adventure.application.progress.ProgressAdventureCommand;
import com.dndmaster.adventure.application.progress.RuleSetAdjudicationViolationException;
import com.dndmaster.adventure.application.progress.SceneProgress;
import com.dndmaster.adventure.application.progress.SceneProgressRequest;
import com.dndmaster.adventure.application.progress.ScenarioProgressViolationException;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class AdventureProgressFlowTest {
    private static final OwnerPlayerId OWNER = new OwnerPlayerId(UUID.randomUUID());
    private static final ScenarioId SCENARIO = new ScenarioId(UUID.randomUUID());
    private static final RuleSetId RULE_SET = new RuleSetId(UUID.randomUUID());

    @Test
    void preserves_scene_action_judgment_and_context_together_after_all_checks() {
        var repository = new InMemoryRepository(adventure());
        var service = service(repository, new StubAi(
                new SceneProgress(SCENARIO, "The gate opens", "guard is alert"),
                new ActionJudgment(RULE_SET, "The stealth check succeeds")));

        var result = service.progressAdventure(new ProgressAdventureCommand(
                repository.current.id(), OWNER, "Sneak past the guard"));

        assertEquals(3, result.conversation().size());
        assertEquals("The gate opens", result.context().currentScene());
        assertEquals("Sneak past the guard", result.context().pendingAction());
        assertEquals("The stealth check succeeds", result.context().latestJudgment());
        assertEquals(1, result.version());
        assertEquals(1, repository.saveCount);
        assertEquals(result.context(), repository.current.currentContext());
        assertEquals(result.conversation(), repository.current.conversation());
    }

    @Test
    void rejects_progress_before_calling_ai_when_readiness_is_incomplete() {
        var repository = new InMemoryRepository(adventure());
        var ai = new StubAi(new SceneProgress(SCENARIO, "scene", null), new ActionJudgment(RULE_SET, "result"));
        var service = new AdventureProgressApplicationService(
                repository, ignored -> new AdventureReadiness(true, false, true), ai);

        assertThrows(AdventureProgressNotReadyException.class, () -> service.progressAdventure(
                new ProgressAdventureCommand(repository.current.id(), OWNER, "Search")));

        assertEquals(0, ai.sceneCalls);
        assertUnchanged(repository);
    }

    @Test
    void blocks_ai_progress_outside_selected_scenario_without_partial_preservation() {
        var repository = new InMemoryRepository(adventure());
        var ai = new StubAi(
                new SceneProgress(new ScenarioId(UUID.randomUUID()), "Invented dungeon", null),
                new ActionJudgment(RULE_SET, "result"));

        assertThrows(ScenarioProgressViolationException.class, () -> service(repository, ai).progressAdventure(
                new ProgressAdventureCommand(repository.current.id(), OWNER, "Search")));

        assertEquals(0, ai.judgmentCalls);
        assertUnchanged(repository);
    }

    @Test
    void blocks_adjudication_outside_selected_rule_set_without_partial_preservation() {
        var repository = new InMemoryRepository(adventure());
        var ai = new StubAi(
                new SceneProgress(SCENARIO, "The gate opens", null),
                new ActionJudgment(new RuleSetId(UUID.randomUUID()), "Use an unrelated rule"));

        assertThrows(RuleSetAdjudicationViolationException.class, () -> service(repository, ai).progressAdventure(
                new ProgressAdventureCommand(repository.current.id(), OWNER, "Search")));

        assertUnchanged(repository);
    }

    @Test
    void converts_cross_context_timeout_and_keeps_conversation_and_context_atomic() {
        var repository = new InMemoryRepository(adventure());
        AiGameMasterPort timeout = new AiGameMasterPort() {
            @Override
            public SceneProgress advanceScene(SceneProgressRequest request) throws TimeoutException {
                return new SceneProgress(SCENARIO, "The gate opens", null);
            }

            @Override
            public ActionJudgment adjudicate(ActionJudgmentRequest request) throws TimeoutException {
                throw new TimeoutException("AI GM deadline exceeded");
            }
        };

        assertThrows(CrossContextTimeoutException.class, () -> service(repository, timeout).progressAdventure(
                new ProgressAdventureCommand(repository.current.id(), OWNER, "Search")));

        assertUnchanged(repository);
    }

    private static AdventureProgressApplicationService service(InMemoryRepository repository, AiGameMasterPort ai) {
        return new AdventureProgressApplicationService(
                repository, ignored -> new AdventureReadiness(true, true, true), ai);
    }

    private static Adventure adventure() {
        return Adventure.create(
                AdventureId.generate(), new SessionId(UUID.randomUUID()), OWNER, SCENARIO, RULE_SET,
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("At the city gate", null, null, null));
    }

    private static void assertUnchanged(InMemoryRepository repository) {
        assertEquals(0, repository.saveCount);
        assertEquals(List.of(), repository.current.conversation());
        assertEquals("At the city gate", repository.current.currentContext().currentScene());
        assertEquals(0, repository.current.version());
    }

    private static final class StubAi implements AiGameMasterPort {
        private final SceneProgress scene;
        private final ActionJudgment judgment;
        private int sceneCalls;
        private int judgmentCalls;

        private StubAi(SceneProgress scene, ActionJudgment judgment) {
            this.scene = scene;
            this.judgment = judgment;
        }

        @Override
        public SceneProgress advanceScene(SceneProgressRequest request) {
            sceneCalls++;
            return scene;
        }

        @Override
        public ActionJudgment adjudicate(ActionJudgmentRequest request) {
            judgmentCalls++;
            return judgment;
        }
    }

    private static final class InMemoryRepository implements AdventureRepository {
        private Adventure current;
        private int saveCount;

        private InMemoryRepository(Adventure current) {
            this.current = current;
        }

        @Override
        public Optional<Adventure> findById(AdventureId adventureId) {
            return current.id().equals(adventureId) ? Optional.of(current) : Optional.empty();
        }

        @Override
        public List<Adventure> findSavedByOwner(OwnerPlayerId ownerPlayerId) {
            return List.of(current);
        }

        @Override
        public void save(Adventure adventure) {
            current = adventure;
            saveCount++;
        }
    }
}
