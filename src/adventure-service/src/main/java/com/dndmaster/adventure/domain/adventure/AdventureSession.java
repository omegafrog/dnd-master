package com.dndmaster.adventure.domain.adventure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Draft session. Plan 021 owns transition to started/frozen state. */
public final class AdventureSession {
    public enum Status { DRAFT, STARTED }
    private final SessionId id;
    private final OwnerPlayerId ownerPlayerId;
    private final int characterLimit;
    private final UUID scenarioPackageId;
    private final long scenarioPackageRevision;
    private final List<AdventurePartyMember> party;
    private final AdventureSessionRuntimeConfiguration runtimeConfiguration;
    private Status status;
    private AdventureId startedAdventureId;
    private UUID startRequestId;
    private long version;

    private AdventureSession(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, Status status, AdventureId startedAdventureId, UUID startRequestId, long version) {
        this.id = Objects.requireNonNull(id, "session id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        this.scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (scenarioPackageRevision < 1) throw new IllegalArgumentException("scenario package revision must be positive");
        this.scenarioPackageRevision = scenarioPackageRevision;
        if (characterLimit < 1) throw new IllegalArgumentException("character limit must be positive");
        this.characterLimit = characterLimit;
        this.party = new ArrayList<>(Objects.requireNonNull(party, "party must not be null"));
        if (this.party.size() > characterLimit) throw new IllegalArgumentException("party exceeds character limit");
        this.runtimeConfiguration = runtimeConfiguration;
        this.status = Objects.requireNonNull(status, "session status must not be null");
        this.startedAdventureId = startedAdventureId;
        this.startRequestId = startRequestId;
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
    }

    public static AdventureSession create(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, characterLimit, List.of(), null, Status.DRAFT, null, null, 0);
    }

    public static AdventureSession create(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            AdventureSessionRuntimeConfiguration runtimeConfiguration) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, characterLimit, List.of(), Objects.requireNonNull(runtimeConfiguration, "runtime configuration must not be null"), Status.DRAFT, null, null, 0);
    }

    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, long version) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, characterLimit, party, null, Status.DRAFT, null, null, version);
    }

    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, long version) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, characterLimit, party, runtimeConfiguration, Status.DRAFT, null, null, version);
    }
    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, Status status, AdventureId startedAdventureId, UUID startRequestId, long version) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, characterLimit, party, runtimeConfiguration, status, startedAdventureId, startRequestId, version);
    }

    public void addPartyMember(AdventurePartyMember member) {
        requireDraft();
        Objects.requireNonNull(member, "party member must not be null");
        if (party.size() >= characterLimit) throw new IllegalStateException("party exceeds storybook character limit");
        if (party.stream().anyMatch(existing -> existing.characterSheetId().equals(member.characterSheetId()))) {
            throw new IllegalArgumentException("character sheet is already in party");
        }
        party.add(member);
        version++;
    }

    public void replacePartyMember(AdventurePartyMember member) {
        requireDraft();
        Objects.requireNonNull(member, "party member must not be null");
        int index = indexOf(member.characterSheetId());
        if (index < 0) throw new IllegalArgumentException("character sheet is not in party");
        party.set(index, member);
        version++;
    }

    public void removePartyMember(CharacterSheetId characterSheetId) {
        requireDraft();
        int index = indexOf(Objects.requireNonNull(characterSheetId, "character sheet id must not be null"));
        if (index < 0) throw new IllegalArgumentException("character sheet is not in party");
        party.remove(index);
        version++;
    }
    public AdventureSession start(AdventureId adventureId, UUID requestId) {
        Objects.requireNonNull(adventureId, "adventure id must not be null"); Objects.requireNonNull(requestId, "request id must not be null");
        if (status == Status.STARTED) {
            if (adventureId.equals(startedAdventureId) && requestId.equals(startRequestId)) return this;
            throw new IllegalStateException("adventure session is already started");
        }
        if (runtimeConfiguration == null) throw new IllegalStateException("adventure session runtime configuration is required");
        if (party.isEmpty()) throw new IllegalStateException("adventure session requires at least one party member");
        status = Status.STARTED; startedAdventureId = adventureId; startRequestId = requestId; version++; return this;
    }
    private void requireDraft() { if (status != Status.DRAFT) throw new IllegalStateException("started adventure session party is frozen"); }
    private int indexOf(CharacterSheetId characterSheetId) {
        for (int index = 0; index < party.size(); index++) if (party.get(index).characterSheetId().equals(characterSheetId)) return index;
        return -1;
    }
    public SessionId id() { return id; }
    public OwnerPlayerId ownerPlayerId() { return ownerPlayerId; }
    public int characterLimit() { return characterLimit; }
    public UUID scenarioPackageId() { return scenarioPackageId; }
    public long scenarioPackageRevision() { return scenarioPackageRevision; }
    public List<AdventurePartyMember> party() { return List.copyOf(party); }
    public AdventureSessionRuntimeConfiguration runtimeConfiguration() { return runtimeConfiguration; }
    public Status status() { return status; }
    public AdventureId startedAdventureId() { return startedAdventureId; }
    public UUID startRequestId() { return startRequestId; }
    public long version() { return version; }
}
