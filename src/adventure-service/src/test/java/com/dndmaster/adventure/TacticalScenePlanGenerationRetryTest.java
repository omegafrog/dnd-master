package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlanStatus;
import com.dndmaster.adventure.domain.adventure.TacticalSceneBoundary;
import com.dndmaster.adventure.domain.adventure.NormalizedCoordinate;
import com.dndmaster.adventure.domain.adventure.TacticalPlacement;
import com.dndmaster.adventure.domain.adventure.TacticalPlacementKind;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.FogPlan;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalScenePlanGenerationRetryTest {
    private static final AdventurePlanConfiguration SHORT_ADVENTURE = new AdventurePlanConfiguration(1, AdventureLength.SHORT);

    @Test
    void retriesInvalidTacticalCandidatesExactlyThreeTimesThenPersistsBlockedPlan() {
        var fixture = new Fixture();
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));

        AdventureStoryPlan plan = fixture.service.generate(fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.BLOCKED, plan.status());
        assertEquals(3, fixture.generator.requests.size());
        assertEquals(List.of(), fixture.generator.requests.get(0).violations());
        assertFalse(fixture.service.isReadyFor(fixture.session));
    }

    @Test
    void acceptsAValidTypedCandidateAndPersistsItWithTheMappedStage() {
        var fixture = new Fixture();
        fixture.generator.candidates.add(TacticalScenePlanCandidate.ready(1, TacticalSceneFixtures.readyScene(), List.of()));

        AdventureStoryPlan plan = fixture.service.generate(fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.READY, plan.status());
        assertEquals(TacticalScenePlan.CURRENT_SCHEMA_VERSION, plan.stages().getFirst().tacticalScenePlan().schemaVersion());
        assertEquals(1, fixture.generator.requests.size());
    }

    @Test
    void rejectsAnUnknownSourceCitationBeforeItCanOverrideSuppliedEvidence() {
        var fixture = new Fixture();
        fixture.generator.candidates.add(TacticalScenePlanCandidate.withCitation(1, TacticalSceneFixtures.sourceGroundedScene("unknown:page:9"),
                List.of(new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", UUID.randomUUID(), 1, "page:9", "unknown", .9))));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));

        AdventureStoryPlan plan = fixture.service.generate(fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.BLOCKED, plan.status());
        assertEquals(3, fixture.generator.requests.size());
        assertEquals("unknown tactical source citation", fixture.generator.requests.get(1).violations().getFirst());
    }

    @Test
    void rejectsAiInferredBossesAndOutcomesAsUnsupportedCoreFacts() {
        var fixture = new Fixture();
        fixture.generator.candidates.add(TacticalScenePlanCandidate.ready(1, TacticalSceneFixtures.unsupportedCoreFactScene(), List.of()));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));

        AdventureStoryPlan plan = fixture.service.generate(fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.BLOCKED, plan.status());
        assertEquals("tactical boss requires source citation", fixture.generator.requests.get(1).violations().getFirst());
    }

    private static final class Fixture {
        private final AdventureSession session;
        private final Generator generator = new Generator();
        private final Plans plans = new Plans();
        private final AdventureStoryPlanApplicationService service;

        private Fixture() {
            UUID mapId = UUID.randomUUID();
            var map = new MapDefinition(mapId, "brewery", "page 1", new MapDefinition.MapGrid(0, 0, 1, 0, "5ft"), List.of(), List.of(), List.of(),
                    new MapSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1"), .9, MapSafetyStatus.SAFE);
            var scenarioPackage = ScenarioPackage.publishWithMaps(ScenarioBundleId.generate(), 1, "tactical", List.of(), List.of(),
                    new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()), CharacterLimit.defaultLimit(), null, List.of(map), List.of());
            session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), scenarioPackage.packageId(), 1, 1,
                    new AdventureSessionRuntimeConfiguration(new ScenarioId(scenarioPackage.packageId()), new RuleSetId(UUID.randomUUID()), List.of(), "ollama", List.of(), "opening"));
            session.addPartyMember(new AdventurePartyMember(new com.dndmaster.adventure.domain.adventure.CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT,
                    false, false, false, false, false, false));
            generator.stage = mappedStage(mapId);
            service = new AdventureStoryPlanApplicationService(plans, new Sessions(session),
                    new PackageRepository(scenarioPackage), generator);
        }
    }

    private static AdventureStoryPlanStage mappedStage(UUID mapId) {
        return new AdventureStoryPlanStage(1, "Cellar", "Clear the cellar", "Rats attack", "Leave", List.of(), List.of("ending"), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureStageType.DUNGEON, "Cellar", mapId, "brewery", "page 1", List.of(), "", "Leave", "", List.of(),
                List.of("ending"), List.of(), com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.AI_SUGGESTION, List.of(), "SAFE", .9);
    }

    private static final class Generator implements AdventureStoryPlanGenerationPort {
        private AdventureStoryPlanStage stage;
        private final List<TacticalScenePlanCandidate> candidates = new ArrayList<>();
        private final List<TacticalSceneRequest> requests = new ArrayList<>();
        public List<AdventureStoryPlanStage> generate(Request request) { return List.of(stage, new AdventureStoryPlanStage(2, "Return", "Return", "Delay", "Finish", List.of(), List.of("ending")), new AdventureStoryPlanStage(3, "Finish", "Finish", "Choice", "Finish", List.of(), List.of("ending"))); }
        public TacticalScenePlanCandidate generateTacticalScene(TacticalSceneRequest request) {
            requests.add(request);
            return candidates.removeFirst();
        }
    }

    private static final class Plans implements AdventureStoryPlanRepository {
        private AdventureStoryPlan value;
        public Optional<AdventureStoryPlan> findBySessionId(SessionId id) { return Optional.ofNullable(value); }
        public void save(AdventureStoryPlan plan) { value = plan; }
    }

    private record Sessions(AdventureSession value) implements AdventureSessionRepository {
        public Optional<AdventureSession> findById(SessionId id) { return id.equals(value.id()) ? Optional.of(value) : Optional.empty(); }
        public void save(AdventureSession ignored, long expectedVersion) { }
    }

    private record PackageRepository(ScenarioPackage value) implements ScenarioPackageRepository {
        public Optional<ScenarioPackage> findByInputFingerprint(String ignored) { return Optional.empty(); }
        public Optional<ScenarioPackage> findById(UUID id) { return id.equals(value.packageId()) ? Optional.of(value) : Optional.empty(); }
        public void save(ScenarioPackage ignored) { }
    }

    private static final class TacticalSceneFixtures {
        private static TacticalScenePlan readyScene() {
            var grounding = PlacementGrounding.aiInference("Map entrance placement is a bounded completion");
            return new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                    new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                    List.of(new TacticalPlacement("player", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), grounding), List.of(), List.of(), List.of());
        }

        private static TacticalScenePlan sourceGroundedScene(String citation) {
            var grounding = PlacementGrounding.sourceCitation(citation);
            return new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                    new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                    List.of(new TacticalPlacement("player", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), grounding), List.of(), List.of(), List.of());
        }

        private static TacticalScenePlan unsupportedCoreFactScene() {
            var grounding = PlacementGrounding.aiInference("AI inferred an unnamed boss without source support");
            return new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                    new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                    List.of(new TacticalPlacement("player", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                    List.of(), List.of(), List.of(), List.of(new TacticalPlacement("boss", TacticalPlacementKind.BOSS, new NormalizedCoordinate(.8, .8), grounding)),
                    List.of(), List.of(), new FogPlan(List.of(), grounding), List.of(), List.of(), List.of());
        }
    }
}
