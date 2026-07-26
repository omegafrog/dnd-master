package com.dndmaster.adventure.domain.adventure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Draft session. Plan 021 owns transition to started/frozen state. */
public final class AdventureSession {
    private final SessionId id;
    private final OwnerPlayerId ownerPlayerId;
    private final int characterLimit;
    private final UUID scenarioPackageId;
    private final long scenarioPackageRevision;
    private final List<AdventurePartyMember> party;
    private long version;

    private AdventureSession(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, long version) {
        this.id = Objects.requireNonNull(id, "session id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        this.scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (scenarioPackageRevision < 1) throw new IllegalArgumentException("scenario package revision must be positive");
        this.scenarioPackageRevision = scenarioPackageRevision;
        if (characterLimit < 1) throw new IllegalArgumentException("character limit must be positive");
        this.characterLimit = characterLimit;
        this.party = new ArrayList<>(Objects.requireNonNull(party, "party must not be null"));
        if (this.party.size() > characterLimit) throw new IllegalArgumentException("party exceeds character limit");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
    }

    public static AdventureSession create(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, characterLimit, List.of(), 0);
    }

    public static AdventureSession rehydrate(SessionId id, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId, long scenarioPackageRevision, int characterLimit,
            List<AdventurePartyMember> party, long version) {
        return new AdventureSession(id, ownerPlayerId, scenarioPackageId, scenarioPackageRevision, characterLimit, party, version);
    }

    public void addPartyMember(AdventurePartyMember member) {
        Objects.requireNonNull(member, "party member must not be null");
        if (party.size() >= characterLimit) throw new IllegalStateException("party exceeds storybook character limit");
        if (party.stream().anyMatch(existing -> existing.characterSheetId().equals(member.characterSheetId()))) {
            throw new IllegalArgumentException("character sheet is already in party");
        }
        party.add(member);
        version++;
    }

    public void replacePartyMember(AdventurePartyMember member) {
        Objects.requireNonNull(member, "party member must not be null");
        int index = indexOf(member.characterSheetId());
        if (index < 0) throw new IllegalArgumentException("character sheet is not in party");
        party.set(index, member);
        version++;
    }

    public void removePartyMember(CharacterSheetId characterSheetId) {
        int index = indexOf(Objects.requireNonNull(characterSheetId, "character sheet id must not be null"));
        if (index < 0) throw new IllegalArgumentException("character sheet is not in party");
        party.remove(index);
        version++;
    }

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
    public long version() { return version; }
}
