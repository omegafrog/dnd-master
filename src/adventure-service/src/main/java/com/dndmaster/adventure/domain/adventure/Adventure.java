package com.dndmaster.adventure.domain.adventure;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameState;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import com.dndmaster.adventure.application.runtime.CompletionProposal;
import com.dndmaster.adventure.application.runtime.PendingRuntimeState;
import java.util.Locale;

public final class Adventure {
    private final AdventureId id;
    private final SessionId sessionId;
    private final OwnerPlayerId ownerPlayerId;
    private final ScenarioId scenarioId;
    private final RuleSetId ruleSetId;
    private final List<AdventurePartyMember> party;
    private UUID lockedScenarioPackageId;
    private long lockedScenarioPackageRevision;
    private GameState gameState;
    private DisclosureState disclosureState;
    private CurrentSituation currentSituation;
    private List<RuntimeAddedFact> runtimeAddedFacts;
    private List<ConversationEntry> conversation;
    private AdventureContext currentContext;
    private AdventureStatus status;
    private long version;
    private int turnIndex;
    private String lastTurnKey;

    private Adventure(
            AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId, ScenarioId scenarioId,
            RuleSetId ruleSetId, List<AdventurePartyMember> party, List<ConversationEntry> conversation,
            AdventureContext currentContext, AdventureStatus status, long version, int turnIndex, String lastTurnKey,
            UUID lockedScenarioPackageId, long lockedScenarioPackageRevision, GameState gameState,
            DisclosureState disclosureState, CurrentSituation currentSituation, List<RuntimeAddedFact> runtimeAddedFacts) {
        this.id = Objects.requireNonNull(id, "adventure id must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        this.ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        this.scenarioId = Objects.requireNonNull(scenarioId, "scenario id must not be null");
        this.ruleSetId = Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        this.party = List.copyOf(Objects.requireNonNull(party, "party must not be null"));
        if (this.party.isEmpty()) throw new IllegalArgumentException("party must not be empty");
        if (lockedScenarioPackageId == null && lockedScenarioPackageRevision != 0) throw new IllegalArgumentException("unlocked adventure cannot have a package revision");
        if (lockedScenarioPackageId != null && lockedScenarioPackageRevision < 1) throw new IllegalArgumentException("locked scenario package revision must be positive");
        this.lockedScenarioPackageId = lockedScenarioPackageId;
        this.lockedScenarioPackageRevision = lockedScenarioPackageRevision;
        this.gameState = Objects.requireNonNull(gameState, "game state must not be null");
        this.disclosureState = Objects.requireNonNull(disclosureState, "disclosure state must not be null");
        this.currentSituation = currentSituation;
        this.runtimeAddedFacts = List.copyOf(Objects.requireNonNull(runtimeAddedFacts, "runtime facts must not be null"));
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
                List.of(), context, AdventureStatus.SAVED, 0, 0, null, null, 0,
                GameState.empty(), DisclosureState.empty(), null, List.of());
    }
    public static Adventure create(AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId, ScenarioId scenarioId, RuleSetId ruleSetId, List<AdventurePartyMember> party, AdventureContext context) {
        if (party == null || party.isEmpty()) throw new IllegalArgumentException("party must not be empty");
        return new Adventure(id, sessionId, ownerPlayerId, scenarioId, ruleSetId, party, List.of(), context, AdventureStatus.SAVED, 0, 0, null,
                null, 0, GameState.empty(), DisclosureState.empty(), null, List.of());
    }

    /** Creates the durable STARTING boundary before the first Situation exists. */
    public static Adventure beginScenarioRuntime(AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId,
            ScenarioId scenarioId, RuleSetId ruleSetId, UUID scenarioPackageId, long scenarioPackageRevision,
            List<AdventurePartyMember> party, AdventureContext context) {
        Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        return new Adventure(id, sessionId, ownerPlayerId, scenarioId, ruleSetId, party, List.of(), context,
                AdventureStatus.STARTING, 0, 0, null, scenarioPackageId, scenarioPackageRevision,
                GameState.empty(), DisclosureState.empty(), null, List.of());
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
        return new Adventure(id, sessionId, ownerPlayerId, scenarioId, ruleSetId, party, conversation, context, status, version, turnIndex, lastTurnKey,
                null, 0, GameState.empty(), DisclosureState.empty(), null, List.of());
    }

    public static Adventure rehydrateWithRuntimeState(
            AdventureId id, SessionId sessionId, OwnerPlayerId ownerPlayerId, ScenarioId scenarioId,
            RuleSetId ruleSetId, List<AdventurePartyMember> party, List<ConversationEntry> conversation,
            AdventureContext context, AdventureStatus status, long version, int turnIndex, String lastTurnKey,
            UUID lockedScenarioPackageId, long lockedScenarioPackageRevision, GameState gameState,
            DisclosureState disclosureState, CurrentSituation currentSituation, List<RuntimeAddedFact> runtimeAddedFacts) {
        return new Adventure(id, sessionId, ownerPlayerId, scenarioId, ruleSetId, party, conversation, context, status,
                version, turnIndex, lastTurnKey, lockedScenarioPackageId, lockedScenarioPackageRevision,
                gameState, disclosureState, currentSituation, runtimeAddedFacts);
    }

