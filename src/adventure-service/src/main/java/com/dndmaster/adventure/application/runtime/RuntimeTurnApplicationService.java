package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 근거 수집 -> 계획 -> 안전 검사 -> 세션 저장 순서로 런타임 턴을 처리한다.
public class RuntimeTurnApplicationService {
    private final AdventureRepository adventureRepository;
    private final RuntimeBindingRepository bindingRepository;
    private final ScenarioPackageRepository scenarioPackageRepository;
    private final RuntimeTurnRepository runtimeTurnRepository;
    private final RuntimeEvidenceSearchPort evidenceSearchPort;
    private final RuntimePlanningPort planningPort;
    private final NarrationSafetyPort narrationSafetyPort;
    private final SessionKnowledgeSetRepository sessionKnowledgeSetRepository;
    private final AdventureStoryPlanRepository storyPlanRepository;
    private final StoryContinuityContextProvider continuityContextProvider;
    private final RuntimeTurnCompactionCoordinator compactionCoordinator;
    private final GmContextResumePromptProvider resumePromptProvider;
    private final GmProviderBindingRepository providerBindingRepository;
    private final DeterministicAdjudicationService adjudicationService;
    private final AuthoritativeStateMutationPort stateMutationPort;
    private SessionEventRepository sessionEventRepository;

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository,
            RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository,
            RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort,
            RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort,
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository) {
        this(adventureRepository, bindingRepository, scenarioPackageRepository, runtimeTurnRepository, evidenceSearchPort,
                planningPort, narrationSafetyPort, sessionKnowledgeSetRepository, null, null, null, null);
    }

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository, RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort, RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AdventureStoryPlanRepository storyPlanRepository) {
        this(adventureRepository, bindingRepository, scenarioPackageRepository, runtimeTurnRepository, evidenceSearchPort,
                planningPort, narrationSafetyPort, sessionKnowledgeSetRepository, storyPlanRepository, null, null, null);
    }

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository, RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort, RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AdventureStoryPlanRepository storyPlanRepository, StoryContinuityContextProvider continuityContextProvider) {
        this(adventureRepository, bindingRepository, scenarioPackageRepository, runtimeTurnRepository, evidenceSearchPort,
                planningPort, narrationSafetyPort, sessionKnowledgeSetRepository, storyPlanRepository, continuityContextProvider, null, null);
    }

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository, RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort, RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AdventureStoryPlanRepository storyPlanRepository, StoryContinuityContextProvider continuityContextProvider,
            RuntimeTurnCompactionCoordinator compactionCoordinator, GmContextResumePromptProvider resumePromptProvider) {
        this(adventureRepository, bindingRepository, scenarioPackageRepository, runtimeTurnRepository, evidenceSearchPort,
                planningPort, narrationSafetyPort, sessionKnowledgeSetRepository, storyPlanRepository, continuityContextProvider,
                compactionCoordinator, resumePromptProvider, null);
    }

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository, RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort, RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AdventureStoryPlanRepository storyPlanRepository, StoryContinuityContextProvider continuityContextProvider,
            RuntimeTurnCompactionCoordinator compactionCoordinator, GmContextResumePromptProvider resumePromptProvider,
            GmProviderBindingRepository providerBindingRepository) {
        this(adventureRepository, bindingRepository, scenarioPackageRepository, runtimeTurnRepository, evidenceSearchPort,
                planningPort, narrationSafetyPort, sessionKnowledgeSetRepository, storyPlanRepository, continuityContextProvider,
                compactionCoordinator, resumePromptProvider, providerBindingRepository, null);
    }

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository, RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort, RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AdventureStoryPlanRepository storyPlanRepository, StoryContinuityContextProvider continuityContextProvider,
            RuntimeTurnCompactionCoordinator compactionCoordinator, GmContextResumePromptProvider resumePromptProvider,
            GmProviderBindingRepository providerBindingRepository, DeterministicAdjudicationService adjudicationService) {
        this(adventureRepository, bindingRepository, scenarioPackageRepository, runtimeTurnRepository, evidenceSearchPort,
                planningPort, narrationSafetyPort, sessionKnowledgeSetRepository, storyPlanRepository, continuityContextProvider,
                compactionCoordinator, resumePromptProvider, providerBindingRepository, adjudicationService, null);
    }

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository, RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort, RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            AdventureStoryPlanRepository storyPlanRepository, StoryContinuityContextProvider continuityContextProvider,
            RuntimeTurnCompactionCoordinator compactionCoordinator, GmContextResumePromptProvider resumePromptProvider,
            GmProviderBindingRepository providerBindingRepository, DeterministicAdjudicationService adjudicationService,
            AuthoritativeStateMutationPort stateMutationPort) {
        this.adventureRepository = Objects.requireNonNull(adventureRepository, "adventure repository must not be null");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "binding repository must not be null");
        this.scenarioPackageRepository = Objects.requireNonNull(scenarioPackageRepository, "scenario package repository must not be null");
        this.runtimeTurnRepository = Objects.requireNonNull(runtimeTurnRepository, "runtime turn repository must not be null");
        this.evidenceSearchPort = Objects.requireNonNull(evidenceSearchPort, "evidence search port must not be null");
        this.planningPort = Objects.requireNonNull(planningPort, "planning port must not be null");
        this.narrationSafetyPort = Objects.requireNonNull(narrationSafetyPort, "narration safety port must not be null");
        this.sessionKnowledgeSetRepository = Objects.requireNonNull(
                sessionKnowledgeSetRepository, "session knowledge set repository must not be null");
        this.storyPlanRepository = storyPlanRepository;
        this.continuityContextProvider = continuityContextProvider;
        this.compactionCoordinator = compactionCoordinator;
        this.resumePromptProvider = resumePromptProvider;
        this.providerBindingRepository = providerBindingRepository;
        this.adjudicationService = adjudicationService;
        this.stateMutationPort = stateMutationPort;
    }

    public void setSessionEventRepository(SessionEventRepository repository) {
        this.sessionEventRepository = repository;
    }

    @Transactional
    public RuntimeTurnResult submitTurn(SubmitRuntimeTurnCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Adventure adventure = adventureRepository.findById(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        adventure.reopen(command.ownerPlayerId());
        RuntimeTurn existing = runtimeTurnRepository.findByCommandId(command.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.turnId().equals(command.turnId())
                    || !existing.adventureId().equals(command.adventureId())
                    || !existing.action().equals(command.action())) {
                throw new IllegalStateException("runtime command id reused with different payload");
            }
            RuntimeTurn committed = resumeCommittedTurn(command, adventure, existing);
            return new RuntimeTurnResult(committed, committed.context(), committed.conversation(), committed.version());
        }
        if (command.expectedVersion() >= 0 && adventure.version() != command.expectedVersion()) {
            throw new IllegalStateException("adventure version does not match");
        }
        RuntimeBinding binding = bindingRepository.findCurrentByAdventureId(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("runtime binding not found"));
        if (!binding.ownerPlayerId().equals(command.ownerPlayerId())) {
            throw new IllegalStateException("runtime binding owner mismatch");
        }
        ScenarioPackage scenarioPackage = scenarioPackageRepository.findById(binding.scenarioPackageId())
                .orElseThrow(() -> new IllegalStateException("scenario package not found"));

        if (!command.advancesState()) {
            RuntimePlan metaPlan = new RuntimePlan(adventure.currentContext().currentScene(), adventure.currentContext().npcState(),
                    adventure.currentContext().latestJudgmentValue().orElse("meta question"),
                    "Meta question answered without advancing game state.", binding.activeSourceContext(), List.of(), List.of(),
                    "system", "read-only", "meta question");
            RuntimeTurn metaTurn = new RuntimeTurn(command.turnId(), command.commandId(), adventure.id(), adventure.sessionId().value(),
                    binding.scenarioPackageId(), binding.bindingVersion(), command.action(), new EvidencePack(List.of(), List.of(), List.of()),
                    metaPlan, binding.activeSourceContext(), adventure.currentContext(), adventure.conversation(), adventure.version(), List.of(), List.of())
                    .markCommitted();
            return new RuntimeTurnResult(metaTurn, adventure.currentContext(), adventure.conversation(), adventure.version());
        }

        EvidencePack evidencePack = command.evidenceOverride() == null
                ? prefetchEvidence(command, adventure, binding, scenarioPackage)
                : command.evidenceOverride().evidencePack();
        AuthoritativeResolution authoritativeResolution = adjudicationService == null ? null
                : adjudicationService.resolve(new DeterministicAdjudicationRequest(
                        command.commandId(), adventure.sessionId().value(), command.turnId(), command.ownerPlayerId().value(),
                        command.action(), adventure.currentContext().toString(), adventure.version(), adventure.version()));
        if (authoritativeResolution != null && authoritativeResolution.status() != AuthoritativeResolution.Status.RESOLVED) {
            throw new IllegalStateException("authoritative resolution is not complete");
        }
        evidencePack = scopedEvidencePack(evidencePack, command.ownerPlayerId().value(), adventure.sessionId().value(), binding.scenarioPackageId(),
                planningPort instanceof GmAgentRuntimePlanningAdapter);
        ModelInputProjection modelInput = modelInputProjection(evidencePack, adventure, scenarioPackage,
                planningPort instanceof GmAgentRuntimePlanningAdapter);
        EvidencePack modelEvidencePack = new EvidencePack(modelInput.storybook(), modelInput.rulebook(), modelInput.resolution());
        ActiveSourceContext modelActiveSource = modelEvidencePack.storybook().stream()
                .filter(evidence -> binding.activeSourceContext() != null
                        && evidence.knowledgeDocumentId().equals(binding.activeSourceContext().knowledgeDocumentId())
                        && evidence.extractionVersion() == binding.activeSourceContext().extractionVersion()
                        && evidence.locator().equals(binding.activeSourceContext().locator()))
                .findFirst().map(evidence -> binding.activeSourceContext()).orElse(null);
        List<String> modelRecentTurns = ModelInputProjection.redactProtectedTurns(
                adventure.conversation().stream().map(entry -> entry.speaker() + ": " + entry.content()).toList(),
                java.util.stream.Stream.of(evidencePack.storybook(), evidencePack.rulebook(), evidencePack.resolution())
                        .flatMap(List::stream).toList());
        modelInput = modelInput.withRuntimeInputs(adventure.currentContext(), modelActiveSource, command.action(),
                modelRecentTurns, adventure.party().stream().map(member -> member.characterSheetId().value() + " control=" + member.controlMode()).toList());
        Set<String> protectedFacts = hiddenData(adventure);
        RuntimePlan plan = planningPort.plan(new RuntimePlanningRequest(
                command.adventureId(), command.ownerPlayerId(), adventure.sessionId().value(), command.turnId(), binding.scenarioPackageId(), binding.bindingVersion(),
                adventure.currentContext(), modelActiveSource, command.action(), modelEvidencePack,
                modelInput.recentTurns(), modelInput.characterSnapshots(),
                modelInput.promptText(), providerSelection(adventure.sessionId().value(), "provider"),
                providerSelection(adventure.sessionId().value(), "model"),
                providerSelection(adventure.sessionId().value(), "reasoning"), modelInput)
                .withProtectedFacts(protectedFacts));
        if (authoritativeResolution != null) plan = plan.withAuthoritativeResolution(authoritativeResolution);
        new GmFinalValidator().validate(
                new GmPlanResult(plan, plan.provider(), plan.model(), plan.reasoning(), List.of()),
                evidencePack, adventure.currentContext(), protectedFacts);
        plan = plan.withWarning("validation=passed;repair-attempted="
                + plan.warnings().stream().anyMatch(warning -> warning.contains("repair-attempted=true")));
        NarrationSafetyAssessment safety = narrationSafetyPort.assess(new NarrationSafetyRequest(
                plan.narration(), evidencePack, adventure.currentContext(), command.action()));
        if (!safety.approved()) {
            throw new IllegalStateException("narration safety rejected: " + safety.reason());
        }

        ActiveSourceContext activeSourceContext = plan.proposedActiveSourceContext() != null
                ? plan.proposedActiveSourceContext()
                : binding.activeSourceContext();
        RuntimeBinding updatedBinding = activeSourceContext != binding.activeSourceContext()
                ? binding.withSelection(activeSourceContext, binding.playabilityReport())
                : binding;
        if (updatedBinding != binding) {
            bindingRepository.save(updatedBinding);
        }

        AdventureContext nextContext = new AdventureContext(plan.scene(), plan.npcState(), command.action(), plan.judgment());
        if (authoritativeResolution != null && stateMutationPort != null) {
            nextContext = stateMutationPort.apply(nextContext, authoritativeResolution);
        }
        List<ConversationEntry> conversation = new ArrayList<>(adventure.conversation());
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", plan.narration()));
        conversation.add(new ConversationEntry(conversation.size(), "PLAYER", command.action()));
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", plan.judgment()));

        long nextVersion = adventure.version() + 1 + (command.turnCharacterSheetId() == null ? 0 : 1);
        List<String> persistedWarnings = new ArrayList<>(plan.warnings());
        if (command.evidenceOverride() != null) {
            persistedWarnings.add("rag-condition:" + command.evidenceOverride().condition());
        }
        RuntimeTurn turn = new RuntimeTurn(
                command.turnId(), command.commandId(), adventure.id(), adventure.sessionId().value(), binding.scenarioPackageId(),
                binding.bindingVersion(), command.action(), evidencePack, plan, activeSourceContext, nextContext,
                conversation, nextVersion,
                plan.citedEvidence().stream()
                        .map(evidence -> evidence.evidenceType() + ":" + evidence.locator())
                        .toList(),
                persistedWarnings);
        runtimeTurnRepository.save(turn);

        Adventure progressed = Adventure.rehydrate(
                adventure.id(), adventure.sessionId(), adventure.ownerPlayerId(), adventure.scenarioId(),
                adventure.ruleSetId(), adventure.party(), adventure.conversation(), adventure.currentContext(),
                adventure.status(), adventure.version(), adventure.turnIndex(), adventure.lastTurnKey());
        progressed.preserveProgress(command.ownerPlayerId(), adventure.version(), nextContext, conversation);
        if (command.turnCharacterSheetId() != null) {
            progressed.advanceTurn(command.ownerPlayerId(), command.turnIndex(), command.turnCharacterSheetId(), command.turnId());
        }
        adventureRepository.save(progressed);

        RuntimeTurn committed = turn.markCommitted();
        runtimeTurnRepository.save(committed);
        if (compactionCoordinator != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { compactionCoordinator.afterCommit(committed); }
                });
            } else {
                compactionCoordinator.afterCommit(committed);
            }
        }
        return new RuntimeTurnResult(committed, progressed.currentContext(), progressed.conversation(), progressed.version());
    }

    private Set<String> hiddenData(Adventure adventure) {
        String storyContext = storyPlanContext(adventure);
        if (storyContext == null || storyContext.isBlank()) return Set.of();
        Set<String> values = new HashSet<>();
        values.add(storyContext);
        Arrays.stream(storyContext.split(";"))
                .map(String::trim)
                .filter(value -> value.contains("="))
                .map(value -> value.substring(value.indexOf('=') + 1).trim())
                .filter(value -> value.length() >= 4)
                .forEach(values::add);
        return Set.copyOf(values);
    }

    private String providerSelection(UUID sessionId, String field) {
        if (providerBindingRepository == null) return "";
        ProviderBinding binding = providerBindingRepository.current(sessionId).orElse(null);
        if (binding == null) return "";
        return switch (field) {
            case "provider" -> binding.selection().provider();
            case "model" -> binding.selection().model();
            case "reasoning" -> binding.selection().reasoning();
            default -> "";
        };
    }

    private RuntimeTurn resumeCommittedTurn(SubmitRuntimeTurnCommand command, Adventure adventure, RuntimeTurn existing) {
        if (existing.committed()) {
            return existing;
        }
        long expectedProgressDelta = command.turnCharacterSheetId() == null ? 1 : 2;
        if (adventure.version() == existing.version() - expectedProgressDelta) {
            Adventure progressed = Adventure.rehydrate(
                    adventure.id(), adventure.sessionId(), adventure.ownerPlayerId(), adventure.scenarioId(),
                    adventure.ruleSetId(), adventure.party(), adventure.conversation(), adventure.currentContext(),
                    adventure.status(), adventure.version(), adventure.turnIndex(), adventure.lastTurnKey());
            progressed.preserveProgress(command.ownerPlayerId(), adventure.version(), existing.context(), existing.conversation());
            if (command.turnCharacterSheetId() != null) {
                progressed.advanceTurn(command.ownerPlayerId(), command.turnIndex(), command.turnCharacterSheetId(), command.turnId());
            }
            adventureRepository.save(progressed);
        }
        RuntimeTurn committed = existing.markCommitted();
        runtimeTurnRepository.save(committed);
        if (compactionCoordinator != null && committed.committed()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { compactionCoordinator.afterCommit(committed); }
                });
            } else {
                compactionCoordinator.afterCommit(committed);
            }
        }
        return committed;
    }

    private EvidencePack prefetchEvidence(
            SubmitRuntimeTurnCommand command, Adventure adventure, RuntimeBinding binding, ScenarioPackage scenarioPackage) {
        List<UUID> knowledgeDocumentIds = knowledgeDocumentIds(adventure, scenarioPackage);
        List<RuntimeEvidence> storybook = scopedSearch(new RuntimeEvidenceSearchRequest(
                adventure.id(), command.ownerPlayerId(), adventure.sessionId(), binding.scenarioPackageId(), knowledgeDocumentIds,
                binding.activeSourceContext(), command.action(), RuntimeEvidenceType.STORYBOOK, 5));
        List<RuntimeEvidence> rulebook = scopedSearch(new RuntimeEvidenceSearchRequest(
                adventure.id(), command.ownerPlayerId(), adventure.sessionId(), binding.scenarioPackageId(), knowledgeDocumentIds,
                binding.activeSourceContext(), command.action(), RuntimeEvidenceType.RULEBOOK, 5));
        List<RuntimeEvidence> resolution = scenarioPackage.runtimeCandidates().stream()
                .flatMap(unit -> resolutionEvidence(unit).stream())
                .map(evidence -> evidence.withScope(command.ownerPlayerId().value(), adventure.sessionId().value(), binding.scenarioPackageId()))
                .filter(evidence -> knowledgeDocumentIds.contains(evidence.knowledgeDocumentId().value()))
                .toList();
        return new EvidencePack(storybook, rulebook, resolution);
    }

    private String storyPlanContext(Adventure adventure) {
        if (continuityContextProvider != null) {
            return continuityContextProvider.load(adventure.sessionId().value()).map(StoryContinuityContext::playerSafeText).orElse("");
        }
        return "";
    }

    private ModelInputProjection modelInputProjection(EvidencePack evidencePack, Adventure adventure, ScenarioPackage scenarioPackage,
            boolean strictProvider) {
        Set<String> disclosureEvents = sessionEventRepository == null ? Set.of() : sessionEventRepository.after(adventure.sessionId().value(), -1).stream()
                .flatMap(event -> java.util.stream.Stream.of(event.type(), event.payload()))
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, Long> expectedVersions = scenarioPackage.documents().stream().collect(java.util.stream.Collectors.toMap(
                document -> document.knowledgeDocumentId().value(),
                com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection::extractionVersion,
                (first, ignored) -> first));
        if (!strictProvider) {
            expectedVersions = java.util.stream.Stream.of(evidencePack.storybook(), evidencePack.rulebook(), evidencePack.resolution())
                    .flatMap(List::stream).collect(java.util.stream.Collectors.toMap(
                            evidence -> evidence.knowledgeDocumentId().value(), RuntimeEvidence::extractionVersion, (first, ignored) -> first));
        }
        return ModelInputProjection.createStrict(new HashSet<>(knowledgeDocumentIds(adventure, scenarioPackage)), expectedVersions,
                adventure.ownerPlayerId().value(), adventure.sessionId().value(), scenarioPackage.packageId(), evidencePack.storybook(),
                evidencePack.rulebook(), evidencePack.resolution(), storyPlanContext(adventure), disclosureEvents, adventure.turnIndex());
    }

    private static EvidencePack scopedEvidencePack(EvidencePack pack, UUID owner, UUID session, UUID scenarioPackage, boolean strict) {
        java.util.function.Function<RuntimeEvidence, RuntimeEvidence> scope = evidence -> {
            if (strict && (evidence.ownerPlayerId() == null || evidence.sessionId() == null || evidence.scenarioPackageId() == null)) {
                throw new IllegalArgumentException("unscoped evidence cannot enter provider planning");
            }
            return evidence.withScope(owner, session, scenarioPackage);
        };
        return new EvidencePack(pack.storybook().stream().map(scope).toList(), pack.rulebook().stream().map(scope).toList(),
                pack.resolution().stream().map(scope).toList());
    }

    private List<RuntimeEvidence> scopedSearch(RuntimeEvidenceSearchRequest request) {
        try {
            return evidenceSearchPort.search(request).stream()
                    .filter(evidence -> request.knowledgeDocumentIds().contains(evidence.knowledgeDocumentId().value()))
                    .toList();
        } catch (RuntimeException ignored) {
            // Evidence search is enrichment; a provider turn can still proceed with no citations.
            return List.of();
        }
    }

    private List<UUID> knowledgeDocumentIds(Adventure adventure, ScenarioPackage scenarioPackage) {
        SessionKnowledgeSet set = sessionKnowledgeSetRepository.findBySessionId(adventure.sessionId())
                .orElseGet(() -> new SessionKnowledgeSet(adventure.sessionId(), List.of()));
        if (!set.sessionId().equals(adventure.sessionId())) {
            throw new IllegalStateException("session knowledge set does not match adventure");
        }
        if (!set.knowledgeDocumentIds().isEmpty()) {
            return set.knowledgeDocumentIds().stream().map(id -> id.value()).toList();
        }
        return scenarioPackage.documents().stream()
                .map(document -> document.knowledgeDocumentId().value())
                .distinct()
                .toList();
    }

    private static List<RuntimeEvidence> resolutionEvidence(ScenarioResolutionUnit unit) {
        List<RuntimeEvidence> evidence = new ArrayList<>();
        for (ScenarioSourceReference ref : unit.sourceRefs()) {
            evidence.add(new RuntimeEvidence(
                    RuntimeEvidenceType.RESOLUTION, ref.knowledgeDocumentId(), ref.extractionVersion(), ref.locator(),
                    unit.sourceQuote(), StoryEvidenceVisibility.PLAYER_VISIBLE, null, 0,
                    java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("resolution-status=" + unit.status()),
                            unit.validationMessages().stream()).toList(), null));
        }
        return evidence;
    }
}
