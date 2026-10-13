package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.Objects;

/** Starts a Scenario Model Runtime Adventure and owns the READY guard. */
public final class AdventureStartApplicationService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AdventureStartApplicationService.class);
    private final ScenarioPackageRepository packages;
    private final AdventureRepository adventures;
    private final com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationApplicationService stagePreparation;

    public AdventureStartApplicationService(ScenarioPackageRepository packages, AdventureRepository adventures,
            com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationApplicationService stagePreparation) {
        this.packages = Objects.requireNonNull(packages, "package repository must not be null");
        this.adventures = Objects.requireNonNull(adventures, "adventure repository must not be null");
        this.stagePreparation = Objects.requireNonNull(stagePreparation, "stage preparation must not be null");
    }

    public Adventure start(StartAdventureCommand command) {
        Objects.requireNonNull(command, "start command must not be null");
        String context = "sessionId=" + command.sessionId().value() + " adventureId=" + command.adventureId().value()
                + " packageId=" + command.scenarioPackageId() + " requestId=" + command.requestId();
        return com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "adventure_start", context, () -> startInternal(command));
    }

    private Adventure startInternal(StartAdventureCommand command) {
        ScenarioPackage scenarioPackage = packages.findById(command.scenarioPackageId())
                .orElseThrow(() -> new IllegalStateException("scenario package not found"));
        if (!scenarioPackage.isReady()) throw new IllegalStateException("scenario package is not READY");
        if (scenarioPackage.bundleRevision() != command.scenarioPackageRevision()) {
            throw new IllegalStateException("scenario package revision does not match start request");
        }

        Adventure adventure = adventures.findById(command.adventureId()).orElse(null);
        if (adventure != null) {
            if (!adventure.ownerPlayerId().equals(command.ownerPlayerId())) {
                if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                    LOGGER.info("dev_flow operation=adventure_start_existing_guard outcome=blocked adventureId={} expectedOwnerId={} actualOwnerId={} expectedPackageId={} actualPackageId={} expectedStatus=authorized_existing_adventure actualStatus={} reason=owner_mismatch recovery=retry_with_adventure_owner",
                            command.adventureId().value(), adventure.ownerPlayerId().value(), command.ownerPlayerId().value(),
                            adventure.lockedScenarioPackageId(), command.scenarioPackageId(), adventure.status());
                }
                throw new SecurityException("adventure access denied");
            }
            if (!command.scenarioPackageId().equals(adventure.lockedScenarioPackageId())) {
                throw new IllegalStateException("adventure is locked to another scenario package");
            }
            if (adventure.status() == com.dndmaster.adventure.domain.adventure.AdventureStatus.ACTIVE) return adventure;
            if (adventure.status() != com.dndmaster.adventure.domain.adventure.AdventureStatus.STARTING) {
                throw new IllegalStateException("adventure cannot resume from " + adventure.status());
            }
        }

        var preparation = packages.findSpellDefinitionPreparationBySession(command.sessionId().value());
        boolean requiresPreparation = !scenarioPackage.spellDefinitions().isEmpty()
                || scenarioPackage.characterCreationBlueprint() != null
                        && scenarioPackage.characterCreationBlueprint().provenance().selectedRulebookId() != null
                || scenarioPackage.documents().stream().anyMatch(document ->
                        "RULEBOOK".equalsIgnoreCase(document.documentType()));
        if (preparation.isEmpty() && requiresPreparation) {
            logSpellReviewGate(command, 1, 0, 0, "missing_preparation");
            throw new IllegalStateException("spell/action review preparation is required before adventure start");
        }
        if (preparation.isPresent()) {
            var review = preparation.get();
            if (!review.packageId().equals(command.scenarioPackageId()) || !review.ownerId().equals(command.ownerPlayerId().value())) {
                throw new SecurityException("spell/action review preparation does not belong to this owner and package");
            }
            long pendingDefinitions = review.definitions().stream()
                    .filter(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition::active)
                    .filter(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition::required)
                    .filter(definition -> definition.reviewStatus()
                            != com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.ReviewStatus.APPROVED)
                    .count();
            long requiredUnsupported = review.definitions().stream()
                    .filter(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition::active)
                    .filter(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition::required)
                    .filter(definition -> !definition.executable() || definition.rangeProfile() == null
                            || !definition.rangeProfile().supported() || definition.targetingProfile() == null
                            || !definition.targetingProfile().supported())
                    .count();
            long pendingOptionalDefinitions = review.definitions().stream()
                    .filter(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition::active)
                    .filter(definition -> !definition.required())
                    .filter(definition -> definition.reviewStatus()
                            != com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.ReviewStatus.APPROVED)
                    .count();
            var selectedSource = selectedRulebookSource(scenarioPackage);
            long mismatchedSourceDefinitions = selectedSource == null ? 0 : review.definitions().stream()
                    .filter(definition -> !definition.userAdded())
                    .filter(definition -> !definition.sourceDocumentId().equals(selectedSource.documentId())
                            || definition.extractionVersion() != selectedSource.registrationVersion())
                    .count();
            if (mismatchedSourceDefinitions > 0) {
                if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                    LOGGER.info("dev_flow operation=adventure_start_spell_review outcome=blocked sessionId={} expectedSourceDocumentId={} expectedRegistrationVersion={} actualDefinitionCount={} actualMismatchedSourceCount={} reason=review_definitions_from_unselected_source recovery=reload_review_from_selected_rulebook",
                            command.sessionId().value(), selectedSource.documentId(), selectedSource.registrationVersion(),
                            review.definitions().size(), mismatchedSourceDefinitions);
                }
                throw new IllegalStateException("spell/action review definitions do not match the selected rulebook source");
            }
            if (pendingDefinitions > 0 || requiredUnsupported > 0) {
                logSpellReviewGate(command, pendingDefinitions, pendingOptionalDefinitions, requiredUnsupported,
                        pendingDefinitions > 0 ? "pending_review" : "required_unsupported");
                throw new IllegalStateException("active required spell/action definitions must be approved and supported before adventure start");
            }
            try {
                packages.lockSpellDefinitionPreparationForStart(review.preparationId(), command.scenarioPackageId(),
                        command.ownerPlayerId().value(), command.sessionId().value(), review.revision());
            } catch (RuntimeException failure) {
                if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                    long actualRevision = failure instanceof com.dndmaster.adventure.domain.scenario.SpellDefinitionRevisionConflictException conflict
                            ? conflict.actualRevision() : review.revision();
                    LOGGER.info("dev_flow operation=adventure_start_spell_review outcome=failed sessionId={} preparationId={} expectedRevision={} actualRevision={} failureType={} reason=preparation_lock_failed recovery=retry_start_after_review_reload",
                            command.sessionId().value(), review.preparationId(), review.revision(), actualRevision,
                            failure.getClass().getSimpleName());
                }
                throw failure;
            }
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=adventure_start_spell_review outcome=locked sessionId={} preparationId={} expectedRevision={} actualRevision={} activeDefinitionCount={} actualPendingOptionalDefinitionCount={} reason=adventure_start",
                        command.sessionId().value(), review.preparationId(), review.revision(), review.revision(),
                        review.definitions().stream().filter(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition::active).count(),
                        pendingOptionalDefinitions);
            }
        }

        if (adventure == null) {
            adventure = Adventure.beginScenarioRuntime(command.adventureId(), command.sessionId(), command.ownerPlayerId(),
                    command.scenarioId(), command.ruleSetId(), command.scenarioPackageId(), command.scenarioPackageRevision(),
                    command.party(), command.initialContext());
            adventures.save(adventure);
        }

        var prepared = stagePreparation.prepare(adventure.id(), command.sessionId(), scenarioPackage.packageId());

        if (adventure.currentSituation() == null) {
            adventure.initializeScenarioRuntime(command.ownerPlayerId(),
                    com.dndmaster.adventure.domain.runtime.GameState.empty(),
                    com.dndmaster.adventure.domain.runtime.DisclosureState.empty(),
                    prepared.currentStage() == null
                            ? com.dndmaster.adventure.domain.runtime.CurrentSituation.initial(prepared.openingSituation().situationId())
                            : com.dndmaster.adventure.domain.runtime.CurrentSituation.fromOpeningStage(prepared.currentStage(), command.initialContext().currentScene()),
                    java.util.List.of(),
                    new com.dndmaster.adventure.domain.adventure.AdventureContext(
                            prepared.openingSituation().situationId(), null, null, null),
                    prepared.currentStage() == null ? null
                            : com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.start(prepared.currentStage()));
            adventures.save(adventure);
        }
        return adventure;
    }

    private static void logSpellReviewGate(StartAdventureCommand command, long pendingRequired, long pendingOptional,
            long unsupported, String reason) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_flow operation=adventure_start_spell_review outcome=blocked sessionId={} expected=active_required_approved_and_supported actualPendingRequired={} actualPendingOptional={} actualRequiredUnsupported={} reason={}",
                    command.sessionId().value(), pendingRequired, pendingOptional, unsupported, reason);
        }
    }

    private static SelectedRulebookSource selectedRulebookSource(ScenarioPackage scenarioPackage) {
        var blueprint = scenarioPackage.characterCreationBlueprint();
        if (blueprint != null && blueprint.provenance().selectedRulebookId() != null)
            return new SelectedRulebookSource(blueprint.provenance().selectedRulebookId(),
                    blueprint.provenance().selectedRulebookExtractionVersion());
        var sources = scenarioPackage.documents().stream()
                .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType())
                        && (document.status() == com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED
                            || document.status() == com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED_WITH_WARNINGS))
                .toList();
        return sources.size() == 1 ? new SelectedRulebookSource(sources.getFirst().knowledgeDocumentId().value(),
                sources.getFirst().extractionVersion()) : null;
    }

    private record SelectedRulebookSource(java.util.UUID documentId, long registrationVersion) {}

}
