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
        UUID linkedCombatEncounterId) {

    public enum Phase { GROUP_TURNS, ENVIRONMENT }
    public enum EnvironmentPhase { ENEMIES_PATROLS_AND_DETECTION, HAZARDS_AND_ENVIRONMENT, DURATIONS_AND_DEADLINES }

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
            @JsonProperty("linkedCombatEncounterId") UUID linkedCombatEncounterId) {
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
            List<String> publicEvents, Position finalPosition, UUID hostileTokenId) {
        public MovementStopEvent {
            Objects.requireNonNull(id);
            Objects.requireNonNull(groupId);
            Objects.requireNonNull(movementOperationId);
            cause = cause == null ? "MOVEMENT_INTERRUPTED" : cause;
            publicEvents = List.copyOf(Objects.requireNonNull(publicEvents));
            Objects.requireNonNull(finalPosition);
        }
    }

    public record EnvironmentProposal(String narration, List<EnemyMove> enemyMoves,
            List<FeatureInteraction> featureInteractions, long expectedMapVersion,
            boolean encounterRequested, UUID linkedCombatEncounterId) {
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
            return new EnvironmentProposal(narration, enemyMoves, featureInteractions, expectedMapVersion, false, encounterId);
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
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, proposal, completedEnvironmentActions,
                pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId);
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
                pendingMovementEvent, resolvedMovementEventIds, encounterId);
    }

    public ExplorationCycle resumeAfterCombat(UUID encounterId) {
        Objects.requireNonNull(encounterId);
        if (!encounterId.equals(linkedCombatEncounterId))
            throw new IllegalArgumentException("combat encounter does not match the suspended exploration cycle");
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal,
                completedEnvironmentActions, pendingMovementEvent, resolvedMovementEventIds, null);
    }

    public ExplorationCycle resolveEncounterWithoutCombat() {
        if (phase != Phase.ENVIRONMENT || pendingEnvironmentProposal == null || !pendingEnvironmentProposal.encounterRequested())
            throw new IllegalStateException("environment encounter request is not pending");
        if (linkedCombatEncounterId != null || pendingEnvironmentProposal.linkedCombatEncounterId() != null)
            throw new IllegalStateException("environment encounter is linked to combat");
        EnvironmentProposal resolved = new EnvironmentProposal(pendingEnvironmentProposal.narration(),
                pendingEnvironmentProposal.enemyMoves(), pendingEnvironmentProposal.featureInteractions(),
                pendingEnvironmentProposal.expectedMapVersion(), false, null);
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, resolved, completedEnvironmentActions,
                pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId);
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
                pendingMovementEvent, resolvedMovementEventIds, linkedCombatEncounterId);
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
                resolvedMovementEventIds, linkedCombatEncounterId);
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
                completedEnvironmentActions, event, resolvedMovementEventIds, linkedCombatEncounterId);
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
                linkedCombatEncounterId);
    }

    public boolean movementEventResolved(UUID eventId) { return resolvedMovementEventIds.contains(eventId); }

    /** Uses the stable phase command ID as the retry key and advances time only after all phases finish. */
    public ExplorationCycle completeEnvironmentPhase(UUID commandId) {
        Objects.requireNonNull(commandId);
        if (phase != Phase.ENVIRONMENT) throw new IllegalStateException("environment phase is not active");
        if (linkedCombatEncounterId != null) throw new IllegalStateException("exploration cycle is suspended for combat");
        if (pendingEnvironmentProposal != null && pendingEnvironmentProposal.encounterRequested())
            throw new IllegalStateException("environment encounter handoff must be linked to combat before the phase can continue");
        if (completedEnvironmentCommands.contains(commandId)) return this;
        if (!currentPhaseCommandId().equals(commandId)) throw new IllegalArgumentException("environment command id is not current");
        Set<UUID> completed = new HashSet<>(completedEnvironmentCommands);
        completed.add(commandId);
        EnvironmentPhase[] phases = EnvironmentPhase.values();
        int nextIndex = environmentPhase.ordinal() + 1;
        if (nextIndex < phases.length) {
            return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, Phase.ENVIRONMENT,
                    phases[nextIndex], completed, elapsedSeconds, null, Set.of(), null, resolvedMovementEventIds,
                    linkedCombatEncounterId);
        }
        return new ExplorationCycle(id, cycleNumber + 1, initiativeByGroup, groupOrder, Set.of(), Phase.GROUP_TURNS,
                EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION, completed, elapsedSeconds + 6, null, Set.of());
    }
}
