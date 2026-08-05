package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RuntimeBindingApplicationService {
    private final AdventureRepository adventureRepository;
    private final ScenarioBundleRepository bundleRepository;
    private final ScenarioPackageRepository scenarioPackageRepository;
    private final RuntimeBindingRepository bindingRepository;
    private final InitialSourceContextProposalPort proposalPort;
    private final KnowledgeDocumentLookupPort knowledgeDocumentLookupPort;
    private final GameSystemDefinitionPort gameSystemDefinitionPort;
    private final boolean requirePublishedReferences;

    public RuntimeBindingApplicationService(
            AdventureRepository adventureRepository,
            ScenarioBundleRepository bundleRepository,
            ScenarioPackageRepository scenarioPackageRepository,
            RuntimeBindingRepository bindingRepository,
            InitialSourceContextProposalPort proposalPort,
            KnowledgeDocumentLookupPort knowledgeDocumentLookupPort) {
        this(adventureRepository, bundleRepository, scenarioPackageRepository, bindingRepository, proposalPort,
                knowledgeDocumentLookupPort, sessionId -> java.util.Optional.empty(), false);
    }

    public RuntimeBindingApplicationService(
            AdventureRepository adventureRepository, ScenarioBundleRepository bundleRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeBindingRepository bindingRepository,
            InitialSourceContextProposalPort proposalPort, KnowledgeDocumentLookupPort knowledgeDocumentLookupPort,
            GameSystemDefinitionPort gameSystemDefinitionPort) {
        this(adventureRepository, bundleRepository, scenarioPackageRepository, bindingRepository, proposalPort,
                knowledgeDocumentLookupPort, gameSystemDefinitionPort, true);
    }

    private RuntimeBindingApplicationService(
            AdventureRepository adventureRepository, ScenarioBundleRepository bundleRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeBindingRepository bindingRepository,
            InitialSourceContextProposalPort proposalPort, KnowledgeDocumentLookupPort knowledgeDocumentLookupPort,
            GameSystemDefinitionPort gameSystemDefinitionPort, boolean requirePublishedReferences) {
        this.adventureRepository = Objects.requireNonNull(adventureRepository, "adventure repository must not be null");
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.scenarioPackageRepository = Objects.requireNonNull(scenarioPackageRepository, "scenario package repository must not be null");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "binding repository must not be null");
        this.proposalPort = Objects.requireNonNull(proposalPort, "proposal port must not be null");
        this.knowledgeDocumentLookupPort = Objects.requireNonNull(knowledgeDocumentLookupPort, "knowledge document lookup port must not be null");
        this.gameSystemDefinitionPort = Objects.requireNonNull(gameSystemDefinitionPort, "game system definition port must not be null");
        this.requirePublishedReferences = requirePublishedReferences;
    }

    public RuntimeBinding bind(BindRuntimeBindingCommand command) {
        Adventure adventure = loadAdventure(command.adventureId(), command.ownerPlayerId());
        if (bindingRepository.findCurrentByAdventureId(command.adventureId()).isPresent()) {
            throw new IllegalStateException("runtime binding already exists; use the session lifecycle");
        }
        ScenarioPackage scenarioPackage = loadPackage(command.scenarioPackageId(), command.ownerPlayerId());
        validateRulebookAccess(command.ownerPlayerId(), command.rulebookIds());
        return persistBinding(
                adventure, command.ownerPlayerId(), scenarioPackage, command.rulebookIds(),
                command.engineId(), command.toolIds(), null);
    }

    /** Used only by session start recovery. Existing binding is the durable idempotency result. */
    public RuntimeBinding bindForSession(BindRuntimeBindingCommand command) {
        Adventure adventure = loadAdventure(command.adventureId(), command.ownerPlayerId());
        RuntimeBinding existing = bindingRepository.findCurrentByAdventureId(command.adventureId()).orElse(null);
        if (existing != null) return existing;
        ScenarioPackage scenarioPackage = loadPackage(command.scenarioPackageId(), command.ownerPlayerId());
        validateRulebookAccess(command.ownerPlayerId(), command.rulebookIds());
        return persistBinding(adventure, command.ownerPlayerId(), scenarioPackage, command.rulebookIds(),
                command.engineId(), command.toolIds(), null);
    }

    public RuntimeBinding switchScenarioPackage(SwitchRuntimePackageCommand command) {
        Adventure adventure = loadAdventure(command.adventureId(), command.ownerPlayerId());
        RuntimeBinding current = bindingRepository.findCurrentByAdventureId(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("runtime binding not found"));
        if (!current.ownerPlayerId().equals(command.ownerPlayerId())) {
            throw new IllegalStateException("runtime binding owner mismatch");
        }
        if (current.bindingVersion() != command.bindingVersion()) {
            throw new IllegalStateException("runtime binding version mismatch");
        }
        if (!current.party().isEmpty()) {
            throw new IllegalStateException("started session runtime package is frozen");
        }
        ScenarioPackage scenarioPackage = loadPackage(command.scenarioPackageId(), command.ownerPlayerId());
        validateRulebookAccess(command.ownerPlayerId(), current.rulebookIds());
        return persistBinding(
                adventure, command.ownerPlayerId(), scenarioPackage, current.rulebookIds(),
                current.engineId(), current.toolIds(), current.bindingVersion());
    }

    public RuntimeBinding chooseActiveSourceContext(ChooseActiveSourceContextCommand command) {
        Adventure adventure = loadAdventure(command.adventureId(), command.ownerPlayerId());
        RuntimeBinding current = bindingRepository.findCurrentByAdventureId(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("runtime binding not found"));
        if (current.bindingVersion() != command.bindingVersion()) {
            throw new IllegalStateException("runtime binding version mismatch");
        }
        ActiveSourceContext selected = current.playabilityReport().candidates().stream()
                .filter(candidate -> candidate.locator().equals(command.locator()))
                .findFirst()
                .map(candidate -> new ActiveSourceContext(candidate.knowledgeDocumentId(), candidate.extractionVersion(), candidate.locator(), candidate.excerpt()))
                .orElseThrow(() -> new IllegalStateException("source context candidate not found"));
        List<String> blockers = current.playabilityReport().blockers().stream()
                .filter(blocker -> !"initial source context is ambiguous".equals(blocker))
                .toList();
        PlayabilityReport report = buildReport(current.playabilityReport(), selected, List.of(), blockers);
        RuntimeBinding updated = current.withSelection(selected, report);
        bindingRepository.save(updated);
        return updated;
    }

    public RuntimeBinding read(AdventureId adventureId, OwnerPlayerId ownerPlayerId) {
        Adventure adventure = loadAdventure(adventureId, ownerPlayerId);
        return bindingRepository.findCurrentByAdventureId(adventure.id()).orElseThrow(() -> new IllegalStateException("runtime binding not found"));
    }

    private RuntimeBinding persistBinding(
            Adventure adventure,
            OwnerPlayerId ownerPlayerId,
            ScenarioPackage scenarioPackage,
            List<UUID> rulebookIds,
            String engineId,
            List<String> toolIds,
            Long previousBindingVersion) {
        List<InitialSourceContextCandidate> candidates = buildCandidates(scenarioPackage);
        InitialSourceContextProposalPort.InitialSourceContextProposalResult proposal = proposalPort.propose(scenarioPackage, candidates);
        PlayabilityReport report = buildReport(
                scenarioPackage.report().status().name(), scenarioPackage.report().warnings(), candidates, proposal,
                rulebookIds, engineId, toolIds);
        ActiveSourceContext selected = selectSourceContext(report, proposal);
        long definitionVersion = rulebookIds.stream()
                .map(gameSystemDefinitionPort::findByRulebook)
                .flatMap(java.util.Optional::stream)
                .mapToLong(GameSystemDefinitionPort.Definition::version)
                .findFirst().orElse(0L);
        long blueprintVersion = scenarioPackage.characterCreationBlueprint() == null
                ? 0L : scenarioPackage.characterCreationBlueprint().revision();
        if (requirePublishedReferences && (definitionVersion < 1 || blueprintVersion < 1)) {
            throw new IllegalStateException("published game system definition and character blueprint are required");
        }
        RuntimeBinding binding = previousBindingVersion == null
                ? RuntimeBinding.create(adventure.id(), ownerPlayerId, scenarioPackage.packageId(), scenarioPackage.bundleRevision(), rulebookIds, adventure.party(), engineId, toolIds, definitionVersion, blueprintVersion, report, selected)
                : RuntimeBinding.rehydrate(adventure.id(), ownerPlayerId, previousBindingVersion + 1, scenarioPackage.packageId(),
                scenarioPackage.bundleRevision(), rulebookIds, adventure.party(), engineId, toolIds, definitionVersion, blueprintVersion, report, selected);
        bindingRepository.save(binding);
        return binding;
    }

    private PlayabilityReport buildReport(
            String packageStatus,
            List<String> packageWarnings,
            List<InitialSourceContextCandidate> candidates,
            InitialSourceContextProposalPort.InitialSourceContextProposalResult proposal,
            List<UUID> rulebookIds,
            String engineId,
            List<String> toolIds) {
        List<String> warnings = new ArrayList<>(packageWarnings);
        List<String> blockers = new ArrayList<>();
        List<String> limits = new ArrayList<>();
        PlayabilityStatus status = PlayabilityStatus.PLAYABLE;
        if ("INVALID".equalsIgnoreCase(packageStatus)) {
            blockers.add("scenario package validation failed");
            status = PlayabilityStatus.BLOCKED;
        } else if ("PARTIAL".equalsIgnoreCase(packageStatus)) {
            warnings.add("scenario package has partial extraction");
            status = PlayabilityStatus.PLAYABLE_WITH_LIMITS;
        }
        if (proposal == null || proposal.candidates().isEmpty()) {
            blockers.add("no initial source context candidates");
            status = PlayabilityStatus.BLOCKED;
        } else if (proposal.candidates().size() > 1) {
            blockers.add("initial source context is ambiguous");
            status = PlayabilityStatus.BLOCKED;
        }
        if (rulebookIds == null || rulebookIds.isEmpty()) {
            blockers.add("rulebook knowledge set is missing");
            status = PlayabilityStatus.BLOCKED;
        }
        if (engineId == null || engineId.isBlank()) {
            blockers.add("engine is missing");
            status = PlayabilityStatus.BLOCKED;
        }
        if (toolIds == null || toolIds.isEmpty()) {
            limits.add("runtime tools are not pinned");
            if (status == PlayabilityStatus.PLAYABLE) {
                status = PlayabilityStatus.PLAYABLE_WITH_LIMITS;
            }
        }
        if (candidates.stream().anyMatch(candidate -> candidate.reason().contains("limit"))) {
            limits.add("source context derived from partial coverage");
            if (status == PlayabilityStatus.PLAYABLE) {
                status = PlayabilityStatus.PLAYABLE_WITH_LIMITS;
            }
        }
        if (status != PlayabilityStatus.BLOCKED && !limits.isEmpty()) {
            status = PlayabilityStatus.PLAYABLE_WITH_LIMITS;
        }
        return new PlayabilityReport(status, warnings, blockers, limits, proposal == null ? candidates : proposal.candidates());
    }

    private PlayabilityReport buildReport(
            PlayabilityReport current,
            ActiveSourceContext selected,
            List<String> warnings,
            List<String> blockers) {
        List<String> mergedWarnings = new ArrayList<>(current.warnings());
        mergedWarnings.addAll(warnings);
        List<String> mergedBlockers = new ArrayList<>(blockers);
        PlayabilityStatus status = mergedBlockers.isEmpty()
                ? (mergedWarnings.isEmpty() ? PlayabilityStatus.PLAYABLE : PlayabilityStatus.PLAYABLE_WITH_LIMITS)
                : PlayabilityStatus.BLOCKED;
        return new PlayabilityReport(status, mergedWarnings, mergedBlockers, current.limits(), current.candidates());
    }

    private ActiveSourceContext selectSourceContext(PlayabilityReport report, InitialSourceContextProposalPort.InitialSourceContextProposalResult proposal) {
        if (proposal == null || proposal.candidates().size() != 1) {
            return null;
        }
        InitialSourceContextCandidate candidate = proposal.candidates().get(0);
        return new ActiveSourceContext(candidate.knowledgeDocumentId(), candidate.extractionVersion(), candidate.locator(), candidate.excerpt());
    }

    private List<InitialSourceContextCandidate> buildCandidates(ScenarioPackage scenarioPackage) {
        List<InitialSourceContextCandidate> candidates = new ArrayList<>();
        for (var unit : scenarioPackage.runtimeCandidates()) {
            for (var ref : unit.sourceRefs()) {
                candidates.add(new InitialSourceContextCandidate(
                        ref.knowledgeDocumentId(), ref.extractionVersion(), ref.locator(), unit.sourceQuote(), 1.0d,
                        unit.status() == com.dndmaster.adventure.domain.scenario.ResolutionStatus.PARTIAL
                                ? "partial source context limit"
                                : "initial source context candidate"));
            }
        }
        return candidates.stream().distinct().toList();
    }

    private void validateRulebookAccess(OwnerPlayerId ownerPlayerId, List<UUID> rulebookIds) {
        List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> ownedDocuments = knowledgeDocumentLookupPort.findOwnedDocuments(ownerPlayerId.value());
        List<UUID> ownedRulebookIds = ownedDocuments.stream().map(record -> record.knowledgeDocumentId().value()).toList();
        if (rulebookIds == null || rulebookIds.isEmpty()) {
            throw new IllegalStateException("rulebook knowledge set is missing");
        }
        for (UUID rulebookId : rulebookIds) {
            if (!ownedRulebookIds.contains(rulebookId)) {
                throw new IllegalStateException("rulebook is not owned");
            }
        }
    }

    private Adventure loadAdventure(AdventureId adventureId, OwnerPlayerId ownerPlayerId) {
        Adventure adventure = adventureRepository.findById(adventureId).orElseThrow(() -> new IllegalStateException("adventure not found"));
        if (!adventure.ownerPlayerId().equals(ownerPlayerId)) {
            throw new IllegalStateException("adventure owner mismatch");
        }
        return adventure;
    }

    private ScenarioPackage loadPackage(UUID scenarioPackageId, OwnerPlayerId ownerPlayerId) {
        ScenarioPackage scenarioPackage = scenarioPackageRepository.findById(scenarioPackageId)
                .orElseThrow(() -> new IllegalStateException("scenario package not found"));
        bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(() -> new IllegalStateException("scenario bundle not found"))
                .authorize(new com.dndmaster.adventure.domain.scenario.OwnerPlayerId(ownerPlayerId.value()));
        return scenarioPackage;
    }

    public record BindRuntimeBindingCommand(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            UUID scenarioPackageId,
            List<UUID> rulebookIds,
            String engineId,
            List<String> toolIds) {}

    public record SwitchRuntimePackageCommand(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            long bindingVersion,
            UUID scenarioPackageId) {}

    public record ChooseActiveSourceContextCommand(
            AdventureId adventureId,
            OwnerPlayerId ownerPlayerId,
            long bindingVersion,
            String locator) {}
}
