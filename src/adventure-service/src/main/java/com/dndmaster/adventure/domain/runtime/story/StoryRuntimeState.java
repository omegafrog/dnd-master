package com.dndmaster.adventure.domain.runtime.story;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Canonical narrative progression for one materialized stage. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class StoryRuntimeState {
    private final long version;
    private final UUID scenarioPackageId;
    private final long backboneRevision;
    private final String stageId;
    private final long detailedStageRevision;
    private final Map<String, SituationStatus> situationStatuses;
    private final Map<String, RevelationStatus> revelationStatuses;
    private final Map<String, PressureState> pressureStates;
    private final String activeSituationId;
    private final boolean openingPresented;
    private final Set<UUID> processedProposalIds;

    public StoryRuntimeState(long version, UUID scenarioPackageId, long backboneRevision, String stageId,
            long detailedStageRevision, Map<String, SituationStatus> situationStatuses,
            Map<String, RevelationStatus> revelationStatuses, Map<String, PressureState> pressureStates,
            String activeSituationId, boolean openingPresented, Set<UUID> processedProposalIds) {
        if (version < 0) throw new IllegalArgumentException("runtime version must not be negative");
        this.version = version;
        this.scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (backboneRevision < 1 || detailedStageRevision < 1) throw new IllegalArgumentException("stage revisions must be positive");
        this.backboneRevision = backboneRevision;
        this.stageId = required(stageId, "stage id");
        this.detailedStageRevision = detailedStageRevision;
        this.situationStatuses = Map.copyOf(situationStatuses == null ? Map.of() : situationStatuses);
        this.revelationStatuses = Map.copyOf(revelationStatuses == null ? Map.of() : revelationStatuses);
        this.pressureStates = Map.copyOf(pressureStates == null ? Map.of() : pressureStates);
        this.activeSituationId = activeSituationId == null || activeSituationId.isBlank() ? null : activeSituationId.trim();
        this.openingPresented = openingPresented;
        this.processedProposalIds = Set.copyOf(processedProposalIds == null ? Set.of() : processedProposalIds);
        if (this.activeSituationId != null && this.situationStatuses.get(this.activeSituationId) != SituationStatus.AVAILABLE) {
            throw new IllegalArgumentException("active situation must be available");
        }
    }

    public static StoryRuntimeState start(DetailedStage stage) {
        Objects.requireNonNull(stage, "detailed stage must not be null");
        Map<String, SituationStatus> situations = new LinkedHashMap<>();
        stage.situations().forEach(situation -> situations.put(situation.situationId(), SituationStatus.AVAILABLE));
        Map<String, RevelationStatus> revelations = new LinkedHashMap<>();
        stage.revelations().forEach(revelation -> revelations.put(revelation.revelationId(), RevelationStatus.UNKNOWN));
        Map<String, PressureState> pressures = Map.of(stage.pressure().pressureId(), PressureState.dormant(stage.pressure().pressureId()));
        String opening = stage.situations().stream().filter(situation -> situation.opening()).map(situation -> situation.situationId())
                .findFirst().orElse(null);
        return new StoryRuntimeState(0, stage.scenarioPackageId(), stage.backboneRevision(), stage.stageId(), stage.revision(),
                situations, revelations, pressures, opening, opening != null, Set.of());
    }

    public long version() { return version; }
    public UUID scenarioPackageId() { return scenarioPackageId; }
    public long backboneRevision() { return backboneRevision; }
    public String stageId() { return stageId; }
    public long detailedStageRevision() { return detailedStageRevision; }
    public Map<String, SituationStatus> situationStatuses() { return situationStatuses; }
    public Map<String, RevelationStatus> revelationStatuses() { return revelationStatuses; }
    public Set<String> learnedRevelationIds() { return revelationStatuses.entrySet().stream()
            .filter(entry -> entry.getValue() == RevelationStatus.LEARNED).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    public Map<String, PressureState> pressureStates() { return pressureStates; }
    public String activeSituationId() { return activeSituationId; }
    public boolean openingPresented() { return openingPresented; }
    public Set<UUID> processedProposalIds() { return processedProposalIds; }
    @JsonIgnore
    public boolean isOpenPlay() { return activeSituationId == null; }

    StoryRuntimeState evolve(Map<String, SituationStatus> situations, Map<String, RevelationStatus> revelations,
            Map<String, PressureState> pressures, String active, boolean opening, UUID proposalId) {
        Set<UUID> processed = new LinkedHashSet<>(processedProposalIds);
        if (proposalId != null) processed.add(proposalId);
        return new StoryRuntimeState(version + 1, scenarioPackageId, backboneRevision, stageId, detailedStageRevision,
                situations, revelations, pressures, active, opening, processed);
    }

    StoryRuntimeState withProcessedProposal(UUID proposalId) {
        Set<UUID> processed = new LinkedHashSet<>(processedProposalIds);
        processed.add(proposalId);
        return new StoryRuntimeState(version, scenarioPackageId, backboneRevision, stageId, detailedStageRevision,
                situationStatuses, revelationStatuses, pressureStates, activeSituationId, openingPresented, processed);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
