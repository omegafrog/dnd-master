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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                    metaPlan, binding.activeSourceContext(), adventure.currentContext(), adventure.conversation(), adventure.version(), List.of(), List.of(), false, !command.gmOnly(), false)
                    .markCommitted();
            return new RuntimeTurnResult(metaTurn, adventure.currentContext(), adventure.conversation(), adventure.version());
        }

        EvidencePack evidencePack = prefetchEvidence(command, adventure, binding, scenarioPackage);
        RuntimePlan plan = planningPort.plan(new RuntimePlanningRequest(
                command.adventureId(), command.ownerPlayerId(), adventure.sessionId().value(), command.turnId(), binding.scenarioPackageId(), binding.bindingVersion(),
                adventure.currentContext(), binding.activeSourceContext(), command.action(), evidencePack,
                adventure.conversation().stream().map(entry -> entry.speaker() + ": " + entry.content()).toList(),
                adventure.party().stream().map(member -> member.characterSheetId().value() + " control=" + member.controlMode()).toList(),
                storyPlanContext(adventure), providerSelection(adventure.sessionId().value(), "provider"),
                providerSelection(adventure.sessionId().value(), "model"),
                providerSelection(adventure.sessionId().value(), "reasoning")));
        NarrationSafetyAssessment safety = narrationSafetyPort.assess(new NarrationSafetyRequest(
                plan.narration(), evidencePack, adventure.currentContext(), command.action()));
        if (!safety.approved()) {
            throw new IllegalStateException("narration safety rejected: " + safety.reason());
        }
        advanceStoryPlanIfRequested(adventure.sessionId(), plan);

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
        List<ConversationEntry> conversation = new ArrayList<>(adventure.conversation());
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", plan.narration()));
        if (!command.gmOnly()) {
            conversation.add(new ConversationEntry(conversation.size(), "PLAYER", command.action()));
        }
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", plan.judgment()));

        long nextVersion = adventure.version() + 1 + (command.turnCharacterSheetId() == null ? 0 : 1);
        RuntimeTurn turn = new RuntimeTurn(
                command.turnId(), command.commandId(), adventure.id(), adventure.sessionId().value(), binding.scenarioPackageId(),
                binding.bindingVersion(), command.action(), evidencePack, plan, activeSourceContext, nextContext,
                conversation, nextVersion,
                plan.citedEvidence().stream()
                        .map(evidence -> evidence.evidenceType() + ":" + evidence.locator())
                        .toList(),
                plan.warnings());
        turn = new RuntimeTurn(turn.turnId(), turn.commandId(), turn.adventureId(), turn.sessionId(), turn.scenarioPackageId(),
                turn.bindingVersion(), turn.action(), turn.evidencePack(), turn.plan(), turn.activeSourceContext(), turn.context(),
                turn.conversation(), turn.version(), turn.citations(), turn.warnings(), false, !command.gmOnly(), command.advancesState());
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

    private String providerSelection(UUID sessionId, String field) {
        if (providerBindingRepository == null) return defaultProviderSelection(field);
        ProviderBinding binding = providerBindingRepository.current(sessionId).orElse(null);
        if (binding == null) return defaultProviderSelection(field);
        return switch (field) {
            case "provider" -> blankOrDefault(binding.selection().provider(), field);
            case "model" -> blankOrDefault(binding.selection().model(), field);
            case "reasoning" -> blankOrDefault(binding.selection().reasoning(), field);
            default -> "";
        };
    }

    private static String blankOrDefault(String value, String field) {
        return value == null || value.isBlank() ? defaultProviderSelection(field) : value;
    }

    /** Keep runtime turns executable during migration when an old session has no binding row. */
    private static String defaultProviderSelection(String field) {
        return switch (field) {
            case "provider" -> "codex-cli";
            case "model" -> "gpt-5.6-luna";
            case "reasoning" -> "none";
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
        List<UUID> storybookDocumentIds = documentIdsOfType(scenarioPackage, "STORYBOOK", knowledgeDocumentIds);
        List<UUID> rulebookDocumentIds = documentIdsOfType(scenarioPackage, "RULEBOOK", knowledgeDocumentIds);
        Map<UUID, Long> extractionVersions = scenarioPackage.documents().stream()
                .collect(java.util.stream.Collectors.toMap(document -> document.knowledgeDocumentId().value(),
                        com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection::extractionVersion, (a, b) -> a));
        extractionVersions = extractionVersions.entrySet().stream().filter(entry -> entry.getValue() > 1)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        List<RuntimeEvidence> storybook = scopedSearch(new RuntimeEvidenceSearchRequest(
                adventure.id(), command.ownerPlayerId(), adventure.sessionId(), binding.scenarioPackageId(), storybookDocumentIds,
                binding.activeSourceContext(), command.action(), RuntimeEvidenceType.STORYBOOK, 5, extractionVersions));
        List<RuntimeEvidence> rulebook = rulebookDocumentIds.isEmpty() ? List.of() : scopedSearch(new RuntimeEvidenceSearchRequest(
                adventure.id(), command.ownerPlayerId(), adventure.sessionId(), binding.scenarioPackageId(), rulebookDocumentIds,
                binding.activeSourceContext(), command.action(), RuntimeEvidenceType.RULEBOOK, 5, extractionVersions));
        List<RuntimeEvidence> resolution = scenarioPackage.runtimeCandidates().stream()
                .flatMap(unit -> resolutionEvidence(unit).stream())
                .filter(evidence -> knowledgeDocumentIds.contains(evidence.knowledgeDocumentId().value()))
                .toList();
        if (storybook.isEmpty()) {
            throw new IllegalStateException("storybook evidence is required for a runtime GM turn");
        }
        return new EvidencePack(storybook, rulebook, resolution);
    }

    private static List<UUID> documentIdsOfType(ScenarioPackage scenarioPackage, String type, List<UUID> selected) {
        return scenarioPackage.documents().stream()
                .filter(document -> type.equalsIgnoreCase(document.documentType()))
                .map(document -> document.knowledgeDocumentId().value())
                .filter(selected::contains)
                .distinct()
                .toList();
    }

    private String storyPlanContext(Adventure adventure) {
        String checkpoint = resumePromptProvider == null ? "" : resumePromptProvider.prompt(adventure.sessionId().value());
        if (continuityContextProvider != null) {
            return checkpoint + continuityContextProvider.load(adventure.sessionId().value()).map(StoryContinuityContext::promptText).orElse("");
        }
        if (storyPlanRepository == null) return checkpoint;
        return checkpoint + storyPlanRepository.findBySessionId(adventure.sessionId())
                .map(AdventureStoryPlanRuntimeContext::format).orElse("");
    }

    private void advanceStoryPlanIfRequested(com.dndmaster.adventure.domain.adventure.SessionId sessionId, RuntimePlan plan) {
        if (!plan.advanceStoryPlan() || storyPlanRepository == null) return;
        storyPlanRepository.findBySessionId(sessionId).ifPresent(current -> {
            if (current.stages().isEmpty() || current.currentStage() >= current.stages().size() - 1) return;
            var stage = current.stages().get(current.currentStage());
            if (!plan.selectedBranchId().isBlank() && !stage.branchIds().contains(plan.selectedBranchId())) {
                throw new IllegalStateException("GM selected an unknown story branch");
            }
            int target = current.currentStage() + 1;
            if (!plan.selectedBranchId().isBlank()) {
                String destination = stage.branchTargets().get(plan.selectedBranchId());
                if (destination != null && destination.startsWith("stage:")) {
                    target = Integer.parseInt(destination.substring("stage:".length())) - 1;
                } else if (destination != null) {
                    target = current.stages().stream().filter(candidate -> candidate.endingIds().contains(destination))
                            .map(candidate -> candidate.position() - 1).findFirst().orElse(target);
                }
            }
            if (target > current.currentStage() && target < current.stages().size()) storyPlanRepository.save(current.advanceTo(target));
        });
    }

    private List<RuntimeEvidence> scopedSearch(RuntimeEvidenceSearchRequest request) {
        return evidenceSearchPort.search(request).stream()
                .filter(evidence -> request.knowledgeDocumentIds().contains(evidence.knowledgeDocumentId().value()))
                .toList();
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
                    unit.sourceQuote()));
        }
        return evidence;
    }
}
