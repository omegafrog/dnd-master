package com.dndmaster.adventure.application.prologue;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.runtime.CharacterSheetReadPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.NarrationSafetyPort;
import com.dndmaster.adventure.application.runtime.NarrationSafetyRequest;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdventurePrologueApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdventurePrologueApplicationService.class);
    private final AdventureRepository adventures;
    private final AdventureStoryPlanRepository plans;
    private final CharacterSheetReadPort sheets;
    private final AdventurePrologueGenerationPort generator;
    private final GmAgentPort gmAgent;
    private final ScenarioPackageRepository packages;
    private final RuntimeEvidenceSearchPort evidenceSearch;
    private final NarrationSafetyPort narrationSafety;

    public AdventurePrologueApplicationService(AdventureRepository adventures, AdventureStoryPlanRepository plans,
            CharacterSheetReadPort sheets, AdventurePrologueGenerationPort generator) {
        this(adventures, plans, sheets, generator, null, null, null, null);
    }

    public AdventurePrologueApplicationService(AdventureRepository adventures, AdventureStoryPlanRepository plans,
            CharacterSheetReadPort sheets, AdventurePrologueGenerationPort generator, GmAgentPort gmAgent) {
        this(adventures, plans, sheets, generator, gmAgent, null, null, null);
    }

    public AdventurePrologueApplicationService(AdventureRepository adventures, AdventureStoryPlanRepository plans,
            CharacterSheetReadPort sheets, AdventurePrologueGenerationPort generator, GmAgentPort gmAgent,
            ScenarioPackageRepository packages, RuntimeEvidenceSearchPort evidenceSearch) {
        this(adventures, plans, sheets, generator, gmAgent, packages, evidenceSearch, null);
    }

    public AdventurePrologueApplicationService(AdventureRepository adventures, AdventureStoryPlanRepository plans,
            CharacterSheetReadPort sheets, AdventurePrologueGenerationPort generator, GmAgentPort gmAgent,
            ScenarioPackageRepository packages, RuntimeEvidenceSearchPort evidenceSearch, NarrationSafetyPort narrationSafety) {
        this.adventures = Objects.requireNonNull(adventures);
        this.plans = Objects.requireNonNull(plans);
        this.sheets = Objects.requireNonNull(sheets);
        this.generator = Objects.requireNonNull(generator);
        this.gmAgent = gmAgent;
        this.packages = packages;
        this.evidenceSearch = evidenceSearch;
        this.narrationSafety = narrationSafety;
    }

    public Adventure ensure(AdventureId adventureId, OwnerPlayerId owner) {
        Adventure adventure = adventures.findById(adventureId).orElseThrow(() -> new IllegalArgumentException("adventure not found"));
        if (!adventure.ownerPlayerId().equals(owner)) throw new SecurityException("adventure access denied");
        if (!adventure.conversation().isEmpty()) return adventure;
        var plan = plans.findBySessionId(adventure.sessionId()).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
        var stage = plan.stages().get(plan.currentStage());
        var party = adventure.party().stream().map(member -> {
            var sheet = sheets.read(member.characterSheetId());
            return new AdventurePrologueGenerationPort.CharacterSnapshot(sheet.name(), sheet.level());
        }).toList();
        var evidence = java.util.stream.Stream.concat(stage.npcOrClues().stream(), stage.endingIds().stream())
                .map(value -> "story-plan:stage-" + stage.position() + ":" + value).toList();
        AdventurePrologueGenerationPort.Request fallbackRequest = new AdventurePrologueGenerationPort.Request(stage, party, evidence);
        String narration;
        EvidencePack selectedEvidence = null;
        if (gmAgent == null) {
            narration = generator.generate(fallbackRequest);
        } else {
            try {
                selectedEvidence = prologueEvidence(adventure, owner, stage);
                narration = gmAgent.plan(new GmContextEnvelope(
                        adventure.id(), owner, adventure.sessionId().value(), java.util.UUID.randomUUID(),
                        adventure.scenarioId().value(), 0, adventure.currentContext(), null,
                        "모험의 첫 장면을 한국어로 생생하게 열어 주세요. 장소: " + stage.location() +
                                ", 목표: " + stage.goal() + ", 갈등: " + stage.conflict() +
                                ". 단서: " + String.join(", ", stage.npcOrClues()),
                        selectedEvidence,
                        java.util.List.of(), party.stream().map(value -> value.name() + " (레벨 " + value.level() + ")").toList(),
                        "첫 단계: " + stage.title(), "", "", "")).plan().narration();
            } catch (RuntimeException failure) {
                LOGGER.warn("adventure_prologue_gm_failed fallback=deterministic errorType={} message={}",
                        failure.getClass().getSimpleName(), failure.getMessage(), failure);
                narration = generator.generate(fallbackRequest);
            }
        }
        if (narrationSafety != null && selectedEvidence != null
                && !narrationSafety.assess(new NarrationSafetyRequest(narration, selectedEvidence,
                        adventure.currentContext(), "prologue")).approved()) {
            LOGGER.warn("adventure_prologue_narration_rejected fallback=deterministic");
            narration = generator.generate(fallbackRequest);
        }
        try {
            if (narration == null || narration.isBlank()) {
                throw new IllegalStateException("prologue narration is blank after GM and deterministic generation");
            }
            var conversation = new ArrayList<>(adventure.conversation());
            conversation.add(new ConversationEntry(0, "AI_GAME_MASTER", narration));
            adventure.preserveProgress(owner, adventure.version(), adventure.currentContext(), conversation);
            adventures.save(adventure);
        } catch (com.dndmaster.adventure.infrastructure.persistence.OptimisticAdventureLockException concurrentRecovery) {
            return adventures.findById(adventureId).orElseThrow(() -> concurrentRecovery);
        } catch (RuntimeException failure) {
            LOGGER.error("adventure_prologue_persist_failed adventureId={} phase=conversation-save", adventureId.value(), failure);
            throw failure;
        }
        return adventure;
    }

    private EvidencePack prologueEvidence(Adventure adventure, OwnerPlayerId owner,
            com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage stage) {
        if (packages == null || evidenceSearch == null) {
            throw new IllegalStateException("prologue evidence providers are required for GM generation");
        }
        ScenarioPackage scenarioPackage = packages.findById(adventure.scenarioId().value())
                .orElseThrow(() -> new IllegalStateException("scenario package not found for prologue evidence"));
        var extractionVersions = scenarioPackage.documents().stream().collect(java.util.stream.Collectors.toMap(
                document -> document.knowledgeDocumentId().value(),
                com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection::extractionVersion,
                (left, right) -> left));
        var storybookIds = documentIds(scenarioPackage, "STORYBOOK");
        var rulebookIds = documentIds(scenarioPackage, "RULEBOOK");
        String action = "모험 첫 장면: " + stage.location() + " " + stage.goal();
        var storybook = withPlanEvidence(search(adventure, owner, storybookIds, action, RuntimeEvidenceType.STORYBOOK, extractionVersions), stage, RuntimeEvidenceType.STORYBOOK);
        if (storybook.isEmpty()) throw new IllegalStateException("storybook evidence is required for a runtime prologue");
        var rulebook = withPlanEvidence(search(adventure, owner, rulebookIds, action, RuntimeEvidenceType.RULEBOOK, extractionVersions), stage, RuntimeEvidenceType.RULEBOOK);
        org.slf4j.LoggerFactory.getLogger(AdventurePrologueApplicationService.class).info(
                "adventure_prologue_evidence storybook={} rulebook={} stage={}", storybook.size(), rulebook.size(), stage.position());
        return new EvidencePack(storybook, rulebook, java.util.List.of());
    }

    private static List<RuntimeEvidence> withPlanEvidence(List<RuntimeEvidence> searched,
            com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage stage, RuntimeEvidenceType type) {
        if (!searched.isEmpty()) return searched;
        return stage.evidence().stream()
                .filter(item -> type.name().equalsIgnoreCase(item.documentType()))
                .map(item -> new RuntimeEvidence(type, new KnowledgeDocumentId(item.documentId()),
                        item.extractionVersion(), item.locator(), item.quote()))
                .toList();
    }

    private List<RuntimeEvidence> search(Adventure adventure, OwnerPlayerId owner, List<java.util.UUID> documentIds,
            String action, RuntimeEvidenceType type, java.util.Map<java.util.UUID, Long> extractionVersions) {
        if (documentIds.isEmpty()) return List.of();
        return evidenceSearch.search(new RuntimeEvidenceSearchRequest(
                adventure.id(), owner, adventure.sessionId(), adventure.scenarioId().value(), documentIds,
                null, action, type, 5, extractionVersions));
    }

    private static List<java.util.UUID> documentIds(ScenarioPackage scenarioPackage, String documentType) {
        return scenarioPackage.documents().stream()
                .filter(document -> documentType.equalsIgnoreCase(document.documentType()))
                .map(document -> document.knowledgeDocumentId().value())
                .distinct().toList();
    }
}
