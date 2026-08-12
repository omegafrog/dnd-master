package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetApplicationService;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.NarrationSafetyAssessment;
import com.dndmaster.adventure.application.runtime.NarrationSafetyPort;
import com.dndmaster.adventure.application.runtime.NarrationSafetyRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimePlanningPort;
import com.dndmaster.adventure.application.runtime.RuntimePlanningRequest;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResult;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.SubmitRuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeBindingRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.PlayabilityReport;
import com.dndmaster.adventure.domain.adventure.PlayabilityStatus;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeTurnApplicationServiceTest {
    @Test
    void meta_question_returns_read_only_result_without_advancing_or_persisting() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        ScenarioPackage scenarioPackage = scenarioPackage(new KnowledgeDocumentId(UUID.randomUUID()), new KnowledgeDocumentId(UUID.randomUUID()));
        InMemoryRuntimeTurnRepository turns = new InMemoryRuntimeTurnRepository();
        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                new InMemoryAdventureRepository(adventure),
                new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId())),
                new InMemoryPackageRepository(scenarioPackage), turns,
                request -> { throw new AssertionError("meta question must not call provider"); },
                request -> { throw new AssertionError("meta question must not call safety port"); },
                request -> { throw new AssertionError("meta question must not call planner"); },
                new InMemorySessionKnowledgeSetRepository());

        RuntimeTurnResult result = service.submitTurn(new SubmitRuntimeTurnCommand(
                adventure.id(), owner, UUID.randomUUID(), UUID.randomUUID(), "What rules are active?", 0, false));

        assertEquals(0, result.version());
        assertEquals(0, turns.saved.size());
        assertEquals(adventure.currentContext(), result.context());
    }

    @Test
    void gm_only_turn_advances_scene_without_persisting_a_fake_player_message() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        ScenarioPackage scenarioPackage = scenarioPackage(new KnowledgeDocumentId(UUID.randomUUID()), new KnowledgeDocumentId(UUID.randomUUID()));
        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                new InMemoryAdventureRepository(adventure),
                new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId())),
                new InMemoryPackageRepository(scenarioPackage), new InMemoryRuntimeTurnRepository(),
                request -> request.evidenceType() == RuntimeEvidenceType.STORYBOOK
                        ? List.of(new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                        new KnowledgeDocumentId(request.knowledgeDocumentIds().get(0)), 1, "page:1", "The next scene begins."))
                        : List.of(),
                request -> new RuntimePlan("next scene", null, "await player choice", "The GM advances the scene.", null, List.of(), List.of()),
                new AllowingSafetyPort(true), new InMemorySessionKnowledgeSetRepository());

        RuntimeTurnResult result = service.submitTurn(new SubmitRuntimeTurnCommand(
                adventure.id(), owner, UUID.randomUUID(), UUID.randomUUID(), "Continue the current beat", 0,
                null, -1, true, true));

        assertEquals(2, result.conversation().size());
        assertTrue(result.conversation().stream().noneMatch(entry -> "PLAYER".equals(entry.speaker())));
        assertEquals(1, result.version());
    }

    @Test
    void persisted_document_selection_survives_runtime_restart_and_limits_retrieval() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId selected = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId excluded = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(selected, excluded);
        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemorySessionKnowledgeSetRepository scopes = new InMemorySessionKnowledgeSetRepository();
        new SessionKnowledgeSetApplicationService(
                adventures,
                scopes,
                ignored -> List.of(
                        new KnowledgeDocumentLookupPort.KnowledgeDocumentRecord(
                                selected, KnowledgeDocumentStatus.INDEXED, "selected.pdf", "RULEBOOK", 1),
                        new KnowledgeDocumentLookupPort.KnowledgeDocumentRecord(
                                excluded, KnowledgeDocumentStatus.INDEXED, "excluded.pdf", "RULEBOOK", 1)))
                .updateSessionKnowledgeSet(adventure.id(), owner, List.of(selected));
        RecordingEvidenceSearchPort search = new RecordingEvidenceSearchPort(selected, excluded);

        RuntimeTurnApplicationService restartedRuntime = new RuntimeTurnApplicationService(
                adventures,
                new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId(), excluded)),
                new InMemoryPackageRepository(scenarioPackage), new InMemoryRuntimeTurnRepository(), search,
                request -> new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of()),
                new AllowingSafetyPort(true), scopes);

        restartedRuntime.submitTurn(new SubmitRuntimeTurnCommand(
                adventure.id(), owner, UUID.randomUUID(), UUID.randomUUID(), "Open the door"));

        assertEquals(List.of(selected.value()), search.requests.getFirst().knowledgeDocumentIds());
        assertEquals(List.of(selected.value()), search.requests.getLast().knowledgeDocumentIds());
    }

    @Test
    void uses_persisted_session_scope_instead_of_binding_rulebooks() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId selected = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId bindingOnly = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(selected, bindingOnly);
        InMemorySessionKnowledgeSetRepository scopes = new InMemorySessionKnowledgeSetRepository();
        scopes.save(new SessionKnowledgeSet(adventure.sessionId(), List.of(selected)));
        RecordingEvidenceSearchPort search = new RecordingEvidenceSearchPort(selected, bindingOnly);

        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                new InMemoryAdventureRepository(adventure),
                new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId(), bindingOnly)),
                new InMemoryPackageRepository(scenarioPackage), new InMemoryRuntimeTurnRepository(), search,
                request -> new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of()),
                new AllowingSafetyPort(true), scopes);

        RuntimeTurnResult result = service.submitTurn(new SubmitRuntimeTurnCommand(
                adventure.id(), owner, UUID.randomUUID(), UUID.randomUUID(), "Open the door"));

        assertEquals(adventure.sessionId(), search.requests.getFirst().sessionId());
        assertEquals(List.of(selected.value()), search.requests.getFirst().knowledgeDocumentIds());
        assertEquals(List.of(selected.value()), search.requests.getLast().knowledgeDocumentIds());
        assertTrue(result.turn().evidencePack().storybook().stream()
                .noneMatch(evidence -> evidence.knowledgeDocumentId().equals(bindingOnly)));
        assertTrue(result.turn().evidencePack().rulebook().stream()
                .noneMatch(evidence -> evidence.knowledgeDocumentId().equals(bindingOnly)));
    }

    @Test
    void uses_scenario_package_documents_when_session_scope_is_empty() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        ScenarioPackage scenarioPackage = scenarioPackage(
                new KnowledgeDocumentId(UUID.randomUUID()), new KnowledgeDocumentId(UUID.randomUUID()));
        KnowledgeDocumentId story = scenarioPackage.documents().getFirst().knowledgeDocumentId();
        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                new InMemoryAdventureRepository(adventure),
                new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId())),
                new InMemoryPackageRepository(scenarioPackage), new InMemoryRuntimeTurnRepository(),
                request -> List.of(new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, story, 1, "page:1", "Story")), request -> new RuntimePlan(
                        "scene", null, "judgment", "narration", null, List.of(), List.of()),
                new AllowingSafetyPort(true), new InMemorySessionKnowledgeSetRepository());

        RuntimeTurnResult result = service.submitTurn(new SubmitRuntimeTurnCommand(
                adventure.id(), owner, UUID.randomUUID(), UUID.randomUUID(), "Open the door"));

        assertEquals(List.of(story.value()), result.turn().evidencePack().storybook().stream()
                .map(evidence -> evidence.knowledgeDocumentId().value()).toList());

    }
    @Test
    void rejects_stale_expected_version_before_planning_or_persistence() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(storyId, rulebookId);
        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId()));
        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                adventures, bindings, new InMemoryPackageRepository(scenarioPackage), new InMemoryRuntimeTurnRepository(),
                new RecordingEvidenceSearchPort(storyId, rulebookId), request -> { throw new AssertionError("must not plan"); },
                new AllowingSafetyPort(true), scope(adventure, storyId, rulebookId));

        assertThrows(IllegalStateException.class, () -> service.submitTurn(new SubmitRuntimeTurnCommand(
                adventure.id(), owner, UUID.randomUUID(), UUID.randomUUID(), "Open the door", 99)));
        assertEquals(0, adventures.current.version());
    }

    @Test
    void prefetches_storybook_and_rulebook_evidence_separately_and_saves_proposed_context() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(storyId, rulebookId);
        ActiveSourceContext proposed = new ActiveSourceContext(storyId, 1, "page:1:span:1", "Story excerpt");

        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId()));
        InMemoryPackageRepository packages = new InMemoryPackageRepository(scenarioPackage);
        InMemoryRuntimeTurnRepository turns = new InMemoryRuntimeTurnRepository();
        RecordingEvidenceSearchPort search = new RecordingEvidenceSearchPort(storyId, rulebookId);
        RecordingPlanningPort planning = new RecordingPlanningPort(proposed);
        AllowingSafetyPort safety = new AllowingSafetyPort(true);

        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                adventures, bindings, packages, turns, search, planning, safety, scope(adventure, storyId, rulebookId));
        RuntimeTurnResult result = service.submitTurn(new SubmitRuntimeTurnCommand(
                adventure.id(), owner, UUID.randomUUID(), UUID.randomUUID(), "Open the door"));

        assertEquals(List.of(RuntimeEvidenceType.STORYBOOK, RuntimeEvidenceType.RULEBOOK), search.requestTypes);
        assertEquals(1, result.turn().evidencePack().storybook().size());
        assertEquals(1, result.turn().evidencePack().rulebook().size());
        assertEquals(1, result.turn().evidencePack().resolution().size());
        assertEquals("근거를 바탕으로 응답한다.", result.turn().plan().narration());
        assertEquals(proposed, result.turn().activeSourceContext());
        assertEquals("page:1:span:1", bindings.current.activeSourceContext().locator());
        assertTrue(result.turn().committed());
        assertEquals(2, turns.saved.size());
        assertEquals(result.turn(), turns.saved.getLast());
        assertEquals(3, result.conversation().size());
        assertEquals(1, result.version());
    }

    @Test
    void fails_closed_when_narration_safety_rejects_output() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(storyId, rulebookId);

        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId()));
        InMemoryPackageRepository packages = new InMemoryPackageRepository(scenarioPackage);
        InMemoryRuntimeTurnRepository turns = new InMemoryRuntimeTurnRepository();
        RecordingEvidenceSearchPort search = new RecordingEvidenceSearchPort(storyId, rulebookId);
        RecordingPlanningPort planning = new RecordingPlanningPort(null);
        AllowingSafetyPort safety = new AllowingSafetyPort(false);

        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                adventures, bindings, packages, turns, search, planning, safety, scope(adventure, storyId, rulebookId));

        assertThrows(IllegalStateException.class, () -> service.submitTurn(
                new SubmitRuntimeTurnCommand(adventure.id(), owner, UUID.randomUUID(), UUID.randomUUID(), "Open the door")));
        assertEquals(0, adventures.current.version());
        assertEquals(0, adventures.current.conversation().size());
        assertEquals(null, bindings.current.activeSourceContext());
        assertEquals(0, turns.saved.size());
    }

    @Test
    void retries_same_runtime_turn_command_from_saved_result_without_replanning() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(storyId, rulebookId);
        ActiveSourceContext proposed = new ActiveSourceContext(storyId, 1, "page:1:span:1", "Story excerpt");
        UUID turnId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId()));
        InMemoryPackageRepository packages = new InMemoryPackageRepository(scenarioPackage);
        InMemoryRuntimeTurnRepository turns = new InMemoryRuntimeTurnRepository();
        RecordingEvidenceSearchPort search = new RecordingEvidenceSearchPort(storyId, rulebookId);
        RecordingPlanningPort planning = new RecordingPlanningPort(proposed);
        AllowingSafetyPort safety = new AllowingSafetyPort(true);

        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                adventures, bindings, packages, turns, search, planning, safety, scope(adventure, storyId, rulebookId));
        SubmitRuntimeTurnCommand command = new SubmitRuntimeTurnCommand(adventure.id(), owner, turnId, commandId, "Open the door", 0);

        RuntimeTurnResult first = service.submitTurn(command);
        RuntimeTurnResult second = service.submitTurn(command);

        assertEquals(first, second);
        assertEquals(2, search.calls);
        assertEquals(1, planning.calls);
        assertEquals(1, safety.calls);
        assertEquals(2, turns.saved.size());
    }

    @Test
    void resumes_a_partially_persisted_turn_without_replanning_or_double_advancing() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId storyId = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId rulebookId = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioPackage scenarioPackage = scenarioPackage(storyId, rulebookId);
        ActiveSourceContext proposed = new ActiveSourceContext(storyId, 1, "page:1:span:1", "Story excerpt");
        UUID turnId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemoryBindingRepository bindings = new InMemoryBindingRepository(binding(adventure.id(), owner, scenarioPackage.packageId()));
        InMemoryPackageRepository packages = new InMemoryPackageRepository(scenarioPackage);
        FlakyRuntimeTurnRepository turns = new FlakyRuntimeTurnRepository();
        RecordingEvidenceSearchPort search = new RecordingEvidenceSearchPort(storyId, rulebookId);
        RecordingPlanningPort planning = new RecordingPlanningPort(proposed);
        AllowingSafetyPort safety = new AllowingSafetyPort(true);

        RuntimeTurnApplicationService service = new RuntimeTurnApplicationService(
                adventures, bindings, packages, turns, search, planning, safety, scope(adventure, storyId, rulebookId));
        SubmitRuntimeTurnCommand command = new SubmitRuntimeTurnCommand(adventure.id(), owner, turnId, commandId, "Open the door");

        assertThrows(IllegalStateException.class, () -> service.submitTurn(command));
        RuntimeTurnResult resumed = service.submitTurn(command);

        assertEquals("근거를 바탕으로 응답한다.", resumed.turn().plan().narration());
        assertEquals(1, adventures.current.version());
        assertEquals(2, search.calls);
        assertEquals(1, planning.calls);
        assertEquals(1, safety.calls);
        assertEquals(2, turns.saved.size());
        assertTrue(turns.saved.stream().anyMatch(RuntimeTurn::committed));
    }

    private static RuntimeBinding binding(AdventureId adventureId, OwnerPlayerId owner, UUID packageId) {
        return binding(adventureId, owner, packageId, new KnowledgeDocumentId(UUID.randomUUID()));
    }

    private static RuntimeBinding binding(AdventureId adventureId, OwnerPlayerId owner, UUID packageId, KnowledgeDocumentId rulebookId) {
        return RuntimeBinding.create(
                adventureId,
                owner,
                packageId,
                1,
                List.of(rulebookId.value()),
                List.of(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true)),
                "engine-1",
                List.of("search"),
                new PlayabilityReport(PlayabilityStatus.PLAYABLE, List.of(), List.of(), List.of(), List.of()),
                null);
    }

    private static Adventure adventure(OwnerPlayerId owner) {
        return Adventure.create(
                AdventureId.generate(), new SessionId(UUID.randomUUID()), owner,
                new ScenarioId(UUID.randomUUID()), new com.dndmaster.adventure.domain.adventure.RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("start", null, null, null));
    }

    private static InMemorySessionKnowledgeSetRepository scope(
            Adventure adventure, KnowledgeDocumentId storyId, KnowledgeDocumentId rulebookId) {
        InMemorySessionKnowledgeSetRepository repository = new InMemorySessionKnowledgeSetRepository();
        repository.save(new SessionKnowledgeSet(adventure.sessionId(), List.of(storyId, rulebookId)));
        return repository;
    }

    private static ScenarioPackage scenarioPackage(KnowledgeDocumentId storyId, KnowledgeDocumentId rulebookId) {
        ScenarioBundleId bundleId = ScenarioBundleId.generate();
        List<ScenarioResolutionUnit> units = List.of(new ScenarioResolutionUnit(
                ResolutionKind.DICE_ROLL, null, null, "1d6", ResolutionVisibility.GM_REFERENCE,
                "Roll 1d6.", List.of(new ScenarioSourceReference(storyId, 1, "page:1:span:1")),
                "fixture", ScenarioResolutionDetail.empty(), ResolutionStatus.COMPLETE, List.of()));
        return ScenarioPackage.publish(
                bundleId, 1, "fingerprint",
                List.of(new ScenarioBundleDocumentSelection(
                        storyId, ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.INDEXED,
                        "story.txt", "STORYBOOK", 1)),
                units,
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()));
    }

    private static final class InMemoryAdventureRepository implements AdventureRepository {
        private Adventure current;

        private InMemoryAdventureRepository(Adventure current) {
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
        }
    }

    private static final class InMemoryBindingRepository implements RuntimeBindingRepository {
        private RuntimeBinding current;

        private InMemoryBindingRepository(RuntimeBinding current) {
            this.current = current;
        }

        @Override
        public Optional<RuntimeBinding> findCurrentByAdventureId(AdventureId adventureId) {
            return current != null && current.adventureId().equals(adventureId) ? Optional.of(current) : Optional.empty();
        }

        @Override
        public List<RuntimeBinding> findAllByAdventureId(AdventureId adventureId) {
            return current != null && current.adventureId().equals(adventureId) ? List.of(current) : List.of();
        }

        @Override
        public void save(RuntimeBinding binding) {
            current = binding;
        }
    }

    private static final class InMemorySessionKnowledgeSetRepository implements SessionKnowledgeSetRepository {
        private final Map<SessionId, SessionKnowledgeSet> values = new HashMap<>();

        @Override
        public Optional<SessionKnowledgeSet> findBySessionId(SessionId sessionId) {
            return Optional.ofNullable(values.get(sessionId));
        }

        @Override
        public void save(SessionKnowledgeSet set) {
            values.put(set.sessionId(), set);
        }
    }

    private static final class InMemoryPackageRepository implements ScenarioPackageRepository {
        private final Map<UUID, ScenarioPackage> packages = new HashMap<>();

        private InMemoryPackageRepository(ScenarioPackage scenarioPackage) {
            packages.put(scenarioPackage.packageId(), scenarioPackage);
        }

        @Override
        public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) {
            return packages.values().stream().filter(candidate -> candidate.inputFingerprint().equals(fingerprint)).findFirst();
        }

        @Override
        public Optional<ScenarioPackage> findById(UUID packageId) {
            return Optional.ofNullable(packages.get(packageId));
        }

        @Override
        public void save(ScenarioPackage scenarioPackage) {
            packages.put(scenarioPackage.packageId(), scenarioPackage);
        }
    }

    private static final class InMemoryRuntimeTurnRepository implements RuntimeTurnRepository {
        private final List<com.dndmaster.adventure.application.runtime.RuntimeTurn> saved = new ArrayList<>();

        @Override
        public Optional<com.dndmaster.adventure.application.runtime.RuntimeTurn> findByTurnId(UUID turnId) {
            return saved.stream().filter(turn -> turn.turnId().equals(turnId)).findFirst();
        }

        @Override
        public Optional<com.dndmaster.adventure.application.runtime.RuntimeTurn> findByCommandId(UUID commandId) {
            return saved.stream().filter(turn -> turn.commandId().equals(commandId)).reduce((first, second) -> second);
        }

        @Override
        public List<com.dndmaster.adventure.application.runtime.RuntimeTurn> findAllByAdventureId(AdventureId adventureId) {
            return saved.stream().filter(turn -> turn.adventureId().equals(adventureId)).toList();
        }

        @Override
        public void save(com.dndmaster.adventure.application.runtime.RuntimeTurn turn) {
            saved.add(turn);
        }
    }

    private static final class FlakyRuntimeTurnRepository implements RuntimeTurnRepository {
        private final List<com.dndmaster.adventure.application.runtime.RuntimeTurn> saved = new ArrayList<>();
        private boolean failOnce = true;

        @Override
        public Optional<com.dndmaster.adventure.application.runtime.RuntimeTurn> findByTurnId(UUID turnId) {
            return saved.stream().filter(turn -> turn.turnId().equals(turnId)).findFirst();
        }

        @Override
        public Optional<com.dndmaster.adventure.application.runtime.RuntimeTurn> findByCommandId(UUID commandId) {
            return saved.stream().filter(turn -> turn.commandId().equals(commandId)).reduce((first, second) -> second);
        }

        @Override
        public List<com.dndmaster.adventure.application.runtime.RuntimeTurn> findAllByAdventureId(AdventureId adventureId) {
            return saved.stream().filter(turn -> turn.adventureId().equals(adventureId)).toList();
        }

        @Override
        public void save(com.dndmaster.adventure.application.runtime.RuntimeTurn turn) {
            if (failOnce && turn.committed()) {
                failOnce = false;
                throw new IllegalStateException("simulated turn persistence failure");
            }
            saved.add(turn);
        }
    }

    private static final class RecordingEvidenceSearchPort implements RuntimeEvidenceSearchPort {
        private final KnowledgeDocumentId storyId;
        private final KnowledgeDocumentId ruleId;
        private final List<RuntimeEvidenceType> requestTypes = new ArrayList<>();
        private final List<RuntimeEvidenceSearchRequest> requests = new ArrayList<>();
        private int calls;

        private RecordingEvidenceSearchPort(KnowledgeDocumentId storyId, KnowledgeDocumentId ruleId) {
            this.storyId = storyId;
            this.ruleId = ruleId;
        }

        @Override
        public List<RuntimeEvidence> search(RuntimeEvidenceSearchRequest request) {
            calls++;
            requests.add(request);
            requestTypes.add(request.evidenceType());
            List<RuntimeEvidence> result = request.evidenceType() == RuntimeEvidenceType.STORYBOOK
                    ? List.of(new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, storyId, 1, "page:1:span:1", "Story excerpt"))
                    : List.of(new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK, ruleId, 1, "rulebook:1", "Rule excerpt"));
            return result;
        }
    }

    private static final class RecordingPlanningPort implements RuntimePlanningPort {
        private final ActiveSourceContext proposed;
        private int calls;

        private RecordingPlanningPort(ActiveSourceContext proposed) {
            this.proposed = proposed;
        }

        @Override
        public RuntimePlan plan(RuntimePlanningRequest request) {
            calls++;
            RuntimeEvidence cited = request.evidencePack().storybook().isEmpty()
                    ? request.evidencePack().rulebook().getFirst()
                    : request.evidencePack().storybook().getFirst();
            return new RuntimePlan(
                    "새 장면",
                    "npc-state",
                    "판정 완료",
                    "근거를 바탕으로 응답한다.",
                    proposed,
                    List.of(cited),
                    List.of());
        }
    }

    private static final class AllowingSafetyPort implements NarrationSafetyPort {
        private final boolean approved;
        private int calls;

        private AllowingSafetyPort(boolean approved) {
            this.approved = approved;
        }

        @Override
        public NarrationSafetyAssessment assess(NarrationSafetyRequest request) {
            calls++;
            return new NarrationSafetyAssessment(approved, approved ? "approved" : "rejected");
        }
    }
}
