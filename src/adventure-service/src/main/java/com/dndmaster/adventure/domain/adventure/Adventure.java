package com.dndmaster.adventure.domain.adventure;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Adventure {
    private final AdventureId id;
    private final SessionId sessionId;
    private final OwnerPlayerId ownerPlayerId;
    private final ScenarioId scenarioId;
    private final RuleSetId ruleSetId;
    private final List<AdventurePartyMember> party;
    private List<ConversationEntry> conversation;
    private AdventureContext currentContext;
    private AdventureStatus status;
    private long version;
    private int turnIndex;
    private String lastTurnKey;

    private Adventure(
            AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId, ScenarioId scenarioId,
            RuleSetId ruleSetId, List<AdventurePartyMember> party, List<ConversationEntry> conversation,
            AdventureContext currentContext, AdventureStatus status, long version, int turnIndex, String lastTurnKey) {
        this.id = Objects.requireNonNull(id, "adventure id must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        this.scenarioId = Objects.requireNonNull(scenarioId, "scenario id must not be null");
        this.ruleSetId = Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        this.party = List.copyOf(Objects.requireNonNull(party, "party must not be null"));
        if (this.party.isEmpty()) throw new IllegalArgumentException("party must not be empty");
        this.conversation = validateConversation(conversation);
        this.currentContext = Objects.requireNonNull(currentContext, "current context must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        if (turnIndex < 0 || turnIndex >= this.party.size()) throw new IllegalArgumentException("turn index out of range");
        this.turnIndex = turnIndex;
        this.lastTurnKey = lastTurnKey;
    }

    public static Adventure create(
            AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId, ScenarioId scenarioId,
            RuleSetId ruleSetId, CharacterSheetId characterSheetId, AdventureContext context) {
        return new Adventure(id, sessionId, ownerPlayerId, scenarioId, ruleSetId,
                List.of(new AdventurePartyMember(characterSheetId, ControlMode.DIRECT, true, true, true, true, true, true)),
                List.of(), context, AdventureStatus.SAVED, 0, 0, null);
    }
    public static Adventure create(AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId, ScenarioId scenarioId, RuleSetId ruleSetId, List<AdventurePartyMember> party, AdventureContext context) {
        if (party == null || party.isEmpty()) throw new IllegalArgumentException("party must not be empty");
        return new Adventure(id, sessionId, ownerPlayerId, scenarioId, ruleSetId, party, List.of(), context, AdventureStatus.SAVED, 0, 0, null);
    }

    public static Adventure rehydrate(
            AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId, ScenarioId scenarioId,
            RuleSetId ruleSetId, List<AdventurePartyMember> party, List<ConversationEntry> conversation,
            AdventureContext context, AdventureStatus status, long version) {
        return rehydrate(id, sessionId, ownerPlayerId, scenarioId, ruleSetId, party, conversation, context, status, version, 0, null);
    }
    public static Adventure rehydrate(
            AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId, ScenarioId scenarioId,
            RuleSetId ruleSetId, List<AdventurePartyMember> party, List<ConversationEntry> conversation,
            AdventureContext context, AdventureStatus status, long version, int turnIndex, String lastTurnKey) {
        return new Adventure(id, sessionId, ownerPlayerId, scenarioId, ruleSetId, party, conversation, context, status, version, turnIndex, lastTurnKey);
    }
    public void preserveProgress(
            OwnerPlayerId requestingOwner, long expectedVersion,
            AdventureContext context, List<ConversationEntry> completeConversation) {
        authorizeSaved(requestingOwner);
        requireExpectedVersion(expectedVersion);
        currentContext = Objects.requireNonNull(context, "current context must not be null");
        conversation = validateConversation(completeConversation);
        version++;
    }

    public void reopen(OwnerPlayerId requestingOwner) { authorizeSaved(requestingOwner); }

    public void advanceTurn(OwnerPlayerId requestingOwner, int expectedTurnIndex, CharacterSheetId characterSheetId, UUID turnId) {
        authorizeSaved(requestingOwner);
        String key = sessionId.value() + ":" + turnId + ":" + characterSheetId.value();
        if (key.equals(lastTurnKey)) return;
        if (turnIndex != expectedTurnIndex) throw new IllegalStateException("adventure turn cursor does not match");
        if (!party.get(turnIndex).characterSheetId().equals(characterSheetId)) throw new IllegalStateException("character is not current turn owner");
        turnIndex = (turnIndex + 1) % party.size();
        lastTurnKey = key;
        version++;
    }

    public void delete(OwnerPlayerId requestingOwner, long expectedVersion) {
        authorizeSaved(requestingOwner);
        requireExpectedVersion(expectedVersion);
        status = AdventureStatus.DELETED;
        version++;
    }

    private void authorizeSaved(OwnerPlayerId requestingOwner) {
        if (!ownerPlayerId.equals(Objects.requireNonNull(requestingOwner, "requesting owner must not be null"))) {
            throw new AdventureAccessDeniedException();
        }
        if (status == AdventureStatus.DELETED) throw new AdventureDeletedException();
    }

    private void requireExpectedVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("adventure version does not match");
    }

    private static List<ConversationEntry> validateConversation(List<ConversationEntry> entries) {
        List<ConversationEntry> copy = List.copyOf(Objects.requireNonNull(entries, "conversation must not be null"));
        var sequences = new HashSet<Long>();
        for (int index = 0; index < copy.size(); index++) {
            ConversationEntry entry = Objects.requireNonNull(copy.get(index), "conversation must not contain null");
            if (entry.sequence() != index || !sequences.add(entry.sequence())) {
                throw new IllegalArgumentException("conversation sequence must be unique and contiguous");
            }
        }
        return copy;
    }

    public AdventureId id() { return id; }
    public SessionId sessionId() { return sessionId; }
    public OwnerPlayerId ownerPlayerId() { return ownerPlayerId; }
    public ScenarioId scenarioId() { return scenarioId; }
    public RuleSetId ruleSetId() { return ruleSetId; }
    public List<AdventurePartyMember> party() { return party; }
    public List<ConversationEntry> conversation() { return conversation; }
    public AdventureContext currentContext() { return currentContext; }
    public AdventureStatus status() { return status; }
    public long version() { return version; }
    public int turnIndex() { return turnIndex; }
    public String lastTurnKey() { return lastTurnKey; }
}
