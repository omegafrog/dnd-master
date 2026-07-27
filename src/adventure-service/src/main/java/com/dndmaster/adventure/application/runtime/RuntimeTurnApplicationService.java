package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// 근거 수집 -> 계획 -> 안전 검사 -> 세션 저장 순서로 런타임 턴을 처리한다.
public final class RuntimeTurnApplicationService {
    private final AdventureRepository adventureRepository;
    private final RuntimeBindingRepository bindingRepository;
    private final ScenarioPackageRepository scenarioPackageRepository;
    private final RuntimeTurnRepository runtimeTurnRepository;
    private final RuntimeEvidenceSearchPort evidenceSearchPort;
    private final RuntimePlanningPort planningPort;
    private final NarrationSafetyPort narrationSafetyPort;

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository,
            RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository,
            RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort,
            RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort) {
        this.adventureRepository = Objects.requireNonNull(adventureRepository, "adventure repository must not be null");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "binding repository must not be null");
        this.scenarioPackageRepository = Objects.requireNonNull(scenarioPackageRepository, "scenario package repository must not be null");
        this.runtimeTurnRepository = Objects.requireNonNull(runtimeTurnRepository, "runtime turn repository must not be null");
        this.evidenceSearchPort = Objects.requireNonNull(evidenceSearchPort, "evidence search port must not be null");
        this.planningPort = Objects.requireNonNull(planningPort, "planning port must not be null");
        this.narrationSafetyPort = Objects.requireNonNull(narrationSafetyPort, "narration safety port must not be null");
    }

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
        RuntimeBinding binding = bindingRepository.findCurrentByAdventureId(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("runtime binding not found"));
        if (!binding.ownerPlayerId().equals(command.ownerPlayerId())) {
            throw new IllegalStateException("runtime binding owner mismatch");
        }
        ScenarioPackage scenarioPackage = scenarioPackageRepository.findById(binding.scenarioPackageId())
                .orElseThrow(() -> new IllegalStateException("scenario package not found"));

        EvidencePack evidencePack = prefetchEvidence(command, adventure, binding, scenarioPackage);
        RuntimePlan plan = planningPort.plan(new RuntimePlanningRequest(
                command.adventureId(), command.ownerPlayerId(), binding.scenarioPackageId(), binding.bindingVersion(),
                adventure.currentContext(), binding.activeSourceContext(), command.action(), evidencePack));
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
        List<ConversationEntry> conversation = new ArrayList<>(adventure.conversation());
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", plan.narration()));
        conversation.add(new ConversationEntry(conversation.size(), "PLAYER", command.action()));
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", plan.judgment()));

        long nextVersion = adventure.version() + 1;
        RuntimeTurn turn = new RuntimeTurn(
                command.turnId(), command.commandId(), adventure.id(), adventure.sessionId().value(), binding.scenarioPackageId(),
                binding.bindingVersion(), command.action(), evidencePack, plan, activeSourceContext, nextContext,
                conversation, nextVersion,
                plan.citedEvidence().stream()
                        .map(evidence -> evidence.evidenceType() + ":" + evidence.locator())
                        .toList(),
                plan.warnings());
        runtimeTurnRepository.save(turn);

        Adventure progressed = Adventure.rehydrate(
                adventure.id(), adventure.sessionId(), adventure.ownerPlayerId(), adventure.scenarioId(),
                adventure.ruleSetId(), adventure.party(), adventure.conversation(), adventure.currentContext(),
                adventure.status(), adventure.version());
        progressed.preserveProgress(command.ownerPlayerId(), adventure.version(), nextContext, conversation);
        adventureRepository.save(progressed);

        RuntimeTurn committed = turn.markCommitted();
        runtimeTurnRepository.save(committed);
        return new RuntimeTurnResult(committed, progressed.currentContext(), progressed.conversation(), progressed.version());
    }

    private RuntimeTurn resumeCommittedTurn(SubmitRuntimeTurnCommand command, Adventure adventure, RuntimeTurn existing) {
        if (existing.committed()) {
            return existing;
        }
        if (adventure.version() == existing.version() - 1) {
            Adventure progressed = Adventure.rehydrate(
                    adventure.id(), adventure.sessionId(), adventure.ownerPlayerId(), adventure.scenarioId(),
                    adventure.ruleSetId(), adventure.party(), adventure.conversation(), adventure.currentContext(),
                    adventure.status(), adventure.version());
            progressed.preserveProgress(command.ownerPlayerId(), adventure.version(), existing.context(), existing.conversation());
            adventureRepository.save(progressed);
        }
        RuntimeTurn committed = existing.markCommitted();
        runtimeTurnRepository.save(committed);
        return committed;
    }

    private EvidencePack prefetchEvidence(
            SubmitRuntimeTurnCommand command, Adventure adventure, RuntimeBinding binding, ScenarioPackage scenarioPackage) {
        List<RuntimeEvidence> storybook = evidenceSearchPort.search(new RuntimeEvidenceSearchRequest(
                adventure.id(), command.ownerPlayerId(), binding.scenarioPackageId(), binding.rulebookIds(),
                binding.activeSourceContext(), command.action(), RuntimeEvidenceType.STORYBOOK, 5));
        List<RuntimeEvidence> rulebook = evidenceSearchPort.search(new RuntimeEvidenceSearchRequest(
                adventure.id(), command.ownerPlayerId(), binding.scenarioPackageId(), binding.rulebookIds(),
                binding.activeSourceContext(), command.action(), RuntimeEvidenceType.RULEBOOK, 5));
        List<RuntimeEvidence> resolution = scenarioPackage.runtimeCandidates().stream()
                .flatMap(unit -> resolutionEvidence(unit).stream())
                .toList();
        return new EvidencePack(storybook, rulebook, resolution);
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
