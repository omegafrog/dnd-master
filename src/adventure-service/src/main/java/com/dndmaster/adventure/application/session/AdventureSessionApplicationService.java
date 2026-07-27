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
import java.util.Objects;
import java.util.List;

public final class AdventureSessionApplicationService {
    private final AdventureSessionRepository repository;
    private final ScenarioPackageRepository packageRepository;
    private final AdventureRepository adventureRepository;
    private final RuntimeBindingApplicationService runtimeBindingService;
    private final AdventureSessionStartCoordinator startCoordinator;
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartOutboxRepository startOutboxRepository) { this(repository, packageRepository, adventureRepository, runtimeBindingService, new AdventureSessionStartCoordinator(startOutboxRepository)); }
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository, AdventureRepository adventureRepository, RuntimeBindingApplicationService runtimeBindingService, AdventureSessionStartCoordinator startCoordinator) { this.repository = Objects.requireNonNull(repository); this.packageRepository = Objects.requireNonNull(packageRepository); this.adventureRepository = Objects.requireNonNull(adventureRepository); this.runtimeBindingService = Objects.requireNonNull(runtimeBindingService); this.startCoordinator = Objects.requireNonNull(startCoordinator); }
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId) {
        return create(owner, scenarioPackageId, null);
    }
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId, AdventureSessionRuntimeConfiguration runtimeConfiguration) {
        var scenarioPackage = packageRepository.findById(scenarioPackageId).orElseThrow(() -> new IllegalArgumentException("scenario package not found"));
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE) throw new IllegalStateException("scenario package is not compiled successfully");
        if (scenarioPackage.characterCreationBlueprint() != null
                && scenarioPackage.characterCreationBlueprint().status() != com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.READY
                && scenarioPackage.characterCreationBlueprint().status() != com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.PUBLISHED) {
            throw new IllegalStateException("character creation blueprint requires review");
        }
        AdventureSession session = runtimeConfiguration == null
                ? AdventureSession.create(SessionId.generate(), owner, scenarioPackage.packageId(), scenarioPackage.bundleRevision(), scenarioPackage.characterLimit().maximumCharacters())
                : AdventureSession.create(SessionId.generate(), owner, scenarioPackage.packageId(), scenarioPackage.bundleRevision(), scenarioPackage.characterLimit().maximumCharacters(), runtimeConfiguration);
        repository.save(session, 0); return session;
    }
    public AdventureSession read(SessionId id, OwnerPlayerId owner) { return authorize(load(id), owner); }
    public AdventureSession readInternal(SessionId id) { return load(id); }
    public AdventureSession addMember(SessionId id, OwnerPlayerId owner, long expectedVersion, AdventurePartyMember member) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion); session.addPartyMember(member); repository.save(session, expectedVersion); return session;
    }
    public AdventureSession replaceMember(SessionId id, OwnerPlayerId owner, long expectedVersion, AdventurePartyMember member) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion); session.replacePartyMember(member); repository.save(session, expectedVersion); return session;
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
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE || scenarioPackage.bundleRevision() != session.scenarioPackageRevision() || scenarioPackage.characterLimit().maximumCharacters() != session.characterLimit()) throw new IllegalStateException("scenario package changed since session draft");
        session.validateStart();
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
        runtimeBindingService.bindForSession(new RuntimeBindingApplicationService.BindRuntimeBindingCommand(adventureId, owner, session.scenarioPackageId(), configuration.rulebookIds(), configuration.engineId(), configuration.toolIds()));
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
}
