package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

public final class AdventureSessionApplicationService {
    private final AdventureSessionRepository repository;
    private final ScenarioPackageRepository packageRepository;
    private final AdventureRepository adventureRepository;
    private final RuntimeBindingApplicationService runtimeBindingService;
    private final AdventureSessionStartCoordinator startCoordinator;
    private final CharacterSheetOwnershipPort characterSheetOwnershipPort;
    private final AdventureStoryPlanRepository storyPlanRepository;
    private final SessionKnowledgeSetRepository sessionKnowledgeSetRepository;
    private final RuntimeTurnApplicationService runtimeTurnService;
    private final AiCompanionGenerationPort aiCompanionGenerationPort;
    private final AiCompanionSheetCreationPort aiCompanionSheetCreationPort;
    private static final CharacterSheetOwnershipPort MISSING_OWNERSHIP_PORT = (session, owner, sheet) -> { throw new IllegalStateException("character sheet ownership verifier is required"); };
    private static final AdventureStoryPlanRepository MISSING_STORY_PLAN_REPOSITORY = new AdventureStoryPlanRepository() { public java.util.Optional<com.dndmaster.adventure.domain.adventure.AdventureStoryPlan> findBySessionId(SessionId id) { return java.util.Optional.empty(); } public void save(com.dndmaster.adventure.domain.adventure.AdventureStoryPlan plan) {} };
    private static final SessionKnowledgeSetRepository MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY = new SessionKnowledgeSetRepository() { public java.util.Optional<SessionKnowledgeSet> findBySessionId(SessionId id) { return java.util.Optional.empty(); } public void save(SessionKnowledgeSet set) {} };
    private static final RuntimeTurnApplicationService MISSING_RUNTIME_TURN_SERVICE = null;
    private static final AiCompanionGenerationPort MISSING_AI_COMPANION_GENERATOR = (session, owner) -> { throw new IllegalStateException("AI companion generation is not configured"); };
    private static final AiCompanionSheetCreationPort MISSING_AI_COMPANION_SHEET_CREATOR = (session, owner, candidate) -> { throw new IllegalStateException("AI companion sheet creation is not configured"); };
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartOutboxRepository startOutboxRepository) { this(repository, packageRepository, adventureRepository, runtimeBindingService, new AdventureSessionStartCoordinator(startOutboxRepository), MISSING_OWNERSHIP_PORT, MISSING_STORY_PLAN_REPOSITORY, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_RUNTIME_TURN_SERVICE, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartOutboxRepository startOutboxRepository, CharacterSheetOwnershipPort ownershipPort) { this(repository, packageRepository, adventureRepository, runtimeBindingService, new AdventureSessionStartCoordinator(startOutboxRepository), ownershipPort, MISSING_STORY_PLAN_REPOSITORY, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_RUNTIME_TURN_SERVICE, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartOutboxRepository startOutboxRepository, CharacterSheetOwnershipPort ownershipPort, AdventureStoryPlanRepository storyPlanRepository) { this(repository, packageRepository, adventureRepository, runtimeBindingService, new AdventureSessionStartCoordinator(startOutboxRepository), ownershipPort, storyPlanRepository, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_RUNTIME_TURN_SERVICE, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, MISSING_OWNERSHIP_PORT, MISSING_STORY_PLAN_REPOSITORY, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_RUNTIME_TURN_SERVICE, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, characterSheetOwnershipPort, MISSING_STORY_PLAN_REPOSITORY, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_RUNTIME_TURN_SERVICE, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, AdventureStoryPlanRepository storyPlanRepository) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, characterSheetOwnershipPort, storyPlanRepository, MISSING_SESSION_KNOWLEDGE_SET_REPOSITORY, MISSING_RUNTIME_TURN_SERVICE, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, AdventureStoryPlanRepository storyPlanRepository, SessionKnowledgeSetRepository sessionKnowledgeSetRepository) { this(repository, packageRepository, adventureRepository, runtimeBindingService, startCoordinator, characterSheetOwnershipPort, storyPlanRepository, sessionKnowledgeSetRepository, MISSING_RUNTIME_TURN_SERVICE, MISSING_AI_COMPANION_GENERATOR, MISSING_AI_COMPANION_SHEET_CREATOR); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator, CharacterSheetOwnershipPort characterSheetOwnershipPort, AdventureStoryPlanRepository storyPlanRepository, SessionKnowledgeSetRepository sessionKnowledgeSetRepository, RuntimeTurnApplicationService runtimeTurnService, AiCompanionGenerationPort aiCompanionGenerationPort, AiCompanionSheetCreationPort aiCompanionSheetCreationPort) { this.repository = Objects.requireNonNull(repository); this.packageRepository = Objects.requireNonNull(packageRepository); this.adventureRepository = Objects.requireNonNull(adventureRepository); this.runtimeBindingService = Objects.requireNonNull(runtimeBindingService); this.startCoordinator = Objects.requireNonNull(startCoordinator); this.characterSheetOwnershipPort = Objects.requireNonNull(characterSheetOwnershipPort); this.storyPlanRepository = Objects.requireNonNull(storyPlanRepository); this.sessionKnowledgeSetRepository = Objects.requireNonNull(sessionKnowledgeSetRepository); this.runtimeTurnService = runtimeTurnService; this.aiCompanionGenerationPort = Objects.requireNonNull(aiCompanionGenerationPort); this.aiCompanionSheetCreationPort = Objects.requireNonNull(aiCompanionSheetCreationPort); }
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
        var scenarioPackage = packageRepository.findById(scenarioPackageId).orElseThrow(() -> new IllegalArgumentException("scenario package not found"));
        var blueprint = scenarioPackage.characterCreationBlueprint();
        requirePublishedBlueprint(scenarioPackage, blueprintId, blueprintRevision);
        int partySize = partySize(scenarioPackage.characterLimit(), requestedPartySize);
        AdventureSession session = AdventureSession.create(SessionId.generate(), owner, scenarioPackage.packageId(), scenarioPackage.bundleRevision(), blueprintId, blueprintRevision, blueprint.provenance().edition(), partySize,
                runtimeConfiguration == null ? defaultRuntimeConfiguration(scenarioPackage) : runtimeConfiguration);
        repository.save(session, 0); return session;
    }

    private static com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint requirePublishedBlueprint(
            com.dndmaster.adventure.domain.scenario.ScenarioPackage scenarioPackage, java.util.UUID blueprintId,
            long blueprintRevision) {
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE) {
            throw new IllegalStateException("scenario package is not compiled successfully");
        }
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
        AdventureSession session = authorize(load(id), owner);
        if (session.status() == AdventureSession.Status.STARTED && requestId.equals(session.startRequestId()) && adventureId.equals(session.startedAdventureId())) return session;
        if (session.status() == AdventureSession.Status.STARTING
                && (!requestId.equals(session.startRequestId()) || !adventureId.equals(session.startedAdventureId()))) {
            throw new IllegalStateException("adventure session is already starting with another request");
        }
        boolean resumingStart = session.status() == AdventureSession.Status.STARTING;
        if (!resumingStart) requireVersion(session, expectedVersion);
        var scenarioPackage = packageRepository.findById(session.scenarioPackageId()).orElseThrow(() -> new IllegalStateException("scenario package not found"));
        var blueprint = scenarioPackage.characterCreationBlueprint();
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE
                || scenarioPackage.bundleRevision() != session.scenarioPackageRevision()
                || (scenarioPackage.characterLimit().isExactPartySize() && scenarioPackage.characterLimit().maximumCharacters() != session.characterLimit())
                || blueprint == null
                || !blueprint.status().equals(com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.PUBLISHED)
                || !session.blueprintId().equals(session.scenarioPackageId())
                || blueprint.revision() != session.blueprintRevision()) throw new IllegalStateException("scenario package or blueprint changed since session draft");
        session.validateStart();
        var storyPlan = storyPlanRepository.findBySessionId(session.id())
                .orElseThrow(() -> new IllegalStateException("adventure story plan is required"));
        boolean recoveredStart = session.status() == AdventureSession.Status.DRAFT
                && session.startedAdventureId() != null
                && session.startedAdventureId().equals(adventureId)
                && session.startRequestId() != null;
        boolean partyLockMatches = session.status() == AdventureSession.Status.STARTING
                ? storyPlan.partyRevision() <= session.version()
                : recoveredStart
                ? storyPlan.partyRevision() == session.version() - 2
                : storyPlan.partyRevision() == session.version();
        if (storyPlan.status() != AdventureStoryPlanStatus.READY
                || storyPlan.packageRevision() != session.scenarioPackageRevision()
                || !partyLockMatches) {
            throw new IllegalStateException("adventure story plan is not ready for current party");
        }
        var configuration = session.runtimeConfiguration();
        if (configuration == null) throw new IllegalStateException("adventure session runtime configuration is required");
        boolean newlyStarting = session.beginStart(adventureId, requestId);
        if (newlyStarting) {
            repository.save(session, expectedVersion);
            startCoordinator.prepare(session.id(), requestId, adventureId.value(), session.scenarioPackageId());
        }
        Adventure adventure = adventureRepository.findById(adventureId).orElse(null);
        if (adventure == null) {
            adventure = Adventure.create(adventureId, session.id(), owner, configuration.scenarioId(), configuration.ruleSetId(), session.party(), new AdventureContext(configuration.initialScene(), null, null, null));
            adventureRepository.save(adventure);
        }
        initializeSessionKnowledgeSetIfMissing(session, scenarioPackage);
        runtimeBindingService.bindForSession(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(adventureId, owner, session.scenarioPackageId(), configuration.rulebookIds(), configuration.engineId(), configuration.toolIds()));
        if (runtimeTurnService != null) runtimeTurnService.openSessionTurn(adventureId, owner, requestId);
        if (session.status() == AdventureSession.Status.STARTING) {
            session.completeStart();
            repository.save(session, session.version() - 1);
            startCoordinator.commit(session.id(), requestId);
        }
        return session;
    }
    public AdventureSession complete(SessionId id, OwnerPlayerId owner, long expectedVersion) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion);
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
    public AdventureSession delete(SessionId id, OwnerPlayerId owner, long expectedVersion) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion);
        session.delete();
        repository.save(session, expectedVersion);
        return session;
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
