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
        Set<UUID> completedEnvironmentActions) {

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
            @JsonProperty("completedEnvironmentActions") Set<UUID> completedEnvironmentActions) {
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
                completedEnvironmentCommands, elapsedSeconds, null, Set.of());
    }

    public record EnvironmentProposal(String narration, List<EnemyMove> enemyMoves, long expectedMapVersion) {
        public EnvironmentProposal(String narration, List<EnemyMove> enemyMoves) { this(narration, enemyMoves, -1); }
        public EnvironmentProposal {
            narration = narration == null ? "" : narration.trim();
            enemyMoves = List.copyOf(Objects.requireNonNull(enemyMoves));
            if (expectedMapVersion < -1) throw new IllegalArgumentException("expected map version is invalid");
        }
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
        if (phase != Phase.GROUP_TURNS) return null;
        return groupOrder.stream().filter(id -> !completedGroups.contains(id)).findFirst().orElse(null);
    }

    public UUID currentPhaseCommandId() {
        if (phase != Phase.ENVIRONMENT) return null;
        return UUID.nameUUIDFromBytes((id + ":" + cycleNumber + ":" + environmentPhase)
                .getBytes(StandardCharsets.UTF_8));
    }

    public boolean groupTurnFinished(UUID groupId) { return completedGroups.contains(groupId); }

    public ExplorationCycle withEnvironmentProposal(EnvironmentProposal proposal) {
        if (phase != Phase.ENVIRONMENT) throw new IllegalStateException("environment phase is not active");
        Objects.requireNonNull(proposal);
        if (pendingEnvironmentProposal != null && !pendingEnvironmentProposal.equals(proposal))
            throw new IllegalStateException("environment proposal is already persisted");
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, proposal, completedEnvironmentActions);
    }

    public ExplorationCycle completeEnvironmentAction(UUID actionId) {
        Objects.requireNonNull(actionId);
        if (phase != Phase.ENVIRONMENT || pendingEnvironmentProposal == null)
            throw new IllegalStateException("environment proposal is not active");
        if (completedEnvironmentActions.contains(actionId)) return this;
        Set<UUID> completed = new HashSet<>(completedEnvironmentActions);
        completed.add(actionId);
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, phase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds, pendingEnvironmentProposal, completed);
    }

    /** A stopping event closes this group's movement turn; no remaining route is replayed. */
    public ExplorationCycle finishGroupTurn(UUID groupId, boolean interrupted) {
        Objects.requireNonNull(groupId);
        if (!initiativeByGroup.containsKey(groupId)) throw new IllegalArgumentException("group is not in this cycle");
        if (phase != Phase.GROUP_TURNS) throw new IllegalStateException("group turns are not active");
        if (completedGroups.contains(groupId)) return this;
        if (!groupId.equals(currentGroupId())) throw new IllegalStateException("groups must finish in initiative order");
        Set<UUID> completed = new HashSet<>(completedGroups);
        completed.add(groupId);
        Phase nextPhase = completed.size() == groupOrder.size() ? Phase.ENVIRONMENT : Phase.GROUP_TURNS;
        return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completed, nextPhase,
                environmentPhase, completedEnvironmentCommands, elapsedSeconds);
    }

    /** Uses the stable phase command ID as the retry key and advances time only after all phases finish. */
    public ExplorationCycle completeEnvironmentPhase(UUID commandId) {
        Objects.requireNonNull(commandId);
        if (phase != Phase.ENVIRONMENT) throw new IllegalStateException("environment phase is not active");
        if (completedEnvironmentCommands.contains(commandId)) return this;
        if (!currentPhaseCommandId().equals(commandId)) throw new IllegalArgumentException("environment command id is not current");
        Set<UUID> completed = new HashSet<>(completedEnvironmentCommands);
        completed.add(commandId);
        EnvironmentPhase[] phases = EnvironmentPhase.values();
        int nextIndex = environmentPhase.ordinal() + 1;
        if (nextIndex < phases.length) {
            return new ExplorationCycle(id, cycleNumber, initiativeByGroup, groupOrder, completedGroups, Phase.ENVIRONMENT,
                    phases[nextIndex], completed, elapsedSeconds, null, Set.of());
        }
        return new ExplorationCycle(id, cycleNumber + 1, initiativeByGroup, groupOrder, Set.of(), Phase.GROUP_TURNS,
                EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION, completed, elapsedSeconds + 6, null, Set.of());
    }
}
