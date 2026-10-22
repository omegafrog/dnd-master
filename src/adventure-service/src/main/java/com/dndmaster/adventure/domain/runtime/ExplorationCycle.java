package com.dndmaster.adventure.domain.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable paused-time movement order and six-second environment phase cursor. */
public record ExplorationCycle(
        UUID id,
        long cycleNumber,
        Map<UUID, Integer> initiativeByGroup,
        List<UUID> groupOrder,
        Set<UUID> completedGroups,
        Phase phase,
        EnvironmentPhase environmentPhase,
        Set<UUID> completedEnvironmentCommands,
        long elapsedSeconds,
        EnvironmentProposal pendingEnvironmentProposal,
        Set<UUID> completedEnvironmentActions,
        MovementStopEvent pendingMovementEvent,
        Set<UUID> resolvedMovementEventIds,
        UUID linkedCombatEncounterId,
        ThreatAwareness threatAwareness) {

    public enum Phase { GROUP_TURNS, ENVIRONMENT }
    public enum EnvironmentPhase { ENEMIES_PATROLS_AND_DETECTION, HAZARDS_AND_ENVIRONMENT, DURATIONS_AND_DEADLINES }

    public record ThreatAwareness(Map<UUID, Set<UUID>> awareCharactersByHostileTokenId,
            Set<UUID> awareHostileTokenIds) {
        public ThreatAwareness {
            var copy = new java.util.HashMap<UUID, Set<UUID>>();
            Objects.requireNonNullElse(awareCharactersByHostileTokenId, Map.<UUID, Set<UUID>>of())
                    .forEach((hostileId, characters) -> copy.put(Objects.requireNonNull(hostileId),
                            Set.copyOf(Objects.requireNonNullElse(characters, Set.of()))));
            awareCharactersByHostileTokenId = Map.copyOf(copy);
            awareHostileTokenIds = Set.copyOf(Objects.requireNonNullElse(awareHostileTokenIds, Set.of()));
        }
        public static ThreatAwareness empty() { return new ThreatAwareness(Map.of(), Set.of()); }
        public ThreatAwareness merge(Map<UUID, Set<UUID>> awarenessByHostile, Set<UUID> newlyAwareHostileIds) {
            var characters = new java.util.HashMap<>(awareCharactersByHostileTokenId);
            Objects.requireNonNullElse(awarenessByHostile, Map.<UUID, Set<UUID>>of()).forEach((hostileId, characterIds) -> {
                var merged = new java.util.HashSet<>(characters.getOrDefault(hostileId, Set.of()));
                merged.addAll(Objects.requireNonNullElse(characterIds, Set.of()));
                if (!merged.isEmpty()) characters.put(hostileId, Set.copyOf(merged));
            });
            var enemies = new java.util.HashSet<>(awareHostileTokenIds);
            enemies.addAll(Objects.requireNonNullElse(newlyAwareHostileIds, Set.of()));
            return new ThreatAwareness(characters, enemies);
        }
        public ThreatAwareness merge(Set<UUID> hostileTokenIds, Set<UUID> awareCharacterIds,
                Set<UUID> newlyAwareHostileIds) {
            var mapping = new java.util.HashMap<UUID, Set<UUID>>();
            for (UUID hostileId : Objects.requireNonNullElse(hostileTokenIds, Set.<UUID>of()))
                mapping.put(hostileId, Objects.requireNonNullElse(awareCharacterIds, Set.of()));
            return merge(mapping, newlyAwareHostileIds);
        }
    }

    private static Map<UUID, Set<UUID>> immutableAwarenessMap(Map<UUID, Set<UUID>> source) {
        var copy = new java.util.HashMap<UUID, Set<UUID>>();
        Objects.requireNonNullElse(source, Map.<UUID, Set<UUID>>of()).forEach((hostileId, characterIds) ->
                copy.put(Objects.requireNonNull(hostileId), Set.copyOf(Objects.requireNonNullElse(characterIds, Set.of()))));
        return Map.copyOf(copy);
    }

    @JsonCreator public ExplorationCycle(
            @JsonProperty("id") UUID id,
            @JsonProperty("cycleNumber") long cycleNumber,
            @JsonProperty("initiativeByGroup") Map<UUID, Integer> initiativeByGroup,
            @JsonProperty("groupOrder") List<UUID> groupOrder,
            @JsonProperty("completedGroups") Set<UUID> completedGroups,
            @JsonProperty("phase") Phase phase,
            @JsonProperty("environmentPhase") EnvironmentPhase environmentPhase,
            @JsonProperty("completedEnvironmentCommands") Set<UUID> completedEnvironmentCommands,
            @JsonProperty("elapsedSeconds") long elapsedSeconds,
            @JsonProperty("pendingEnvironmentProposal") EnvironmentProposal pendingEnvironmentProposal,
            @JsonProperty("completedEnvironmentActions") Set<UUID> completedEnvironmentActions,
            @JsonProperty("pendingMovementEvent") MovementStopEvent pendingMovementEvent,
            @JsonProperty("resolvedMovementEventIds") Set<UUID> resolvedMovementEventIds,
            @JsonProperty("linkedCombatEncounterId") UUID linkedCombatEncounterId,
            @JsonProperty("threatAwareness") ThreatAwareness threatAwareness) {
        this.id = Objects.requireNonNull(id, "cycle id must not be null");
        if (cycleNumber < 1 || elapsedSeconds < 0 || elapsedSeconds % 6 != 0)
            throw new IllegalArgumentException("cycle number and elapsed time are invalid");
        this.cycleNumber = cycleNumber;
        this.initiativeByGroup = Map.copyOf(Objects.requireNonNull(initiativeByGroup));
        this.groupOrder = List.copyOf(Objects.requireNonNull(groupOrder));
        this.completedGroups = Set.copyOf(Objects.requireNonNull(completedGroups));
        this.phase = Objects.requireNonNull(phase);
        this.environmentPhase = Objects.requireNonNull(environmentPhase);
        this.completedEnvironmentCommands = Set.copyOf(Objects.requireNonNull(completedEnvironmentCommands));
        this.elapsedSeconds = elapsedSeconds;
        this.pendingEnvironmentProposal = pendingEnvironmentProposal;
        this.completedEnvironmentActions = Set.copyOf(Objects.requireNonNullElse(completedEnvironmentActions, Set.of()));
        this.pendingMovementEvent = pendingMovementEvent;
        this.resolvedMovementEventIds = Set.copyOf(Objects.requireNonNullElse(resolvedMovementEventIds, Set.of()));
        this.linkedCombatEncounterId = linkedCombatEncounterId;
        this.threatAwareness = Objects.requireNonNullElse(threatAwareness, ThreatAwareness.empty());
        if (this.groupOrder.isEmpty() || this.groupOrder.size() != this.initiativeByGroup.size()
                || !new HashSet<>(this.groupOrder).equals(this.initiativeByGroup.keySet())
                || this.groupOrder.stream().anyMatch(Objects::isNull)
                || !this.groupOrder.containsAll(this.completedGroups))
            throw new IllegalArgumentException("cycle group order must include each scored group exactly once");
        if (phase != Phase.ENVIRONMENT && pendingEnvironmentProposal != null)
            throw new IllegalArgumentException("environment proposal requires environment phase");
    }

    public ExplorationCycle(UUID id, long cycleNumber, Map<UUID, Integer> initiativeByGroup, List<UUID> groupOrder,
            Set<UUID> completedGroups, Phase phase, EnvironmentPhase environmentPhase,
            Set<UUID> completedEnvironmentCommands, long elapsedSeconds, EnvironmentProposal pendingEnvironmentProposal,
            Set<UUID> completedEnvironmentActions, MovementStopEvent pendingMovementEvent,
            Set<UUID> resolvedMovementEventIds, UUID linkedCombatEncounterId) {
        this(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase, environmentPhase,
                completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal, completedEnvironmentActions,
                pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId, ThreatAwareness.empty());
    }

    public ExplorationCycle(UUID id, long cycleNumber, Map<UUID, Integer> initiativeByGroup, List<UUID> groupOrder,
            Set<UUID> completedGroups, Phase phase, EnvironmentPhase environmentPhase,
            Set<UUID> completedEnvironmentCommands, long elapsedSeconds) {
        this(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase, environmentPhase,
                completedEnvironmentCommands, elapsedSeconds, null, Set.of(), null, Set.of());
    }

    public ExplorationCycle(UUID id, long cycleNumber, Map<UUID, Integer> initiativeByGroup, List<UUID> groupOrder,
            Set<UUID> completedGroups, Phase phase, EnvironmentPhase environmentPhase,
            Set<UUID> completedEnvironmentCommands, long elapsedSeconds, EnvironmentProposal pendingEnvironmentProposal,
            Set<UUID> completedEnvironmentActions) {
        this(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase, environmentPhase,
                completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal, completedEnvironmentActions, null, Set.of());
    }

    public ExplorationCycle(UUID id, long cycleNumber, Map<UUID, Integer> initiativeByGroup, List<UUID> groupOrder,
            Set<UUID> completedGroups, Phase phase, EnvironmentPhase environmentPhase,
            Set<UUID> completedEnvironmentCommands, long elapsedSeconds, EnvironmentProposal pendingEnvironmentProposal,
            Set<UUID> completedEnvironmentActions, MovementStopEvent pendingMovementEvent) {
        this(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase, environmentPhase,
                completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal, completedEnvironmentActions,
                pendingMovementEvent, Set.of(), null);
    }

    public ExplorationCycle(UUID id, long cycleNumber, Map<UUID, Integer> initiativeByGroup, List<UUID> groupOrder,
            Set<UUID> completedGroups, Phase phase, EnvironmentPhase environmentPhase,
            Set<UUID> completedEnvironmentCommands, long elapsedSeconds, EnvironmentProposal pendingEnvironmentProposal,
            Set<UUID> completedEnvironmentActions, MovementStopEvent pendingMovementEvent, Set<UUID> resolvedMovementEventIds) {
        this(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase, environmentPhase,
                completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal, completedEnvironmentActions,
                pendingMovementEvent, resolvedMovementEventIds, null);
    }

    public record MovementStopEvent(UUID id, UUID groupId, UUID movementOperationId, String cause,
            List<String> publicEvents, Position finalPosition, UUID hostileTokenId,
            Set<UUID> discoveredByCharacterSheetIds, Set<UUID> awareCharacterSheetIds, boolean enemiesAware,
            Set<UUID> awareEnemyTokenIds, Set<UUID> threatHostileTokenIds,
            Map<UUID, Set<UUID>> awareCharactersByHostileTokenId) {
        public MovementStopEvent(UUID id, UUID groupId, UUID movementOperationId, String cause,
                List<String> publicEvents, Position finalPosition, UUID hostileTokenId,
                Set<UUID> discoveredByCharacterSheetIds, Set<UUID> awareCharacterSheetIds, boolean enemiesAware) {
            this(id, groupId, movementOperationId, cause, publicEvents, finalPosition, hostileTokenId,
                    discoveredByCharacterSheetIds, awareCharacterSheetIds, enemiesAware, Set.of(),
                    hostileTokenId == null ? Set.of() : Set.of(hostileTokenId), Map.of());
        }
        public MovementStopEvent(UUID id, UUID groupId, UUID movementOperationId, String cause,
                List<String> publicEvents, Position finalPosition, UUID hostileTokenId,
                Set<UUID> discoveredByCharacterSheetIds, Set<UUID> awareCharacterSheetIds, boolean enemiesAware,
                Set<UUID> awareEnemyTokenIds) {
            this(id, groupId, movementOperationId, cause, publicEvents, finalPosition, hostileTokenId,
                    discoveredByCharacterSheetIds, awareCharacterSheetIds, enemiesAware, awareEnemyTokenIds,
                    hostileTokenId == null ? Set.of() : Set.of(hostileTokenId), Map.of());
        }
        public MovementStopEvent(UUID id, UUID groupId, UUID movementOperationId, String cause,
                List<String> publicEvents, Position finalPosition, UUID hostileTokenId,
                Set<UUID> discoveredByCharacterSheetIds, Set<UUID> awareCharacterSheetIds, boolean enemiesAware,
                Set<UUID> awareEnemyTokenIds, Set<UUID> threatHostileTokenIds) {
            this(id, groupId, movementOperationId, cause, publicEvents, finalPosition, hostileTokenId,
                    discoveredByCharacterSheetIds, awareCharacterSheetIds, enemiesAware, awareEnemyTokenIds,
                    threatHostileTokenIds, Map.of());
        }
        public MovementStopEvent(UUID id, UUID groupId, UUID movementOperationId, String cause,
                List<String> publicEvents, Position finalPosition, UUID hostileTokenId) {
            this(id, groupId, movementOperationId, cause, publicEvents, finalPosition, hostileTokenId, Set.of(), Set.of(), false);
        }
        public MovementStopEvent {
            Objects.requireNonNull(id);
            Objects.requireNonNull(groupId);
            Objects.requireNonNull(movementOperationId);
            cause = cause == null ? "MOVEMENT_INTERRUPTED" : cause;
            publicEvents = List.copyOf(Objects.requireNonNull(publicEvents));
            Objects.requireNonNull(finalPosition);
            discoveredByCharacterSheetIds = Set.copyOf(Objects.requireNonNullElse(discoveredByCharacterSheetIds, Set.of()));
            awareCharacterSheetIds = Set.copyOf(Objects.requireNonNullElse(awareCharacterSheetIds, Set.of()));
            awareEnemyTokenIds = Set.copyOf(Objects.requireNonNullElse(awareEnemyTokenIds, Set.of()));
            threatHostileTokenIds = Set.copyOf(Objects.requireNonNullElse(threatHostileTokenIds,
                    hostileTokenId == null ? Set.of() : Set.of(hostileTokenId)));
            awareCharactersByHostileTokenId = immutableAwarenessMap(awareCharactersByHostileTokenId);
            if (!awareCharacterSheetIds.containsAll(discoveredByCharacterSheetIds))
                throw new IllegalArgumentException("discoverers must be aware of the threat");
        }
        public MovementStopEvent shareWith(Set<UUID> characterSheetIds, UUID discovererId, boolean enemiesHeardNotification) {
            return shareWith(characterSheetIds, discovererId, enemiesHeardNotification,
                    enemiesHeardNotification && hostileTokenId != null ? Set.of(hostileTokenId) : Set.of());
        }
        public MovementStopEvent shareWith(Set<UUID> characterSheetIds, UUID discovererId, boolean enemiesHeardNotification,
                Set<UUID> heardEnemyTokenIds) {
            if (!discoveredByCharacterSheetIds.contains(discovererId)) throw new IllegalArgumentException("sharing actor has not discovered the threat");
            var shared = new HashSet<>(awareCharacterSheetIds);
            shared.addAll(characterSheetIds);
            var perHostile = new java.util.HashMap<>(awareCharactersByHostileTokenId);
            for (UUID hostileId : perHostile.keySet()) {
                var aware = new HashSet<>(perHostile.get(hostileId));
                aware.addAll(characterSheetIds);
                perHostile.put(hostileId, Set.copyOf(aware));
            }
            var awareEnemies = new HashSet<>(awareEnemyTokenIds);
            awareEnemies.addAll(heardEnemyTokenIds);
            return new MovementStopEvent(id, groupId, movementOperationId, cause, publicEvents, finalPosition,
                    hostileTokenId, discoveredByCharacterSheetIds, shared, enemiesAware || enemiesHeardNotification,
                    awareEnemies, threatHostileTokenIds, perHostile);
        }
    }

    public record ThreatDiscovery(Set<UUID> discovererCharacterSheetIds, boolean enemyDiscoveredFirst,
            boolean enemyHeardNotification, boolean bothSidesNotHidden, Set<UUID> awareEnemyTokenIds,
            Set<UUID> threatHostileTokenIds, Map<UUID, Set<UUID>> awareCharactersByHostileTokenId) {
        public ThreatDiscovery(Set<UUID> discovererCharacterSheetIds, boolean enemyDiscoveredFirst,
                boolean enemyHeardNotification, boolean bothSidesNotHidden) {
            this(discovererCharacterSheetIds, enemyDiscoveredFirst, enemyHeardNotification, bothSidesNotHidden,
                    Set.of(), Set.of(), Map.of());
        }
        public ThreatDiscovery(Set<UUID> discovererCharacterSheetIds, boolean enemyDiscoveredFirst,
                boolean enemyHeardNotification, boolean bothSidesNotHidden, Set<UUID> awareEnemyTokenIds) {
            this(discovererCharacterSheetIds, enemyDiscoveredFirst, enemyHeardNotification, bothSidesNotHidden,
                    awareEnemyTokenIds, Set.of(), Map.of());
        }
        public ThreatDiscovery(Set<UUID> discovererCharacterSheetIds, boolean enemyDiscoveredFirst,
                boolean enemyHeardNotification, boolean bothSidesNotHidden, Set<UUID> awareEnemyTokenIds,
                Set<UUID> threatHostileTokenIds) {
            this(discovererCharacterSheetIds, enemyDiscoveredFirst, enemyHeardNotification, bothSidesNotHidden,
                    awareEnemyTokenIds, threatHostileTokenIds, Map.of());
        }
        public ThreatDiscovery {
            discovererCharacterSheetIds = Set.copyOf(Objects.requireNonNullElse(discovererCharacterSheetIds, Set.of()));
            awareEnemyTokenIds = Set.copyOf(Objects.requireNonNullElse(awareEnemyTokenIds, Set.of()));
            threatHostileTokenIds = Set.copyOf(Objects.requireNonNullElse(threatHostileTokenIds, Set.of()));
            awareCharactersByHostileTokenId = immutableAwarenessMap(awareCharactersByHostileTokenId);
            if (enemyDiscoveredFirst && !discovererCharacterSheetIds.isEmpty())
                throw new IllegalArgumentException("enemy-first discovery cannot name party discoverers");
            if (bothSidesNotHidden && enemyDiscoveredFirst)
                throw new IllegalArgumentException("both sides cannot be unnoticed when enemies discovered first");
        }
    }

    public record EnvironmentProposal(String narration, List<EnemyMove> enemyMoves,
            List<FeatureInteraction> featureInteractions, long expectedMapVersion,
            boolean encounterRequested, UUID linkedCombatEncounterId, ThreatDiscovery threatDiscovery,
            Set<UUID> awareCharacterSheetIds, boolean hostileActionStarted, boolean playerResponseRequired) {
        public EnvironmentProposal(String narration, List<EnemyMove> enemyMoves, List<FeatureInteraction> featureInteractions,
                long expectedMapVersion, boolean encounterRequested, UUID linkedCombatEncounterId, ThreatDiscovery threatDiscovery,
                Set<UUID> awareCharacterSheetIds) {
            this(narration, enemyMoves, featureInteractions, expectedMapVersion, encounterRequested, linkedCombatEncounterId,
                    threatDiscovery, awareCharacterSheetIds, encounterRequested, false);
        }
        public EnvironmentProposal(String narration, List<EnemyMove> enemyMoves, List<FeatureInteraction> featureInteractions,
                long expectedMapVersion, boolean encounterRequested, UUID linkedCombatEncounterId, ThreatDiscovery threatDiscovery,
                Set<UUID> awareCharacterSheetIds, boolean hostileActionStarted) {
            this(narration, enemyMoves, featureInteractions, expectedMapVersion, encounterRequested, linkedCombatEncounterId,
                    threatDiscovery, awareCharacterSheetIds, hostileActionStarted, false);
        }
        public EnvironmentProposal(String narration, List<EnemyMove> enemyMoves, List<FeatureInteraction> featureInteractions,
                long expectedMapVersion, boolean encounterRequested, UUID linkedCombatEncounterId) {
            this(narration, enemyMoves, featureInteractions, expectedMapVersion, encounterRequested, linkedCombatEncounterId, null, Set.of());
        }
        public EnvironmentProposal(String narration, List<EnemyMove> enemyMoves) { this(narration, enemyMoves, List.of(), -1, false, null); }
        public EnvironmentProposal(String narration, List<EnemyMove> enemyMoves, long expectedMapVersion) {
            this(narration, enemyMoves, List.of(), expectedMapVersion, false, null);
        }
        public EnvironmentProposal(String narration, List<EnemyMove> enemyMoves, long expectedMapVersion,
                boolean encounterRequested, UUID linkedCombatEncounterId) {
            this(narration, enemyMoves, List.of(), expectedMapVersion, encounterRequested, linkedCombatEncounterId);
        }
        public EnvironmentProposal {
            narration = narration == null ? "" : narration.trim();
            enemyMoves = List.copyOf(Objects.requireNonNull(enemyMoves));
            featureInteractions = List.copyOf(Objects.requireNonNullElse(featureInteractions, List.of()));
            awareCharacterSheetIds = Set.copyOf(awareCharacterSheetIds == null
                    ? threatDiscovery == null ? Set.of() : threatDiscovery.discovererCharacterSheetIds()
                    : awareCharacterSheetIds);
            if (threatDiscovery == null && !awareCharacterSheetIds.isEmpty())
                throw new IllegalArgumentException("awareness requires a threat discovery");
            if (threatDiscovery != null && !awareCharacterSheetIds.containsAll(threatDiscovery.discovererCharacterSheetIds()))
                throw new IllegalArgumentException("discoverers must remain aware of the threat");
            if (featureInteractions.stream().map(FeatureInteraction::featureId).distinct().count() != featureInteractions.size())
                throw new IllegalArgumentException("environment feature interactions must be unique by feature id");
            if (expectedMapVersion < -1) throw new IllegalArgumentException("expected map version is invalid");
            if (linkedCombatEncounterId != null && encounterRequested)
                throw new IllegalArgumentException("linked combat encounter already satisfies the encounter handoff");
        }

        public EnvironmentProposal linkCombatEncounter(UUID encounterId) {
            Objects.requireNonNull(encounterId);
            if (!encounterRequested && linkedCombatEncounterId == null)
                throw new IllegalStateException("environment phase did not request an encounter");
            if (linkedCombatEncounterId != null && !linkedCombatEncounterId.equals(encounterId))
                throw new IllegalStateException("environment phase is already linked to another combat encounter");
            return new EnvironmentProposal(narration, enemyMoves, featureInteractions, expectedMapVersion, false, encounterId,
                    threatDiscovery, awareCharacterSheetIds, hostileActionStarted, playerResponseRequired);
        }
        public EnvironmentProposal shareWith(Set<UUID> characterSheetIds, UUID discovererId, boolean enemiesHeardNotification) {
            return shareWith(characterSheetIds, discovererId, enemiesHeardNotification, Set.of());
        }
        public EnvironmentProposal shareWith(Set<UUID> characterSheetIds, UUID discovererId,
                boolean enemiesHeardNotification, Set<UUID> heardEnemyTokenIds) {
            if (threatDiscovery == null || !threatDiscovery.discovererCharacterSheetIds().contains(discovererId))
                throw new IllegalArgumentException("sharing actor has not discovered the threat");
            var shared = new HashSet<>(awareCharacterSheetIds);
            shared.addAll(characterSheetIds);
            var awareEnemies = new HashSet<>(threatDiscovery.awareEnemyTokenIds());
            awareEnemies.addAll(heardEnemyTokenIds);
            var perHostileAwareness = new java.util.HashMap<UUID, Set<UUID>>(
                    threatDiscovery.awareCharactersByHostileTokenId());
            perHostileAwareness.replaceAll((hostileId, awareCharacters) -> {
                var sharedAwareness = new HashSet<>(awareCharacters);
                sharedAwareness.addAll(characterSheetIds);
                return Set.copyOf(sharedAwareness);
            });
            var discovery = new ThreatDiscovery(threatDiscovery.discovererCharacterSheetIds(),
                    threatDiscovery.enemyDiscoveredFirst(), enemiesHeardNotification, threatDiscovery.bothSidesNotHidden(),
                    awareEnemies, threatDiscovery.threatHostileTokenIds(), perHostileAwareness);
            return new EnvironmentProposal(narration, enemyMoves, featureInteractions, expectedMapVersion,
                    encounterRequested, linkedCombatEncounterId, discovery, shared, hostileActionStarted, playerResponseRequired);
        }
        public EnvironmentProposal withDiscovery(ThreatDiscovery discovery, Set<UUID> partyCharacterIds) {
            Objects.requireNonNull(discovery);
            if (!partyCharacterIds.containsAll(discovery.discovererCharacterSheetIds()))
                throw new IllegalArgumentException("threat discoverers must belong to the adventure party");
            Map<UUID, Set<UUID>> perHostile = new java.util.HashMap<>(discovery.awareCharactersByHostileTokenId());
            if (discovery.bothSidesNotHidden()) for (UUID hostileId : discovery.threatHostileTokenIds())
                perHostile.put(hostileId, Set.copyOf(partyCharacterIds));
            Set<UUID> aware = perHostile.values().stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
            if (perHostile.isEmpty()) aware = discovery.discovererCharacterSheetIds();
            ThreatDiscovery effectiveDiscovery = new ThreatDiscovery(discovery.discovererCharacterSheetIds(),
                    discovery.enemyDiscoveredFirst(), discovery.enemyHeardNotification(), discovery.bothSidesNotHidden(),
                    discovery.awareEnemyTokenIds(), discovery.threatHostileTokenIds(), perHostile);
            return new EnvironmentProposal(narration, enemyMoves, featureInteractions, expectedMapVersion,
                    encounterRequested, linkedCombatEncounterId, effectiveDiscovery, aware, hostileActionStarted,
                    discovery.enemyDiscoveredFirst() && !hostileActionStarted);
        }
        public EnvironmentProposal acknowledgePlayerResponse() {
            if (!playerResponseRequired) return this;
            return new EnvironmentProposal(narration, enemyMoves, featureInteractions, expectedMapVersion,
                    encounterRequested, linkedCombatEncounterId, threatDiscovery, awareCharacterSheetIds,
                    hostileActionStarted, false);
        }
        public boolean enemiesAware() {
            return threatDiscovery != null && (!threatDiscovery.awareEnemyTokenIds().isEmpty()
                    || threatDiscovery.enemyDiscoveredFirst() || threatDiscovery.enemyHeardNotification()
                    || threatDiscovery.bothSidesNotHidden());
        }
    }
    public record FeatureInteraction(UUID featureId) {
        public FeatureInteraction { Objects.requireNonNull(featureId); }
    }
    public record Position(int x, int y) { }
    public record EnemyMove(UUID tokenId, List<Position> path) {
        public EnemyMove {
            Objects.requireNonNull(tokenId);
            path = List.copyOf(Objects.requireNonNull(path));
            if (path.isEmpty()) throw new IllegalArgumentException("enemy movement path must not be empty");
        }
    }

    public static ExplorationCycle start(UUID id, Map<UUID, Integer> initiativeByGroup) {
        return start(id, initiativeByGroup, new java.util.Random());
    }

    public static ExplorationCycle start(UUID id, Map<UUID, Integer> initiativeByGroup, java.util.Random random) {
        List<UUID> order = ExplorationInitiativePolicy.order(initiativeByGroup, random);
        return new ExplorationCycle(id, 1, initiativeByGroup, order, Set.of(), Phase.GROUP_TURNS,
                EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION, Set.of(), 0);
    }

    public UUID currentGroupId() {
        if (phase != Phase.GROUP_TURNS || pendingMovementEvent != null || linkedCombatEncounterId != null) return null;
        return groupOrder.stream().filter(id -> !completedGroups.contains(id)).findFirst().orElse(null);
    }

    public UUID linkedCombatEncounterId() { return linkedCombatEncounterId; }

    @com.fasterxml.jackson.annotation.JsonProperty(value = "currentPhaseCommandId",
            access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public UUID currentPhaseCommandId() {
        if (phase != Phase.ENVIRONMENT) return null;
        return UUID.nameUUIDFromBytes((id + ":" + cycleNumber + ":" + environmentPhase)
                .getBytes(StandardCharsets.UTF_8));
    }

    public boolean groupTurnFinished(UUID groupId) { return completedGroups.contains(groupId); }

    public ExplorationCycle withEnvironmentProposal(EnvironmentProposal proposal) {
        if (phase != Phase.ENVIRONMENT) throw new IllegalStateException("environment phase is not active");
        Objects.requireNonNull(proposal);
        if (environmentPhase != EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION && !proposal.enemyMoves().isEmpty())
            throw new IllegalArgumentException("enemy movement is allowed only during the enemy and patrol phase");
        if (environmentPhase != EnvironmentPhase.HAZARDS_AND_ENVIRONMENT && !proposal.featureInteractions().isEmpty())
            throw new IllegalArgumentException("spatial feature interactions are allowed only during the hazards and environment phase");
        if (pendingEnvironmentProposal != null && !pendingEnvironmentProposal.equals(proposal))
            throw new IllegalStateException("environment proposal is already persisted");
        ThreatAwareness awareness = threatAwareness;
        if (proposal.threatDiscovery() != null) awareness = awareness.merge(
                proposal.threatDiscovery().awareCharactersByHostileTokenId(),
                proposal.threatDiscovery().awareEnemyTokenIds());
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, proposal, completedEnvironmentActions,
                pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId, awareness);
    }

    public ExplorationCycle shareThreatWithGroup(UUID groupId, UUID discovererId, Set<UUID> groupMemberIds,
            boolean enemiesHeardNotification) {
        return shareThreatWithGroup(groupId, discovererId, groupMemberIds, enemiesHeardNotification, Set.of());
    }
    public ExplorationCycle shareThreatWithGroup(UUID groupId, UUID discovererId, Set<UUID> groupMemberIds,
            boolean enemiesHeardNotification, Set<UUID> heardEnemyTokenIds) {
        Objects.requireNonNull(groupId);
        Objects.requireNonNull(discovererId);
        Set<UUID> members = Set.copyOf(Objects.requireNonNull(groupMemberIds));
        if (!members.contains(discovererId)) throw new IllegalArgumentException("sharing character is not in the group");
        if (pendingMovementEvent != null && pendingMovementEvent.groupId().equals(groupId)) {
            if (!pendingMovementEvent.discoveredByCharacterSheetIds().contains(discovererId))
                throw new IllegalArgumentException("sharing actor has not discovered the threat");
            if (pendingMovementEvent.awareCharacterSheetIds().containsAll(members)) return this;
            var event = pendingMovementEvent.shareWith(members, discovererId, enemiesHeardNotification, heardEnemyTokenIds);
            var awareness = threatAwareness.merge(event.awareCharactersByHostileTokenId(), heardEnemyTokenIds);
            return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                    environmentPhase, completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal,
                    completedEnvironmentActions, event, resolvedMovementEventIds, linkedCombatEncounterId, awareness);
        }
        if (pendingEnvironmentProposal != null && pendingEnvironmentProposal.threatDiscovery() != null) {
            if (!pendingEnvironmentProposal.threatDiscovery().discovererCharacterSheetIds().contains(discovererId))
                throw new IllegalArgumentException("sharing actor has not discovered the threat");
            if (pendingEnvironmentProposal.awareCharacterSheetIds().containsAll(members)) return this;
            var proposal = pendingEnvironmentProposal.shareWith(members, discovererId, enemiesHeardNotification, heardEnemyTokenIds);
            var awareness = threatAwareness.merge(proposal.threatDiscovery().awareCharactersByHostileTokenId(), heardEnemyTokenIds);
            return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                    environmentPhase, completedEnvironmentCommands, elapsedSeconds, proposal,
                    completedEnvironmentActions, pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId, awareness);
        }
        throw new IllegalStateException("no shareable threat discovery is pending");
    }

    public ExplorationCycle linkCombatEncounter(UUID encounterId) {
        Objects.requireNonNull(encounterId);
        if (linkedCombatEncounterId != null && !linkedCombatEncounterId.equals(encounterId))
            throw new IllegalStateException("exploration cycle is already linked to another combat encounter");
        EnvironmentProposal linked = pendingEnvironmentProposal;
        if (linked != null && (linked.encounterRequested() || linked.linkedCombatEncounterId() != null))
            linked = linked.linkCombatEncounter(encounterId);
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, linked, completedEnvironmentActions,
                pendingMovementEvent, resolvedMovementEventIds, encounterId, ThreatAwareness.empty());
    }

    public ExplorationCycle resumeAfterCombat(UUID encounterId) {
        Objects.requireNonNull(encounterId);
        if (!encounterId.equals(linkedCombatEncounterId))
            throw new IllegalArgumentException("combat encounter does not match the suspended exploration cycle");
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal,
                completedEnvironmentActions, pendingMovementEvent, resolvedMovementEventIds, null, ThreatAwareness.empty());
    }

    public ExplorationCycle resolveEncounterWithoutCombat() {
        if (phase != Phase.ENVIRONMENT || pendingEnvironmentProposal == null || !pendingEnvironmentProposal.encounterRequested())
            throw new IllegalStateException("environment encounter request is not pending");
        if (linkedCombatEncounterId != null || pendingEnvironmentProposal.linkedCombatEncounterId() != null)
            throw new IllegalStateException("environment encounter is linked to combat");
        EnvironmentProposal resolved = new EnvironmentProposal(pendingEnvironmentProposal.narration(),
                pendingEnvironmentProposal.enemyMoves(), pendingEnvironmentProposal.featureInteractions(),
                pendingEnvironmentProposal.expectedMapVersion(), false, null, pendingEnvironmentProposal.threatDiscovery(),
                pendingEnvironmentProposal.awareCharacterSheetIds(), pendingEnvironmentProposal.hostileActionStarted());
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, resolved, completedEnvironmentActions,
                pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId, threatAwareness);
    }

    public ExplorationCycle acknowledgePendingEnvironmentResponse() {
        if (phase != Phase.ENVIRONMENT || pendingEnvironmentProposal == null || linkedCombatEncounterId != null)
            return this;
        EnvironmentProposal acknowledged = pendingEnvironmentProposal.acknowledgePlayerResponse();
        if (acknowledged == pendingEnvironmentProposal) return this;
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, acknowledged, completedEnvironmentActions,
                pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId, threatAwareness);
    }

    public ExplorationCycle completeEnvironmentAction(UUID actionId) {
        Objects.requireNonNull(actionId);
        if (phase != Phase.ENVIRONMENT || pendingEnvironmentProposal == null)
            throw new IllegalStateException("environment proposal is not active");
        if (completedEnvironmentActions.contains(actionId)) return this;
        Set<UUID> completed = new HashSet<>(completedEnvironmentActions);
        completed.add(actionId);
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal, completed,
                pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId, threatAwareness);
    }

    /** A stopping event closes this group's movement turn; no remaining route is replayed. */
    public ExplorationCycle finishGroupTurn(UUID groupId, boolean interrupted) {
        Objects.requireNonNull(groupId);
        if (!initiativeByGroup.containsKey(groupId)) throw new IllegalArgumentException("group is not in this cycle");
        if (linkedCombatEncounterId != null) throw new IllegalStateException("exploration cycle is suspended for combat");
        if (phase != Phase.GROUP_TURNS) throw new IllegalStateException("group turns are not active");
        if (pendingMovementEvent != null) throw new IllegalStateException("movement event must be resolved before another group moves");
        if (completedGroups.contains(groupId)) return this;
        if (!groupId.equals(currentGroupId())) throw new IllegalStateException("groups must finish in initiative order");
        Set<UUID> completed = new HashSet<>(completedGroups);
        completed.add(groupId);
        Phase nextPhase = completed.size() == groupOrder.size() ? Phase.ENVIRONMENT : Phase.GROUP_TURNS;
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completed, nextPhase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, null, Set.of(), null,
                resolvedMovementEventIds, linkedCombatEncounterId, threatAwareness);
    }

    public ExplorationCycle interruptGroupTurn(UUID groupId, MovementStopEvent event) {
        Objects.requireNonNull(event);
        if (linkedCombatEncounterId != null) throw new IllegalStateException("exploration cycle is suspended for combat");
        if (!initiativeByGroup.containsKey(groupId) || !groupId.equals(event.groupId()))
            throw new IllegalArgumentException("movement event group is not in this cycle");
        if (resolvedMovementEventIds.contains(event.id())) return this;
        if (pendingMovementEvent != null) {
            if (pendingMovementEvent.movementOperationId().equals(event.movementOperationId())) return this;
            throw new IllegalStateException("another movement event is waiting for resolution");
        }
        if (phase != Phase.GROUP_TURNS || !groupId.equals(currentGroupId()))
            throw new IllegalStateException("only the active group turn can be interrupted");
        Set<UUID> completed = new HashSet<>(completedGroups);
        completed.add(groupId);
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completed, Phase.GROUP_TURNS,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal,
                completedEnvironmentActions, event, resolvedMovementEventIds, linkedCombatEncounterId,
                threatAwareness.merge(event.awareCharactersByHostileTokenId(), event.awareEnemyTokenIds()));
    }

    public ExplorationCycle resolveMovementEvent(UUID eventId) {
        Objects.requireNonNull(eventId);
        if (linkedCombatEncounterId != null) throw new IllegalStateException("exploration cycle is suspended for combat");
        if (resolvedMovementEventIds.contains(eventId)) return this;
        if (pendingMovementEvent == null || !pendingMovementEvent.id().equals(eventId))
            throw new IllegalArgumentException("movement event id is not pending");
        Phase nextPhase = completedGroups.size() == groupOrder.size() ? Phase.ENVIRONMENT : Phase.GROUP_TURNS;
        Set<UUID> resolved = new HashSet<>(resolvedMovementEventIds);
        resolved.add(eventId);
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, nextPhase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, null, Set.of(), null, resolved,
                linkedCombatEncounterId, threatAwareness);
    }

    public boolean movementEventResolved(UUID eventId) { return resolvedMovementEventIds.contains(eventId); }

    /** Uses the stable phase command ID as the retry key and advances time only after all phases finish. */
    public ExplorationCycle completeEnvironmentPhase(UUID commandId) {
        Objects.requireNonNull(commandId);
        if (phase != Phase.ENVIRONMENT) throw new IllegalStateException("environment phase is not active");
        if (linkedCombatEncounterId != null) throw new IllegalStateException("exploration cycle is suspended for combat");
        if (pendingEnvironmentProposal != null && pendingEnvironmentProposal.encounterRequested())
            throw new IllegalStateException("environment encounter handoff must be linked to combat before the phase can continue");
        if (pendingEnvironmentProposal != null && pendingEnvironmentProposal.playerResponseRequired())
            throw new IllegalStateException("player response is required before the environment phase can continue");
        if (completedEnvironmentCommands.contains(commandId)) return this;
        if (!currentPhaseCommandId().equals(commandId)) throw new IllegalArgumentException("environment command id is not current");
        Set<UUID> completed = new HashSet<>(completedEnvironmentCommands);
        completed.add(commandId);
        EnvironmentPhase[] phases = EnvironmentPhase.values();
        int nextIndex = environmentPhase.ordinal() + 1;
        if (nextIndex < phases.length) {
            return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, Phase.ENVIRONMENT,
                    phases[nextIndex], completed, elapsedSeconds, null, Set.of(), null, resolvedMovementEventIds,
                    linkedCombatEncounterId, threatAwareness);
        }
        return new ExplorationCycle(id, cycleNumber + 1, initiativeByGroup, groupOrder, Set.of(), Phase.GROUP_TURNS,
                EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION, completed, elapsedSeconds + 6, null, Set.of(), null,
                Set.of(), null, threatAwareness);
    }
}