    public void lockScenarioPackage(UUID packageId, long packageRevision) {
        Objects.requireNonNull(packageId, "scenario package id must not be null");
        if (lockedScenarioPackageId != null || status != AdventureStatus.SAVED) {
            throw new IllegalStateException("scenario package is already locked");
        }
        if (packageRevision < 1) throw new IllegalArgumentException("scenario package revision must be positive");
        lockedScenarioPackageId = packageId;
        lockedScenarioPackageRevision = packageRevision;
        status = AdventureStatus.STARTING;
        version++;
    }

    public void initializeScenarioRuntime(OwnerPlayerId requestingOwner, GameState gameState,
            DisclosureState disclosureState, CurrentSituation situation, List<RuntimeAddedFact> runtimeFacts,
            AdventureContext playerContext) {
        authorizeSaved(requestingOwner);
        if (status != AdventureStatus.STARTING) {
            if (status == AdventureStatus.ACTIVE && currentSituation != null) return;
            throw new IllegalStateException("adventure is not starting");
        }
        if (lockedScenarioPackageId == null) throw new IllegalStateException("scenario package must be locked before start");
        if (currentSituation != null) throw new IllegalStateException("initial situation already exists");
        this.gameState = Objects.requireNonNull(gameState, "game state must not be null");
        this.disclosureState = Objects.requireNonNull(disclosureState, "disclosure state must not be null");
        this.currentSituation = Objects.requireNonNull(situation, "current situation must not be null");
        this.runtimeAddedFacts = validateRuntimeFacts(runtimeFacts);
        this.currentContext = Objects.requireNonNull(playerContext, "player context must not be null");
        this.status = AdventureStatus.ACTIVE;
        version++;
    }

    public void commitCanonicalState(OwnerPlayerId requestingOwner, long expectedVersion, GameState gameState,
            DisclosureState disclosureState, CurrentSituation situation, List<RuntimeAddedFact> runtimeFacts,
            AdventureContext playerContext, List<ConversationEntry> completeConversation) {
        authorizeSaved(requestingOwner);
        requireExpectedVersion(expectedVersion);
        this.gameState = Objects.requireNonNull(gameState, "game state must not be null");
        this.disclosureState = Objects.requireNonNull(disclosureState, "disclosure state must not be null");
        this.currentSituation = Objects.requireNonNull(situation, "current situation must not be null");
        this.runtimeAddedFacts = validateRuntimeFacts(runtimeFacts);
        this.currentContext = Objects.requireNonNull(playerContext, "player context must not be null");
        this.conversation = validateConversation(completeConversation);
        version++;
    }

    /** Applies every local RuntimeTurn change under one Adventure aggregate version. */
    public void commitRuntimeTurn(OwnerPlayerId requestingOwner, long expectedVersion,
            PendingRuntimeState pending, AdventureContext playerContext,
            List<ConversationEntry> completeConversation, CompletionProposal completion) {
        authorizeRuntime(requestingOwner);
        requireExpectedVersion(expectedVersion);
        Objects.requireNonNull(pending, "pending runtime state must not be null");
        Objects.requireNonNull(completion, "completion proposal must not be null");
        gameState = pending.gameStateDelta().apply(gameState);
        disclosureState = disclosureState.merge(pending.disclosureState());
        currentSituation = Objects.requireNonNull(pending.situation(), "situation must not be null");
        runtimeAddedFacts = mergeRuntimeFacts(pending.runtimeAddedFacts());
        currentContext = Objects.requireNonNull(playerContext, "player context must not be null");
        conversation = validateConversation(completeConversation);
        if (completion.complete()) {
            status = AdventureStatus.COMPLETED;
            currentContext = new AdventureContext(completion.concludingScene(), currentContext.npcState(),
                    currentContext.pendingAction(), currentContext.latestJudgment());
        }
        version++;
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

    private void authorizeRuntime(OwnerPlayerId requestingOwner) {
        if (!ownerPlayerId.equals(Objects.requireNonNull(requestingOwner, "requesting owner must not be null"))) {
            throw new AdventureAccessDeniedException();
        }
        if (status == AdventureStatus.DELETED || status == AdventureStatus.COMPLETED) {
            throw new IllegalStateException("adventure is not active");
        }
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

    private static List<RuntimeAddedFact> validateRuntimeFacts(List<RuntimeAddedFact> facts) {
        List<RuntimeAddedFact> copy = List.copyOf(Objects.requireNonNull(facts, "runtime facts must not be null"));
        if (copy.stream().map(RuntimeAddedFact::factId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("runtime fact ids must be unique");
        }
        return copy;
    }

    private List<RuntimeAddedFact> mergeRuntimeFacts(List<RuntimeAddedFact> additions) {
        List<RuntimeAddedFact> merged = new java.util.ArrayList<>(runtimeAddedFacts);
        for (RuntimeAddedFact addition : validateRuntimeFacts(additions)) {
            RuntimeAddedFact existing = merged.stream().filter(fact -> fact.factId().equals(addition.factId())).findFirst().orElse(null);
            if (existing != null) continue;
            String normalized = addition.content().toLowerCase(Locale.ROOT);
            if (merged.stream().anyMatch(fact -> fact.content().toLowerCase(Locale.ROOT).equals(normalized))) continue;
            merged.add(addition);
        }
        return List.copyOf(merged);
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
    public UUID lockedScenarioPackageId() { return lockedScenarioPackageId; }
    public long lockedScenarioPackageRevision() { return lockedScenarioPackageRevision; }
    public GameState gameState() { return gameState; }
    public DisclosureState disclosureState() { return disclosureState; }
    public CurrentSituation currentSituation() { return currentSituation; }
    public List<RuntimeAddedFact> runtimeAddedFacts() { return runtimeAddedFacts; }
}
