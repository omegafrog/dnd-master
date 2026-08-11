package com.dndmaster.adventure.domain.adventure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Draft session. Plan 021 owns transition to started/frozen state. */
public final class AdventureSession {
    public enum Status { DRAFT, STARTING, STARTED, COMPLETED, DELETED }
    private final SessionId id;
    private final OwnerPlayerId ownerPlayerId;
    private final int characterLimit;
    private final UUID scenarioPackageId;
    private final long scenarioPackageRevision;
    private final UUID blueprintId;
    private final long blueprintRevision;
    private final String characterEdition;
    private final List<AdventurePartyMember> party;
    private final AdventureSessionRuntimeConfiguration runtimeConfiguration;
    private Status status;
    private AdventureId startedAdventureId;
    private UUID startRequestId;
    private long version;

    private AdventureSession(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, String characterEdition, int characterLimit,
            List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, Status status, AdventureId startedAdventureId, UUID startRequestId, long version) {
        this.id = Objects.requireNonNull(id, "session id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        this.scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (scenarioPackageRevision < 1) throw new IllegalArgumentException("scenario package revision must be positive");
        this.scenarioPackageRevision = scenarioPackageRevision;
        this.blueprintId = Objects.requireNonNull(blueprintId, "blueprint id must not be null");
        if (blueprintRevision < 1) throw new IllegalArgumentException("blueprint revision must be positive");
        this.blueprintRevision = blueprintRevision;
        if (characterEdition == null || characterEdition.isBlank()) throw new IllegalArgumentException("character edition must not be blank");
        this.characterEdition = characterEdition;
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
        return create(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, scenarioPackageId, 1, characterLimit);
    }
    public static AdventureSession create(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, int characterLimit) {
        return create(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, blueprintId, blueprintRevision, "DND_5E_2014", characterLimit);
    }
    public static AdventureSession create(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, String characterEdition, int characterLimit) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, blueprintId, blueprintRevision, characterEdition, characterLimit, List.of(), null, Status.DRAFT, null, null, 0);
    }
    public static AdventureSession create(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, int characterLimit, AdventureSessionRuntimeConfiguration runtimeConfiguration) {
        return create(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, blueprintId, blueprintRevision, "DND_5E_2014", characterLimit, runtimeConfiguration);
    }
    public static AdventureSession create(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, String characterEdition, int characterLimit, AdventureSessionRuntimeConfiguration runtimeConfiguration) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, blueprintId, blueprintRevision, characterEdition, characterLimit, List.of(), Objects.requireNonNull(runtimeConfiguration, "runtime configuration must not be null"), Status.DRAFT, null, null, 0);
    }

    public static AdventureSession create(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            AdventureSessionRuntimeConfiguration runtimeConfiguration) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, scenarioPackageId, 1, "DND_5E_2014", characterLimit, List.of(), Objects.requireNonNull(runtimeConfiguration, "runtime configuration must not be null"), Status.DRAFT, null, null, 0);
    }

    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, long version) {
        return createRehydrated(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, scenarioPackageId, 1, "DND_5E_2014", characterLimit, party, null, Status.DRAFT, null, null, version);
    }

    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, long version) {
        return createRehydrated(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, scenarioPackageId, 1, "DND_5E_2014", characterLimit, party, runtimeConfiguration, Status.DRAFT, null, null, version);
    }
    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, Status status, AdventureId startedAdventureId, UUID startRequestId, long version) {
        return createRehydrated(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, scenarioPackageId, 1, "DND_5E_2014", characterLimit, party, runtimeConfiguration, status, startedAdventureId, startRequestId, version);
    }
    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, int characterLimit, List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, Status status, AdventureId startedAdventureId, UUID startRequestId, long version) {
        return createRehydrated(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, blueprintId, blueprintRevision, "DND_5E_2014", characterLimit, party, runtimeConfiguration, status, startedAdventureId, startRequestId, version);
    }
    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, String characterEdition, int characterLimit, List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, Status status, AdventureId startedAdventureId, UUID startRequestId, long version) {
        return createRehydrated(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, blueprintId, blueprintRevision, characterEdition, characterLimit, party, runtimeConfiguration, status, startedAdventureId, startRequestId, version);
    }
    private static AdventureSession createRehydrated(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, String characterEdition, int characterLimit, List<AdventurePartyMember> party, AdventureSessionRuntimeConfiguration runtimeConfiguration, Status status, AdventureId startedAdventureId, UUID startRequestId, long version) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, blueprintId, blueprintRevision, characterEdition, characterLimit, party, runtimeConfiguration, status, startedAdventureId, startRequestId, version);
    }

    public void addPartyMember(AdventurePartyMember member) {
        requireDraft();
        Objects.requireNonNull(member, "party member must not be null");
        if (party.size() >= characterLimit) {
            throw new IllegalStateException("party exceeds storybook character limit");
        }
        if (party.stream().anyMatch(existing -> existing.characterSheetId().equals(member.characterSheetId()))) {
            throw new IllegalArgumentException("character sheet is already in party");
        }
        party.add(member);
        version++;
    }

    /** Explicit candidate adoption is the only transition from AI proposal to party member. */
    public void adoptAiCompanion(AiCompanionCandidate candidate, CharacterSheetId characterSheetId, ControlMode controlMode) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        addPartyMember(new AdventurePartyMember(Objects.requireNonNull(characterSheetId), controlMode, false, false, false, false, false, false));
    }

    private int directPartySize() {
        return (int) party.stream().filter(member -> member.controlMode() == ControlMode.DIRECT).count();
    }

    public void replacePartyMember(AdventurePartyMember member) {
        requireDraft();
        Objects.requireNonNull(member, "party member must not be null");
        int index = indexOf(member.characterSheetId());
        if (index < 0) throw new IllegalArgumentException("character sheet is not in party");
        AdventurePartyMember current = party.get(index);
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
        if (!beginStart(adventureId, requestId)) return this;
        completeStart();
        return this;
    }
    public boolean beginStart(AdventureId adventureId, UUID requestId) {
        Objects.requireNonNull(adventureId, "adventure id must not be null"); Objects.requireNonNull(requestId, "request id must not be null");
        if (status == Status.STARTED || status == Status.STARTING) {
            if (adventureId.equals(startedAdventureId) && requestId.equals(startRequestId)) return false;
            throw new IllegalStateException("adventure session is already starting or started");
        }
        validateStart();
        status = Status.STARTING; startedAdventureId = adventureId; startRequestId = requestId; version++;
        return true;
    }
    public void completeStart() {
        if (status != Status.STARTING) throw new IllegalStateException("adventure session is not starting");
        status = Status.STARTED; version++;
    }

    public List<CharacterSheetId> complete() {
        requireStarted();
        status = Status.COMPLETED;
        version++;
        return party.stream().map(AdventurePartyMember::characterSheetId).toList();
    }

    public List<CharacterSheetId> delete() {
        if (status != Status.STARTED && status != Status.COMPLETED) {
            throw new IllegalStateException("only an active or completed adventure session can be deleted");
        }
        status = Status.DELETED;
        version++;
        return party.stream().map(AdventurePartyMember::characterSheetId).toList();
    }
    public void validateStart() {
        if (runtimeConfiguration == null) throw new IllegalStateException("adventure session runtime configuration is required");
        if (party.size() != characterLimit) throw new IllegalStateException("adventure session requires the configured party capacity");
        if (directPartySize() < 1) throw new IllegalStateException("adventure session requires the solo player's character");
    }
    private void requireDraft() { if (status != Status.DRAFT) throw new IllegalStateException("started adventure session party is frozen"); }
    private void requireStarted() { if (status != Status.STARTED) throw new IllegalStateException("adventure session is not started"); }
    private int indexOf(CharacterSheetId characterSheetId) {
        for (int index = 0; index < party.size(); index++) if (party.get(index).characterSheetId().equals(characterSheetId)) return index;
        return -1;
    }
    public SessionId id() { return id; }
    public OwnerPlayerId ownerPlayerId() { return ownerPlayerId; }
    public int characterLimit() { return characterLimit; }
    public UUID scenarioPackageId() { return scenarioPackageId; }
    public long scenarioPackageRevision() { return scenarioPackageRevision; }
    public UUID blueprintId() { return blueprintId; }
    public long blueprintRevision() { return blueprintRevision; }
    public String characterEdition() { return characterEdition; }
    public List<AdventurePartyMember> party() { return List.copyOf(party); }
    public AdventureSessionRuntimeConfiguration runtimeConfiguration() { return runtimeConfiguration; }
    public Status status() { return status; }
    public AdventureId startedAdventureId() { return startedAdventureId; }
    public UUID startRequestId() { return startRequestId; }
    public long version() { return version; }
}
