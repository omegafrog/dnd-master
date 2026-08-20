package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanCandidateValidationException;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanValidator;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
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
    void retryPortRequestRetainsFailedCandidateAttemptAndDiagnostics() {
        var failed = ResolutionCandidate.diceRoll(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1", "bad", "roll");
        var excerpt = new ResolutionExtractionPort.SourceExcerpt(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1", "roll");
        final ResolutionExtractionPort.ResolutionExtractionRequest[] captured = new ResolutionExtractionPort.ResolutionExtractionRequest[1];
        ResolutionExtractionPort port = request -> { captured[0] = request; return List.of(failed); };

        port.retryCandidate(new ResolutionExtractionPort.CandidateRetryRequest(
                "op", failed, List.of(excerpt), "schema", "retry", 3, List.of("dice expression is invalid")));

        assertEquals(failed, captured[0].failedCandidate());
        assertEquals(3, captured[0].attempt());
        assertEquals(List.of("dice expression is invalid"), captured[0].diagnostics());
    }

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
    void eachStageTargetedCandidateHasAnIndependentThreeAttemptBudget() {
        var fixture = new Fixture(true);
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.withCitation(
                1, TacticalSceneFixtures.readyScene(TacticalScenePlanValidator.key(fixture.sourceCitation), fixture.playerId),
                List.of(fixture.sourceCitation)));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(2));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(2));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.withCitation(
                2, TacticalSceneFixtures.readyScene(TacticalScenePlanValidator.key(fixture.sourceCitation), fixture.playerId),
                List.of(fixture.sourceCitation)));

        AdventureStoryPlan plan = fixture.service.generate(
                fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.READY, plan.status());
        assertEquals(List.of(1, 1, 1, 2, 2, 2), fixture.generator.requests.stream()
                .map(request -> request.stage().position()).toList());
    }

    @Test
    void acceptsAValidTypedCandidateAndPersistsItWithTheMappedStage() {
        var fixture = new Fixture();
        fixture.generator.candidates.add(TacticalScenePlanCandidate.withCitation(
                1, TacticalSceneFixtures.readyScene(TacticalScenePlanValidator.key(fixture.sourceCitation), fixture.playerId),
                List.of(fixture.sourceCitation)));

        AdventureStoryPlan plan = fixture.service.generate(fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.READY, plan.status());
        assertEquals(TacticalScenePlan.CURRENT_SCHEMA_VERSION, plan.stages().getFirst().tacticalScenePlan().schemaVersion());
        assertEquals(1, fixture.generator.requests.size());
    }

    @Test
    void rejectsAnUnknownSourceCitationBeforeItCanOverrideSuppliedEvidence() {
        var fixture = new Fixture();
        fixture.generator.candidates.add(TacticalScenePlanCandidate.withCitation(1, TacticalSceneFixtures.sourceGroundedScene("unknown:page:9", fixture.playerId),
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
        fixture.generator.candidates.add(TacticalScenePlanCandidate.ready(
                1, TacticalSceneFixtures.unsupportedCoreFactScene(fixture.playerId), List.of()));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));
        fixture.generator.candidates.add(TacticalScenePlanCandidate.absent(1));

        AdventureStoryPlan plan = fixture.service.generate(fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.BLOCKED, plan.status());
        assertEquals("tactical boss requires source citation", fixture.generator.requests.get(1).violations().getFirst());
    }

    @Test
    void rejectsUnsupportedCoreStoryStageFactsBeforeGeneratingATacticalCandidate() {
        var fixture = new Fixture();
        fixture.generator.stage = unsupportedCoreStage(fixture.mapId, fixture.sourceCitation);

        AdventureStoryPlan plan = fixture.service.generate(
                fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.BLOCKED, plan.status());
        assertTrue(plan.failureReason().contains("story stage boss is not supported by source evidence"));
        assertTrue(plan.failureReason().contains("story stage reward is not supported by source evidence"));
        assertTrue(plan.failureReason().contains("story stage transition is not supported by source evidence"));
        assertEquals(3, fixture.generator.outlineRequests.size());
        assertEquals(List.of(), fixture.generator.outlineRequests.getFirst().violations());
        assertTrue(fixture.generator.outlineRequests.get(1).violations()
                .contains("story stage boss is not supported by source evidence"));
        assertEquals(0, fixture.generator.requests.size());
    }

    @Test
    void retriesTypedOutlineCandidateValidationFailuresAndPersistsBlockedDiagnostics() {
        var fixture = new Fixture();
        fixture.generator.outlineValidationFailuresRemaining = 3;

        AdventureStoryPlan plan = fixture.service.generate(
                fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);

        assertEquals(AdventureStoryPlanStatus.BLOCKED, plan.status());
        assertEquals(3, fixture.generator.outlineRequests.size());
        assertTrue(plan.failureReason().contains("AI returned an unknown source citation"));
        assertEquals(List.of("AI returned an unknown source citation"),
                fixture.generator.outlineRequests.get(1).violations());
    }

    @Test
    void retriesTypedCandidateFailuresExactlyThreeTimesThenBlocksTheStartGate() {
        var fixture = new Fixture();
        fixture.generator.candidateValidationFailuresRemaining = 3;
        AdventureStoryPlan plan = fixture.service.generate(fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE);
        assertEquals(AdventureStoryPlanStatus.BLOCKED, plan.status());
        assertEquals(3, fixture.generator.requests.size());
        assertEquals("malformed tactical candidate", plan.failureReason());
        assertFalse(fixture.service.isReadyFor(fixture.session));
    }

    @Test
    void doesNotPersistProviderAvailabilityFailureAsInvalidCandidateContent() {
        var fixture = new Fixture();
        fixture.generator.providerFailuresRemaining = 1;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> fixture.service.generate(
                        fixture.session.id(), fixture.session.ownerPlayerId(), SHORT_ADVENTURE));

        assertEquals("provider unavailable", failure.getMessage());
        assertEquals(1, fixture.generator.requests.size());
        assertEquals(null, fixture.plans.value);
    }

    @Test
    void rejectsReadyEnemyOnlyTacticalScenes() {
        var grounding = PlacementGrounding.aiInference("bounded");
        assertThrows(IllegalArgumentException.class, () -> new TacticalScenePlan(1, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                List.of(), List.of(), List.of(), List.of(new TacticalPlacement("enemy", TacticalPlacementKind.ENEMY, new NormalizedCoordinate(.5, .5), grounding)),
                List.of(), List.of(), List.of(), new FogPlan(List.of(), grounding), List.of(), List.of(), List.of()));
    }

    private static final class Fixture {
        private final AdventureSession session;
        private final Generator generator = new Generator();
        private final Plans plans = new Plans();
        private final AdventureStoryPlanApplicationService service;
        private final AdventureStoryPlanGenerationPort.SourceCitation sourceCitation;
        private final UUID mapId;
        private final String playerId;

        private Fixture() {
            this(false);
        }

        private Fixture(boolean twoTacticalStages) {
            mapId = UUID.randomUUID();
            UUID secondMapId = UUID.randomUUID();
            var sourceDocument = new KnowledgeDocumentId(UUID.randomUUID());
            String sourceQuote = "The cellar includes entry, alarm, reinforcement, boss, reward, success, failure, exit, and surrender. The party leaves.";
            sourceCitation = new AdventureStoryPlanGenerationPort.SourceCitation(
                    "STORYBOOK", sourceDocument.value(), 1, "page:1", sourceQuote, 1.0);
            var sourceReference = new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(
                    sourceDocument, 1, "page:1");
            var document = new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(
                    sourceDocument,
                    com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAIN_SCENARIO,
                    com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                    "cellar.txt", "STORYBOOK", 1);
            var unit = new com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit(
                    com.dndmaster.adventure.domain.scenario.ResolutionKind.SKILL_ABILITY_CHECK,
                    "Perception", 10, null,
                    com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE,
                    sourceQuote, List.of(sourceReference), "test", null,
                    ResolutionStatus.COMPLETE, List.of());
            var map = new MapDefinition(mapId, "brewery", "page 1", new MapDefinition.MapGrid(0, 0, 1, 0, "5ft"), List.of(), List.of(), List.of(),
                    new MapSourceReference(sourceDocument, 1, "page:1"), .9, MapSafetyStatus.SAFE);
            var secondMap = new MapDefinition(secondMapId, "brewery-upper", "page 2", new MapDefinition.MapGrid(0, 0, 1, 0, "5ft"), List.of(), List.of(), List.of(),
                    new MapSourceReference(sourceDocument, 1, "page:1"), .9, MapSafetyStatus.SAFE);
            var scenarioPackage = ScenarioPackage.publishWithMaps(ScenarioBundleId.generate(), 1, "tactical", List.of(document), List.of(unit),
                    new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()), CharacterLimit.defaultLimit(), null,
                    twoTacticalStages ? List.of(map, secondMap) : List.of(map), List.of());
            session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), scenarioPackage.packageId(), 1, 1,
                    new AdventureSessionRuntimeConfiguration(new ScenarioId(scenarioPackage.packageId()), new RuleSetId(UUID.randomUUID()), List.of(), "ollama", List.of(), "opening"));
            var characterSheetId = new com.dndmaster.adventure.domain.adventure.CharacterSheetId(UUID.randomUUID());
            playerId = characterSheetId.value().toString();
            session.addPartyMember(new AdventurePartyMember(characterSheetId, ControlMode.DIRECT,
                    false, false, false, false, false, false));
            generator.stage = mappedStage(1, mapId, sourceCitation);
            if (twoTacticalStages) generator.additionalTacticalStage = mappedStage(2, secondMapId, sourceCitation);
            service = new AdventureStoryPlanApplicationService(plans, new Sessions(session),
                    new PackageRepository(scenarioPackage), generator);
        }
    }

    private static AdventureStoryPlanStage mappedStage(
            int position, UUID mapId, AdventureStoryPlanGenerationPort.SourceCitation citation) {
        return new AdventureStoryPlanStage(position, "Cellar", "Clear the cellar", "Rats attack", "exit", List.of(), List.of("ending"), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureStageType.DUNGEON, "Cellar", mapId, "brewery", "page 1", List.of(), "", "exit", "", List.of("reward"),
                List.of("ending"), List.of(evidence(citation)), com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.GROUNDED, List.of(), "SAFE", .9);
    }

    private static AdventureStoryPlanStage unsupportedCoreStage(
            UUID mapId, AdventureStoryPlanGenerationPort.SourceCitation citation) {
        return new AdventureStoryPlanStage(1, "Cellar", "Clear the cellar", "Rats attack", "inherit the kingdom", List.of(), List.of("ending"), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureStageType.DUNGEON, "Cellar", mapId, "brewery", "page 1", List.of(), "ancient dragon", "inherit the kingdom", "", List.of("royal crown"),
                List.of("ending"), List.of(evidence(citation)), com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.GROUNDED, List.of(), "SAFE", .9);
    }

    private static com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence evidence(
            AdventureStoryPlanGenerationPort.SourceCitation citation) {
        return new com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence(
                citation.documentType(), citation.documentId(), citation.extractionVersion(),
                citation.locator(), citation.quote(), citation.confidence());
    }

    private static final class Generator implements AdventureStoryPlanGenerationPort {
        private AdventureStoryPlanStage stage;
        private AdventureStoryPlanStage additionalTacticalStage;
        private final List<TacticalScenePlanCandidate> candidates = new ArrayList<>();
        private final List<TacticalSceneRequest> requests = new ArrayList<>();
        private final List<Request> outlineRequests = new ArrayList<>();
        private int outlineValidationFailuresRemaining;
        private int candidateValidationFailuresRemaining;
        private int providerFailuresRemaining;
        public List<AdventureStoryPlanStage> generate(Request request) {
            outlineRequests.add(request);
            if (outlineValidationFailuresRemaining-- > 0) {
                throw new AdventureStoryPlanCandidateValidationException(
                        List.of("AI returned an unknown source citation"));
            }
            if (additionalTacticalStage != null) {
                return List.of(stage, additionalTacticalStage,
                        new AdventureStoryPlanStage(3, "Finish", "Finish", "Choice", "Finish", List.of(), List.of("ending")));
            }
            return List.of(stage,
                    new AdventureStoryPlanStage(2, "Return", "Return", "Delay", "Finish", List.of(), List.of("ending")),
                    new AdventureStoryPlanStage(3, "Finish", "Finish", "Choice", "Finish", List.of(), List.of("ending")));
        }
        public TacticalScenePlanCandidate generateTacticalScene(TacticalSceneRequest request) {
            requests.add(request);
            if (candidateValidationFailuresRemaining-- > 0) {
                throw new AdventureStoryPlanCandidateValidationException(
                        List.of("malformed tactical candidate"));
            }
            if (providerFailuresRemaining-- > 0) throw new IllegalStateException("provider unavailable");
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
        private static TacticalScenePlan readyScene(String citation, String playerId) {
            var placement = PlacementGrounding.aiInference("Map entrance placement is a bounded completion");
            var source = PlacementGrounding.sourceCitation(citation);
            return new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                    new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                    List.of(new TacticalPlacement(playerId, TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), placement)),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), placement),
                    requiredTriggers(source, playerId),
                    List.of(new com.dndmaster.adventure.domain.adventure.TacticalOutcome("leave", "party leaves", source)), List.of());
        }

        private static List<com.dndmaster.adventure.domain.adventure.TacticalTrigger> requiredTriggers(
                PlacementGrounding grounding, String playerId) {
            return java.util.Arrays.stream(com.dndmaster.adventure.domain.adventure.TacticalTriggerType.values())
                    .filter(type -> type != com.dndmaster.adventure.domain.adventure.TacticalTriggerType.FOG_REVEAL)
                    .map(type -> new com.dndmaster.adventure.domain.adventure.TacticalTrigger(
                            type.name().toLowerCase(), type,
                            type == com.dndmaster.adventure.domain.adventure.TacticalTriggerType.COMBAT_ENTRY
                                    ? List.of(playerId) : List.of(),
                            "", grounding))
                    .toList();
        }

        private static TacticalScenePlan sourceGroundedScene(String citation, String playerId) {
            var grounding = PlacementGrounding.sourceCitation(citation);
            return new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                    new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                    List.of(new TacticalPlacement(playerId, TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), grounding), List.of(), List.of(), List.of());
        }

        private static TacticalScenePlan unsupportedCoreFactScene(String playerId) {
            var grounding = PlacementGrounding.aiInference("AI inferred an unnamed boss without source support");
            return new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                    new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                    List.of(new TacticalPlacement(playerId, TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                    List.of(), List.of(), List.of(), List.of(new TacticalPlacement("boss", TacticalPlacementKind.BOSS, new NormalizedCoordinate(.8, .8), grounding)),
                    List.of(), List.of(), new FogPlan(List.of(), grounding), List.of(), List.of(), List.of());
        }
    }
}
