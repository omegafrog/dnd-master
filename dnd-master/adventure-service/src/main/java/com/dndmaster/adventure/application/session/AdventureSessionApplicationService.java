package com.dndmaster.adventure.application.session;

import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import java.util.Objects;

public final class AdventureSessionApplicationService {
    private final AdventureSessionRepository repository;
    private final ScenarioPackageRepository packageRepository;
    public AdventureSessionApplicationService(AdventureSessionRepository repository, ScenarioPackageRepository packageRepository) { this.repository = Objects.requireNonNull(repository); this.packageRepository = Objects.requireNonNull(packageRepository); }
    public AdventureSession create(OwnerPlayerId owner, java.util.UUID scenarioPackageId) {
        var scenarioPackage = packageRepository.findById(scenarioPackageId).orElseThrow(() -> new IllegalArgumentException("scenario package not found"));
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE) throw new IllegalStateException("scenario package is not compiled successfully");
        AdventureSession session = AdventureSession.create(SessionId.generate(), owner, scenarioPackage.packageId(), scenarioPackage.bundleRevision(), scenarioPackage.characterLimit().maximumCharacters());
        repository.save(session, 0); return session;
    }
    public AdventureSession read(SessionId id, OwnerPlayerId owner) { return authorize(load(id), owner); }
    public AdventureSession addMember(SessionId id, OwnerPlayerId owner, long expectedVersion, AdventurePartyMember member) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion); session.addPartyMember(member); repository.save(session, expectedVersion); return session;
    }
    public AdventureSession replaceMember(SessionId id, OwnerPlayerId owner, long expectedVersion, AdventurePartyMember member) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion); session.replacePartyMember(member); repository.save(session, expectedVersion); return session;
    }
    public AdventureSession removeMember(SessionId id, OwnerPlayerId owner, long expectedVersion, com.dndmaster.adventure.domain.adventure.CharacterSheetId sheetId) {
        AdventureSession session = authorize(load(id), owner); requireVersion(session, expectedVersion); session.removePartyMember(sheetId); repository.save(session, expectedVersion); return session;
    }
    private AdventureSession load(SessionId id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("adventure session not found")); }
    private static AdventureSession authorize(AdventureSession session, OwnerPlayerId owner) {
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        return session;
    }
    private static void requireVersion(AdventureSession session, long expectedVersion) { if (session.version() != expectedVersion) throw new IllegalStateException("adventure session version does not match"); }
}
