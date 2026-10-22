package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureStatus;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnResult;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.combat.CombatMapPreparationPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationPort;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class AdventureSessionApplicationService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AdventureSessionApplicationService.class);
    private final AdventureSessionRepository repository;
    private final ScenarioPackageRepository packageRepository;
    private final AdventureRepository adventureRepository;
    private final RuntimeBindingApplicationService runtimeBindingService;
    private final AdventureSessionStartCoordinator startCoordinator;
    private final CharacterSheetOwnershipPort characterSheetOwnershipPort;
    private final SessionKnowledgeSetRepository sessionKnowledgeSetRepository;
    private final AiCompanionGenerationPort aiCompanionGenerationPort;
    private final AiCompanionSheetCreationPort aiCompanionSheetCreationPort;
    private final CombatMapPreparationPort combatMapPreparationPort;
    private final StageArtifactPreparationPort stagePreparation;
    private final RuntimeTurnApplicationService runtimeTurnService;
    private final com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService combatLifecycleService;
    private com.dndmaster.adventure.application.ability.AdventureAbilityDefinitionPreparationService abilityDefinitionPreparationService;
    private com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService appliedRuleSetService;
    private com.dndmaster.adventure.dice.AdventureDicePorts initiativeDicePorts;
    private static final CharacterSheetOwnershipPort MISSING_OWNERSHIP_PORT = (session, owner, sheet) -> { throw new IllegalStateException("character sheet ownership verifier is required"); };
    private static final SessionKnowledgeSetRepository MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY = new SessionKnowledgeSetRepository() { public java.util.Optional<SessionKnowledgeSet> findBySessionId(SessionId id) { return java.util.Optional.empty(); } public void save(SessionKnowledgeSet set) {} };
    private static final AiCompanionGenerationPort MISSING_AI_COMPANION_GENERATOR = (session, owner) -> { throw new IllegalStateException("AI companion generation is not configured"); };
    private static final AiCompanionSheetCreationPort MISSING_AI_COMPANION_SHEET_CREATOR = (session, owner, candidate) -> { throw new IllegalStateException("AI companion sheet creation is not configured"); };
    private static final CombatMapPreparationPort MISSING_COMBAT_MAP_PREPARATION_PORT = (adventure, owner, rules, map, stage) -> null;
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartOutboxRepository startOutboxRepository) { this(repository, packageRepository, adventureRepository, runtimeBindingService, new AdventureSessionStartCoordinator(startOutboxRepository), MISSING_OWNERSHIP_PORT, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartOutboxRepository startOutboxRepository, CharacterSheetOwnershipPort ownershipPort) { this(repository, packageRepository, adventureRepository, runtimeBindingService, new AdventureSessionStartCoordinator(startOutboxRepository), ownershipPort, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, MISSING_OWNERSHIP_PORT, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, characterSheetOwnershipPort, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, characterSheetOwnershipPort, sessionKnowledgeSetRepository, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository, AiCompanionGenerationPort aiCompanionGenerationPort, AiCompanionSheetCreationPort aiCompanionSheetCreationPort) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, characterSheetOwnershipPort, sessionKnowledgeSetRepository, aiCompanionGenerationPort, aiCompanionSheetCreationPort, MISSING_COMBAT_MAP_PREPARATION_PORT); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository, AiCompanionGenerationPort aiCompanionGenerationPort, AiCompanionSheetCreationPort aiCompanionSheetCreationPort, CombatMapPreparationPort combatMapPreparationPort) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, characterSheetOwnershipPort, sessionKnowledgeSetRepository, aiCompanionGenerationPort, aiCompanionSheetCreationPort, combatMapPreparationPort, (adventureId, sessionId, scenarioPackageId) -> { throw new IllegalStateException("stage artifact preparation is not configured"); }); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository, AiCompanionGenerationPort aiCompanionGenerationPort, AiCompanionSheetCreationPort aiCompanionSheetCreationPort, CombatMapPreparationPort combatMapPreparationPort, StageArtifactPreparationPort stagePreparation) { this.repository = Objects.requireNonNull(repository); this.packageRepository = Objects.requireNonNull(packageRepository); this.adventureRepository = Objects.requireNonNull(adventureRepository); this.runtimeBindingService = Objects.requireNonNull(runtimeBindingService); this.startCoordinator = Objects.requireNonNull(startCoordinator); this.characterSheetOwnershipPort = Objects.requireNonNull(characterSheetOwnershipPort); this.sessionKnowledgeSetRepository = Objects.requireNonNull(sessionKnowledgeSetRepository); this.aiCompanionGenerationPort = Objects.requireNonNull(aiCompanionGenerationPort); this.aiCompanionSheetCreationPort = Objects.requireNonNull(aiCompanionSheetCreationPort); this.combatMapPreparationPort = Objects.requireNonNull(combatMapPreparationPort); this.stagePreparation = Objects.requireNonNull(stagePreparation); this.runtimeTurnService = null; this.combatLifecycleService = null; }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository, AiCompanionGenerationPort aiCompanionGenerationPort, AiCompanionSheetCreationPort aiCompanionSheetCreationPort, CombatMapPreparationPort combatMapPreparationPort, StageArtifactPreparationPort stagePreparation, RuntimeTurnApplicationService runtimeTurnService) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, characterSheetOwnershipPort, sessionKnowledgeSetRepository, aiCompanionGenerationPort, aiCompanionSheetCreationPort, combatMapPreparationPort, stagePreparation, runtimeTurnService, null); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository, AiCompanionGenerationPort aiCompanionGenerationPort, AiCompanionSheetCreationPort aiCompanionSheetCreationPort, CombatMapPreparationPort combatMapPreparationPort, StageArtifactPreparationPort stagePreparation, RuntimeTurnApplicationService runtimeTurnService, com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService combatLifecycleService) { this.repository = Objects.requireNonNull(repository); this.packageRepository = Objects.requireNonNull(packageRepository); this.adventureRepository = Objects.requireNonNull(adventureRepository); this.runtimeBindingService = Objects.requireNonNull(runtimeBindingService); this.startCoordinator = Objects.requireNonNull(startCoordinator); this.characterSheetOwnershipPort = Objects.requireNonNull(characterSheetOwnershipPort); this.sessionKnowledgeSetRepository = Objects.requireNonNull(sessionKnowledgeSetRepository); this.aiCompanionGenerationPort = Objects.requireNonNull(aiCompanionGenerationPort); this.aiCompanionSheetCreationPort = Objects.requireNonNull(aiCompanionSheetCreationPort); this.combatMapPreparationPort = Objects.requireNonNull(combatMapPreparationPort); this.stagePreparation = Objects.requireNonNull(stagePreparation); this.runtimeTurnService = runtimeTurnService; this.combatLifecycleService = combatLifecycleService; }
    public void setInitiativeDicePorts(com.dndmaster.adventure.dice.AdventureDicePorts dicePorts) {
        this.initiativeDicePorts = Objects.requireNonNull(dicePorts);
    }
    public void setAbilityDefinitionPreparationService(
            com.dndmaster.adventure.application.ability.AdventureAbilityDefinitionPreparationService service) {
        this.abilityDefinitionPreparationService = Objects.requireNonNull(service);
    }
    public void setAppliedRuleSetService(
            com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService service) {
        this.appliedRuleSetService = Objects.requireNonNull(service);
    }
    public boolean restoreAppliedRuleSet(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID expectedRuleSetId) {
        AdventureSession session = repository.findByStartedAdventureId(adventureId.value(), ownerPlayerId).orElse(null);
        if (session == null || session.runtimeConfiguration() == null || appliedRuleSetService == null
                || !session.runtimeConfiguration().ruleSetId().value().equals(expectedRuleSetId)) return false;
        persistAppliedRuleSet(session, adventureId);
        return true;
    }
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId) {
        return create(owner, scenarioPackageId, scenarioPackageId, 1, null);
    }
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId, AdventureSessionRuntimeConfiguration runtimeConfiguration) {
        var scenarioPackage = packageRepository.findById(scenarioPackageId).orElseThrow(() -> new IllegalArgumentException("scenario package not found"));
        var blueprint = requirePublishedBlueprint(scenarioPackage, scenarioPackageId,
                scenarioPackage.characterCreationBlueprint() == null ? 0 : scenarioPackage.characterCreationBlueprint().revision());
        return create(owner, scenarioPackageId, scenarioPackageId, blueprint.revision(), runtimeConfiguration, null);
    }
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId, java.util.UUID blueprintId, long blueprintRevision, AdventureSessionRuntimeConfiguration runtimeConfiguration) {
        return create(owner, scenarioPackageId, blueprintId, blueprintRevision, runtimeConfiguration, null);
    }
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId, java.util.UUID blueprintId, long blueprintRevision, AdventureSessionRuntimeConfiguration runtimeConfiguration, Integer requestedPartySize) {
        return create(owner, scenarioPackageId, blueprintId, blueprintRevision, runtimeConfiguration, requestedPartySize, null);
    }

    @Transactional
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId, java.util.UUID blueprintId,
            long blueprintRevision, AdventureSessionRuntimeConfiguration runtimeConfiguration, Integer requestedPartySize,
            java.util.UUID preparationId) {
        return create(owner, scenarioPackageId, blueprintId, blueprintRevision, runtimeConfiguration,
                requestedPartySize, preparationId, com.dndmaster.adventure.domain.adventure.StoryLength.NORMAL);
    }

    @Transactional
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId, java.util.UUID blueprintId,
            long blueprintRevision, AdventureSessionRuntimeConfiguration runtimeConfiguration, Integer requestedPartySize,
            java.util.UUID preparationId, com.dndmaster.adventure.domain.adventure.StoryLength storyLength) {
        Objects.requireNonNull(storyLength, "story length must not be null");
        var scenarioPackage = packageRepository.findById(scenarioPackageId).orElseThrow(() -> new IllegalArgumentException("scenario package not found"));
        var blueprint = scenarioPackage.characterCreationBlueprint();
        requirePublishedBlueprint(scenarioPackage, blueprintId, blueprintRevision);
        int partySize = partySize(scenarioPackage.characterLimit(), requestedPartySize);
        SessionId sessionId = SessionId.generate();
        com.dndmaster.adventure.domain.scenario.SpellDefinitionReviewPreparation review = null;
        if (preparationId != null) {
            review = packageRepository.findSpellDefinitionPreparation(preparationId)
                    .orElseThrow(() -> new IllegalStateException("spell/action review preparation not found"));
            if (!review.packageId().equals(scenarioPackageId) || !review.ownerId().equals(owner.value()))
                throw new SecurityException("spell/action review preparation does not belong to this owner and package");
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
                    LOGGER.info("dev_flow operation=spell_definition_preparation outcome=blocked preparationId={} expectedSourceDocumentId={} expectedRegistrationVersion={} actualDefinitionCount={} actualMismatchedSourceCount={} reason=review_definitions_from_unselected_source recovery=reload_review_from_selected_rulebook",
                            preparationId, selectedSource.documentId(), selectedSource.registrationVersion(),
                            review.definitions().size(), mismatchedSourceDefinitions);
                }
                throw new IllegalStateException("spell/action review definitions do not match the selected rulebook source");
            }
            if (pendingDefinitions > 0 || requiredUnsupported > 0) {
                logPreparationGate(preparationId, pendingDefinitions, pendingOptionalDefinitions, requiredUnsupported,
                        pendingDefinitions > 0 ? "pending_review" : "required_unsupported");
                throw new IllegalStateException("active required spell/action definitions must be approved and supported before session creation");
            }
        }
        AdventureSession session = AdventureSession.create(sessionId, owner, scenarioPackage.packageId(), scenarioPackage.bundleRevision(), blueprintId, blueprintRevision, blueprint.provenance().edition(), partySize,
                runtimeConfiguration == null ? defaultRuntimeConfiguration(scenarioPackage) : runtimeConfiguration, storyLength);
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=adventure_session_create outcome=started sessionId={} packageId={} preparationId={} storyLength={} expectedVersion=0 expectedPartySize={} reason=session_persist_and_preparation_bind",
                    sessionId.value(), scenarioPackageId, preparationId, storyLength, partySize);
        }
        try {
            repository.save(session, 0);
        } catch (RuntimeException failure) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=adventure_session_create outcome=failed sessionId={} packageId={} preparationId={} expectedVersion=0 actualVersion={} failureType={} reason=session_persist_failed recovery=retry_session_creation",
                        sessionId.value(), scenarioPackageId, preparationId, session.version(), failure.getClass().getSimpleName());
            }
            throw failure;
        }
        if (preparationId != null) {
            try {
                packageRepository.bindSpellDefinitionPreparation(preparationId, scenarioPackageId, owner.value(),
                        sessionId.value(), review.revision());
            } catch (RuntimeException failure) {
                if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                    String actualRevision = failure instanceof com.dndmaster.adventure.domain.scenario.SpellDefinitionRevisionConflictException conflict
                            ? Long.toString(conflict.actualRevision()) : "unknown";
                    LOGGER.info("dev_flow operation=adventure_session_create outcome=failed sessionId={} packageId={} preparationId={} expectedPreparationId={} actualPreparationId={} expectedPackageId={} actualPackageId=unconfirmed expectedOwnerId={} actualOwnerId=unconfirmed expectedSessionId={} actualSessionId=unconfirmed expectedRevision={} actualRevision={} failureType={} reason=preparation_bind_failed recovery=retry_after_preparation_reload",
                            sessionId.value(), scenarioPackageId, preparationId, preparationId, "unconfirmed",
                            scenarioPackageId, owner.value(), sessionId.value(), review.revision(), actualRevision,
                            failure.getClass().getSimpleName());
                }
                throw failure;
            }
        }
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            long pendingOptionalDefinitions = review == null ? 0 : review.definitions().stream()
                    .filter(com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition::active)
                    .filter(definition -> !definition.required())
                    .filter(definition -> definition.reviewStatus()
                            != com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.ReviewStatus.APPROVED)
                    .count();
            LOGGER.info("dev_flow operation=adventure_session_create outcome=completed sessionId={} packageId={} preparationId={} actualVersion={} actualPartySize={} actualPendingOptionalDefinitionCount={} reason=session_persisted_and_preparation_bound",
                    sessionId.value(), scenarioPackageId, preparationId, session.version(), session.party().size(), pendingOptionalDefinitions);
        }
        return session;
    }

    private static void logPreparationGate(java.util.UUID preparationId, long pendingRequired, long pendingOptional,
            long requiredUnsupported, String reason) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            org.slf4j.LoggerFactory.getLogger(AdventureSessionApplicationService.class).info(
                    "dev_flow operation=spell_definition_preparation outcome=blocked preparationId={} expected=active_required_approved_and_supported actualPendingRequired={} actualPendingOptional={} actualRequiredUnsupported={} reason={}",
                    preparationId, pendingRequired, pendingOptional, requiredUnsupported, reason);
        }
    }

    private static SelectedRulebookSource selectedRulebookSource(com.dndmaster.adventure.domain.scenario.ScenarioPackage scenarioPackage) {
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

    private record SelectedRulebookSource(UUID documentId, long registrationVersion) {}

    private static com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint requirePublishedBlueprint(
            com.dndmaster.adventure.domain.scenario.ScenarioPackage scenarioPackage, java.util.UUID blueprintId,
            long blueprintRevision) {
        var blueprint = scenarioPackage.characterCreationBlueprint();
        if (blueprint == null || !blueprintId.equals(scenarioPackage.packageId())
                || blueprint.status() != com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.PUBLISHED
                || blueprint.revision() != blueprintRevision) {
            throw new IllegalStateException("character creation requires the published blueprint revision");
        }
        return blueprint;
    }
    public AdventureSession read(SessionId id, OwnerPlayerId owner) { return authorize(load(id), owner); }
    public List<AdventureSession> listByScenarioPackageId(java.util.UUID scenarioPackageId, OwnerPlayerId owner) {
        return repository.findByScenarioPackageId(scenarioPackageId).stream().filter(session -> session.ownerPlayerId().equals(owner)).toList();
    }
    public AdventureSession readInternal(SessionId id) { return load(id); }
    private void initializeSessionKnowledgeSetIfMissing(AdventureSession session, com.dndmaster.adventure.domain.scenario.ScenarioPackage scenarioPackage) {
        if (sessionKnowledgeSetRepository.findBySessionId(session.id()).isPresent()) return;
        sessionKnowledgeSetRepository.save(new SessionKnowledgeSet(session.id(), scenarioPackage.documents().stream()
                .map(document -> document.knowledgeDocumentId())
                .distinct()
                .toList()));
    }
    public AdventureSession addMember(SessionId id, OwnerPlayerId owner, long expectedVersion, AdventurePartyMember member) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion); characterSheetOwnershipPort.verify(session.id(), owner, member.characterSheetId()); session.addPartyMember(member); repository.save(session, expectedVersion); return session;
    }
    public com.dndmaster.adventure.domain.adventure.AiCompanionCandidate generateAiCandidate(SessionId id, OwnerPlayerId owner) {
        AdventureSession session = authorize(load(id), owner);
        if (session.status() != AdventureSession.Status.DRAFT) throw new IllegalStateException("party is frozen");
        if (session.party().size() >= session.characterLimit()) throw new IllegalStateException("party is already full");
        return aiCompanionGenerationPort.generate(session.id(), owner);
    }
    public AdventureSession adoptAiCandidate(SessionId id, OwnerPlayerId owner, long expectedVersion,
                                              com.dndmaster.adventure.domain.adventure.AiCompanionCandidate candidate,
                                              com.dndmaster.adventure.domain.adventure.ControlMode controlMode) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion);
        var sheetId = aiCompanionSheetCreationPort.create(session.id(), owner, candidate);
        characterSheetOwnershipPort.verify(session.id(), owner, sheetId);
        session.adoptAiCompanion(candidate, sheetId, controlMode); repository.save(session, expectedVersion); return session;
    }
    public AdventureSession replaceMember(SessionId id, OwnerPlayerId owner, long expectedVersion, AdventurePartyMember member) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion); characterSheetOwnershipPort.verify(session.id(), owner, member.characterSheetId()); session.replacePartyMember(member); repository.save(session, expectedVersion); return session;
    }
    public AdventureSession removeMember(SessionId id, OwnerPlayerId owner, long expectedVersion, com.dndmaster.adventure.domain.adventure.CharacterSheetId sheetId) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion); session.removePartyMember(sheetId); repository.save(session, expectedVersion); return session;
    }
    public AdventureSession start(SessionId id, OwnerPlayerId owner, long expectedVersion, java.util.UUID requestId, AdventureId adventureId) {
        return start(id, owner, expectedVersion, requestId, adventureId, false);
    }

    /** Prepares the map draft before the owner confirms the runtime start. */
    public AdventureSession start(SessionId id, OwnerPlayerId owner, long expectedVersion, java.util.UUID requestId, AdventureId adventureId, boolean prepareMapOnly) {
        String context = "sessionId=" + id.value() + " adventureId=" + adventureId.value()
                + " requestId=" + requestId + " prepareMapOnly=" + prepareMapOnly;
        return com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "session_start_service", context,
                () -> startInternal(id, owner, expectedVersion, requestId, adventureId, prepareMapOnly));
    }

    private AdventureSession startInternal(SessionId id, OwnerPlayerId owner, long expectedVersion,
            java.util.UUID requestId, AdventureId adventureId, boolean prepareMapOnly) {
        String context = "sessionId=" + id.value() + " adventureId=" + adventureId.value()
                + " requestId=" + requestId;
        AdventureSession session = authorize(load(id), owner);
        if (session.status() == AdventureSession.Status.DELETED) {
            throw new IllegalStateException("deleted adventure session cannot be started");
        }
        if (session.status() == AdventureSession.Status.STARTED) {
            if (adventureId.equals(session.startedAdventureId())) {
                persistAppliedRuleSet(session, session.startedAdventureId());
                return session;
            }
            throw new IllegalStateException("adventure session is already started with another adventure");
        }
        if (session.status() == AdventureSession.Status.STARTING) {
            // The browser can lose its in-memory idempotency key after a reload.
            // A STARTING session already owns one durable adventure and request;
            // resume that transaction instead of creating a second start attempt.
            if (session.startedAdventureId() == null || session.startRequestId() == null) {
                throw new IllegalStateException("adventure session start recovery data is missing");
            }
            adventureId = session.startedAdventureId();
            requestId = session.startRequestId();
        }
        boolean resumingStart = session.status() == AdventureSession.Status.STARTING;
        java.util.UUID effectiveRequestId = requestId;
        if (!resumingStart) requireVersion(session, expectedVersion);
        var scenarioPackage = packageRepository.findById(session.scenarioPackageId()).orElseThrow(() -> new IllegalStateException("scenario package not found"));
        if (scenarioPackage.scenarioModel() == null || !scenarioPackage.isReady()) {
            throw new IllegalStateException("scenario package is not READY");
        }
        if (scenarioPackage.bundleRevision() != session.scenarioPackageRevision()) {
            throw new IllegalStateException("scenario package revision does not match session draft");
        }
        session.validateStart();
        var configuration = session.runtimeConfiguration();
        if (configuration == null) throw new IllegalStateException("adventure session runtime configuration is required");
        // A retried browser request can generate a new adventure id after a
        // previous attempt already persisted the adventure. Reuse the
        // session-owned row so the unique session constraint remains intact.
        Adventure persistedAdventure = adventureRepository.findById(adventureId)
                .or(() -> adventureRepository.findBySessionId(session.id()))
                .orElse(null);
        AdventureId effectiveAdventureId = persistedAdventure == null ? adventureId : persistedAdventure.id();
        persistAppliedRuleSet(session, effectiveAdventureId);
        boolean newlyStarting = session.beginStart(effectiveAdventureId, requestId);
        if (newlyStarting) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    LOGGER, "session_start_persist_starting", context + " newlyStarting=true", () -> {
                        repository.save(session, expectedVersion);
                        startCoordinator.prepare(session.id(), effectiveRequestId, effectiveAdventureId.value(), session.scenarioPackageId());
                    });
        }
        Adventure adventure = persistedAdventure;
        if (adventure == null) {
            adventure = Adventure.beginScenarioRuntime(effectiveAdventureId, session.id(), owner, configuration.scenarioId(), configuration.ruleSetId(),
                    session.scenarioPackageId(), scenarioPackage.bundleRevision(), session.party(), new AdventureContext(configuration.initialScene(), null, null, null));
            Adventure newAdventure = adventure;
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    LOGGER, "session_start_persist_adventure", context, () -> adventureRepository.save(newAdventure));
        }
        var preparedStage = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "session_start_stage_artifacts", context,
                () -> stagePreparation.prepare(effectiveAdventureId, session.id(), scenarioPackage.packageId(), session.storyLength()));
        if (adventure.currentSituation() == null) {
            adventure.initializeScenarioRuntime(owner,
                    com.dndmaster.adventure.domain.runtime.GameState.empty(),
                    com.dndmaster.adventure.domain.runtime.DisclosureState.empty(),
                    preparedStage.currentStage() == null
                            ? com.dndmaster.adventure.domain.runtime.CurrentSituation.initial(preparedStage.openingSituation().situationId())
                            : com.dndmaster.adventure.domain.runtime.CurrentSituation.fromOpeningStage(preparedStage.currentStage(), configuration.initialScene()),
                    List.of(), new AdventureContext(preparedStage.openingSituation().situationId(), null, null, null),
                    preparedStage.currentStage() == null ? null
                            : com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.start(preparedStage.currentStage(),
                                    preparedStage.detailedStages(), session.storyLength(), preparedStage.backbone().requiredSituations().size()));
            Adventure initializedAdventure = adventure;
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    LOGGER, "session_start_initialize_adventure", context,
                    () -> adventureRepository.save(initializedAdventure));
        }
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "session_start_knowledge_set", context,
                () -> initializeSessionKnowledgeSetIfMissing(session, scenarioPackage));
        if (abilityDefinitionPreparationService != null) {
            Adventure abilityPrepAdventure = adventure;
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(LOGGER,
                    "session_start_ability_definitions", context,
                    () -> abilityDefinitionPreparationService.prepareAtAdventureStart(abilityPrepAdventure));
        }
        if (prepareMapOnly) {
            Adventure draftAdventure = adventure;
            var initialMapDefinition = scenarioPackage.initialMapDefinition(configuration.initialScene());
            initialMapDefinition.ifPresent(mapDefinition -> {
                var situation = draftAdventure.currentSituation();
                var draftContext = new CombatMapPreparationPort.ActivationContext(null, situation.situationId(),
                        situation.revision(), draftAdventure.turnIndex(), configuration.initialScene(),
                        situation.location(), null, null, situation.firstNarration());
                com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                        LOGGER, "session_start_prepare_map_draft", context + " mapDefinitionId=" + mapDefinition.id(),
                        () -> combatMapPreparationPort.prepareDraft(effectiveAdventureId, owner.value(),
                                configuration.ruleSetId(), mapDefinition, draftContext));
            });
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=session_start_map_preparation outcome=completed sessionId={} adventureId={} availableMapCount={} preparedMapCount={} actualStatus={} nextAction=owner_map_review",
                        id.value(), effectiveAdventureId.value(), scenarioPackage.mapDefinitions().size(),
                        initialMapDefinition.isPresent() ? 1 : 0, session.status());
            }
            return session;
        }
        boolean mapLayoutConfirmed = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "session_start_layout_confirmation_check", context,
                () -> combatMapPreparationPort.mapLayoutConfirmed(effectiveAdventureId, owner.value()));
        if (!mapLayoutConfirmed) {
            throw new IllegalStateException("맵 초안을 먼저 저장해야 모험을 시작할 수 있습니다.");
        }
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "session_start_runtime_binding", context,
                () -> runtimeBindingService.bindForSession(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(
                        effectiveAdventureId, owner, session.scenarioPackageId(), configuration.rulebookIds(),
                        configuration.engineId(), configuration.toolIds())));
        RuntimeTurnResult openingResult = runtimeTurnService == null
                ? null
                : com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                        LOGGER, "session_start_opening_gm_turn", context + " adventureId=" + effectiveAdventureId.value(),
                        () -> runtimeTurnService.openSessionTurn(effectiveAdventureId, owner, effectiveRequestId));
        if (openingResult != null && openingResult.turn().plan().combatStartRequested()) {
            if (combatLifecycleService == null) {
                throw new IllegalStateException("combat lifecycle is not configured for session opening");
            }
            if (!openingResult.turn().committed()) {
                throw new IllegalStateException("session-opening combat requires a committed GM turn");
            }
            Adventure committedAdventure = adventureRepository.findById(effectiveAdventureId).orElseThrow(
                    () -> new IllegalStateException("adventure disappeared after opening turn commit"));
            var enemies = openingResult.turn().plan().combatEnemies();
            com.dndmaster.adventure.application.combat.CombatStartTransitionPolicy
                    .requireCommittedCombatSituation(committedAdventure.currentSituation(), enemies);
            var participants = com.dndmaster.adventure.application.combat.CombatStartParticipantFactory
                    .fromPartyAndGmProposal(effectiveAdventureId.value(), session.party(), enemies,
                            member -> member.characterSheetId().value().toString(), ignored -> 0, ignored -> 30,
                            (participantId, scope) -> initiativeDicePorts.rollInitiative(effectiveAdventureId.value(),
                                    committedAdventure.ruleSetId().value(), session.id().value(), effectiveRequestId,
                                    participantId, committedAdventure.version(), scope));
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    LOGGER, "session_start_opening_combat", context + " enemyCount=" + enemies.size(), () -> {
                        var encounter = combatLifecycleService.startFromCommittedGmTurn(effectiveAdventureId.value(), true,
                                participants);
                        if (encounter != null) combatLifecycleService.scheduleFirstAiTurn(encounter, effectiveRequestId);
                    });
        }
        if (session.status() == AdventureSession.Status.STARTING) {
            session.completeStart();
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                    LOGGER, "session_start_commit", context + " adventureId=" + effectiveAdventureId.value(), () -> {
                        repository.save(session, session.version() - 1);
                        startCoordinator.commit(session.id(), effectiveRequestId);
                    });
        }
        return session;
    }

    private void persistAppliedRuleSet(AdventureSession session, AdventureId adventureId) {
        var configuration = session.runtimeConfiguration();
        if (appliedRuleSetService == null || configuration == null) return;
        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(
                LOGGER, "session_start_applied_rule_set", "sessionId=" + session.id().value()
                        + " adventureId=" + adventureId.value()
                        + " rulebookCount=" + configuration.rulebookIds().size(),
                () -> appliedRuleSetService.saveRuleSet(
                        new com.dndmaster.adventure.domain.ruleset.RuleSetId(configuration.ruleSetId().value()),
                        new com.dndmaster.adventure.application.ruleset.CreateAppliedRuleSetCommand(
                                new com.dndmaster.adventure.domain.ruleset.AdventureId(adventureId.value()),
                                new com.dndmaster.adventure.domain.ruleset.OwnerPlayerId(session.ownerPlayerId().value()),
                                new com.dndmaster.adventure.domain.ruleset.DndEdition(session.characterEdition()),
                                configuration.rulebookIds().stream()
                                        .map(com.dndmaster.adventure.domain.ruleset.RulebookId::new).toList())));
    }

    public AdventureSession complete(SessionId id, OwnerPlayerId owner, long expectedVersion) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion);
        Adventure linkedAdventure = (session.startedAdventureId() == null
                ? java.util.Optional.<Adventure>empty()
                : adventureRepository.findById(session.startedAdventureId()))
                .or(() -> adventureRepository.findBySessionId(session.id()))
                .orElseThrow(() -> new IllegalStateException("session cannot be completed before its adventure exists"));
        if (linkedAdventure.status() != AdventureStatus.COMPLETED) {
            throw new IllegalStateException("session can be completed only after the adventure has a committed conclusion");
        }
        if (session.status() == AdventureSession.Status.COMPLETED) return session;
        session.complete();
        repository.save(session, expectedVersion);
        return session;
    }
    public AdventureSession recoverFailedStart(SessionId id, OwnerPlayerId owner, long expectedVersion) {
        AdventureSession session = authorize(load(id), owner);
        requireVersion(session, expectedVersion);
        session.recoverFailedStart();
        repository.save(session, expectedVersion);
        return session;
    }
    @Transactional
    public AdventureSession delete(SessionId id, OwnerPlayerId owner, long expectedVersion) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion);
        retireStartedAdventure(session, owner);
        session.delete();
        repository.save(session, expectedVersion);
        return session;
    }
    private void retireStartedAdventure(AdventureSession session, OwnerPlayerId owner) {
        var adventure = session.startedAdventureId() == null
                ? adventureRepository.findBySessionId(session.id())
                : adventureRepository.findById(session.startedAdventureId()).or(() -> adventureRepository.findBySessionId(session.id()));
        adventure.filter(value -> value.status() != AdventureStatus.DELETED).ifPresent(value -> {
            value.delete(owner, value.version());
            adventureRepository.save(value);
        });
    }
    private AdventureSession load(SessionId id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("adventure session not found")); }
    private static AdventureSession authorize(AdventureSession session, OwnerPlayerId owner) {
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        return session;
    }
    private static void requireVersion(AdventureSession session, long expectedVersion) { if (session.version() != expectedVersion) throw new IllegalStateException("adventure session version does not match"); }

    private static int partySize(com.dndmaster.adventure.domain.scenario.CharacterLimit limit, Integer requestedPartySize) {
        if (limit.isExactPartySize()) {
            if (requestedPartySize != null && requestedPartySize != limit.maximumCharacters()) {
                throw new IllegalArgumentException("storybook requires exactly " + limit.maximumCharacters() + " party members");
            }
            return limit.maximumCharacters();
        }
        int selected = requestedPartySize == null ? Math.min(4, limit.maximumCharacters()) : requestedPartySize;
        if (selected < 1 || selected > limit.maximumCharacters()) {
            throw new IllegalArgumentException("party size must be between 1 and " + limit.maximumCharacters());
        }
        return selected;
    }

    private static AdventureSessionRuntimeConfiguration defaultRuntimeConfiguration(com.dndmaster.adventure.domain.scenario.ScenarioPackage scenarioPackage) {
        List<UUID> rulebookIds = scenarioPackage.documents().stream()
                .filter(document -> document.role() == com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.RULEBOOK)
                .map(document -> document.knowledgeDocumentId().value())
                .distinct()
                .toList();
        return new AdventureSessionRuntimeConfiguration(
                new com.dndmaster.adventure.domain.adventure.ScenarioId(scenarioPackage.packageId()),
                new com.dndmaster.adventure.domain.adventure.RuleSetId(scenarioPackage.bundleId().value()),
                rulebookIds,
                "ollama",
                List.of("search", "move"),
                "opening");
    }
}
