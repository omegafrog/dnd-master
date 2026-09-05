package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Lockable hidden scenario structure published as a child of ScenarioPackage. */
public record ScenarioModel(
        int schemaVersion,
        List<ScenarioModelElement> actors,
        List<ScenarioModelElement> locations,
        List<ScenarioModelElement> objectives,
        List<ScenarioModelElement> revelations,
        List<ScenarioModelElement> encounters,
        List<ScenarioModelElement> relationships,
        List<ScenarioModelElement> resolutionCriteria,
        String startingSituation) {
    public ScenarioModel {
        if (schemaVersion <= 0) throw new IllegalArgumentException("scenario model schema version must be positive");
        actors = copy(actors); locations = copy(locations); objectives = copy(objectives);
        revelations = copy(revelations); encounters = copy(encounters); relationships = copy(relationships);
        resolutionCriteria = copy(resolutionCriteria);
        startingSituation = startingSituation == null ? "" : startingSituation.trim();
    }

    public static ScenarioModel empty() {
        return new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");
    }

    public boolean hasCoreResolutionInformation() {
        return !objectives.isEmpty() && !resolutionCriteria.isEmpty() && !startingSituation.isBlank();
    }

    /** Returns the immutable identity set used to validate agent citations. */
    public Set<String> elementIds() {
        return Stream.of(actors, locations, objectives, revelations, encounters, relationships, resolutionCriteria)
                .flatMap(List::stream)
                .map(ScenarioModelElement::elementId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean containsElement(String elementId) {
        return elementId != null && elementIds().contains(elementId);
    }

    public ScenarioModel withGeneratedCore(ScenarioCreativity policy) {
        if (policy == ScenarioCreativity.NONE || hasCoreResolutionInformation()) return this;
        ScenarioModelElement objective = new ScenarioModelElement("generated-objective", "objective",
                java.util.Map.of("value", "Resolve the scenario's central objective", "source", "GENERATED_" + policy), List.of());
        ScenarioModelElement resolution = new ScenarioModelElement("generated-resolution", "resolution",
                java.util.Map.of("value", "The central objective is resolved", "source", "GENERATED_" + policy), List.of());
        return new ScenarioModel(schemaVersion, actors, locations,
                objectives.isEmpty() ? List.of(objective) : objectives,
                revelations, encounters, relationships,
                resolutionCriteria.isEmpty() ? List.of(resolution) : resolutionCriteria,
                startingSituation.isBlank() ? "The adventure begins with the central objective unresolved." : startingSituation);
    }

    private static List<ScenarioModelElement> copy(List<ScenarioModelElement> elements) {
        return List.copyOf(Objects.requireNonNull(elements, "model elements are required"));
    }
}
