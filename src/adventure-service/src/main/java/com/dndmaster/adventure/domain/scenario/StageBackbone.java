package com.dndmaster.adventure.domain.scenario;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record StageBackbone(UUID scenarioPackageId, long revision, List<StageBackboneEntry> stages,
                            List<ScenarioSourceReference> sourceRefs, List<RequiredSituation> requiredSituations) {
    public StageBackbone(UUID scenarioPackageId, long revision, List<StageBackboneEntry> stages,
            List<ScenarioSourceReference> sourceRefs) {
        this(scenarioPackageId, revision, stages, sourceRefs, List.of());
    }

    public StageBackbone {
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (revision < 1) throw new IllegalArgumentException("backbone revision must be positive");
        stages = List.copyOf(Objects.requireNonNull(stages, "stages must not be null"));
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "backbone source refs must not be null"));
        requiredSituations = List.copyOf(Objects.requireNonNull(requiredSituations, "required situations must not be null"));
        if (stages.isEmpty()) throw new IllegalArgumentException("backbone must contain a stage");
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < stages.size(); index++) {
            StageBackboneEntry stage = stages.get(index);
            if (!ids.add(stage.stageId())) throw new IllegalArgumentException("stage ids must be unique");
            if (stage.order() != index + 1) throw new IllegalArgumentException("stage order must be contiguous");
        }
        validateRequiredGraph(requiredSituations);
    }

    public static void validateRequiredGraph(List<RequiredSituation> requiredSituations) {
        if (requiredSituations.isEmpty()) return;
        Set<String> ids = requiredSituations.stream().map(RequiredSituation::situationId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> summaries = requiredSituations.stream().map(RequiredSituation::summary)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.size() != requiredSituations.size()) throw new IllegalArgumentException("required situation ids must be unique");
        if (summaries.size() != requiredSituations.size()) throw new IllegalArgumentException("required situation summaries must be unique");
        if (requiredSituations.stream().flatMap(item -> item.prerequisiteSituationIds().stream()).anyMatch(id -> !ids.contains(id)))
            throw new IllegalArgumentException("required situation has an unknown prerequisite");
        if (requiredSituations.stream().anyMatch(item -> item.prerequisiteSituationIds().contains(item.situationId())
                || item.prerequisiteSituationIds().stream().distinct().count() != item.prerequisiteSituationIds().size()))
            throw new IllegalArgumentException("required situation has a self or duplicate prerequisite");

        List<RequiredSituation> roots = requiredSituations.stream().filter(item -> item.prerequisiteSituationIds().isEmpty()).toList();
        List<RequiredSituation> endings = requiredSituations.stream().filter(RequiredSituation::isEnding).toList();
        if (roots.size() != 1) throw new IllegalArgumentException("required situation graph must have exactly one root");
        if (endings.isEmpty()) throw new IllegalArgumentException("required situation graph must include an explicit ending");

        java.util.Map<String, List<String>> successors = new java.util.HashMap<>();
        requiredSituations.forEach(item -> successors.put(item.situationId(), new java.util.ArrayList<>()));
        requiredSituations.forEach(item -> item.prerequisiteSituationIds().forEach(parent -> successors.get(parent).add(item.situationId())));
        Set<String> endingIds = endings.stream().map(RequiredSituation::situationId).collect(java.util.stream.Collectors.toSet());
        if (endings.stream().anyMatch(item -> !successors.get(item.situationId()).isEmpty()))
            throw new IllegalArgumentException("ending situation must not have successors");
        if (hasCycle(requiredSituations)) throw new IllegalArgumentException("required situation graph must be acyclic");
        for (RequiredSituation situation : requiredSituations) {
            if (!canReachEnding(situation.situationId(), successors, endingIds, new java.util.HashSet<>()))
                throw new IllegalArgumentException("every required situation must reach an explicit ending");
        }
    }

    private static boolean canReachEnding(String id, java.util.Map<String, List<String>> successors,
            Set<String> endings, Set<String> visited) {
        if (endings.contains(id)) return true;
        if (!visited.add(id)) return false;
        return successors.get(id).stream().anyMatch(next -> canReachEnding(next, successors, endings, visited));
    }

    private static boolean hasCycle(List<RequiredSituation> situations) {
        java.util.Map<String, List<String>> successors = new java.util.HashMap<>();
        situations.forEach(item -> successors.put(item.situationId(), new java.util.ArrayList<>()));
        situations.forEach(item -> item.prerequisiteSituationIds().forEach(parent -> successors.get(parent).add(item.situationId())));
        Set<String> visited = new java.util.HashSet<>();
        Set<String> visiting = new java.util.HashSet<>();
        return successors.keySet().stream().anyMatch(id -> hasCycle(id, successors, visited, visiting));
    }

    private static boolean hasCycle(String id, java.util.Map<String, List<String>> successors,
            Set<String> visited, Set<String> visiting) {
        if (visited.contains(id)) return false;
        if (!visiting.add(id)) return true;
        for (String next : successors.get(id)) if (hasCycle(next, successors, visited, visiting)) return true;
        visiting.remove(id);
        visited.add(id);
        return false;
    }
}
