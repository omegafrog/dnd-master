package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Owns candidate requests, three-attempt validation, and activation policy. */
public final class ScenarioSpatialFeaturePreparationService {
    public static final int MAX_ATTEMPTS = 3;
    private final ScenarioSpatialFeaturePlacementModelPort model;

    public ScenarioSpatialFeaturePreparationService(ScenarioSpatialFeaturePlacementModelPort model) {
        this.model = Objects.requireNonNull(model, "placement model must not be null");
    }

    public ScenarioSpatialFeaturePreparationResult prepare(MapDefinition map) {
        Objects.requireNonNull(map, "map must not be null");
        if (map.spatialFeatures().isEmpty()) return new ScenarioSpatialFeaturePreparationResult(true, List.of(), List.of(), List.of(), 0);
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ScenarioSpatialFeaturePlacementModelPort.Proposal proposal;
            try {
                proposal = model.propose(new ScenarioSpatialFeaturePlacementModelPort.Context(map, attempt, failures));
            } catch (RuntimeException exception) {
                failures.add("spatial placement proposal was unavailable");
                continue;
            }
            Validation validation = validate(map, proposal);
            if (validation.requiredFailures.isEmpty()) {
                warnings.addAll(validation.optionalFailures);
                return new ScenarioSpatialFeaturePreparationResult(true, validation.placements, warnings, failures, attempt);
            }
            failures.addAll(validation.requiredFailures);
            warnings.addAll(validation.optionalFailures);
        }
        return new ScenarioSpatialFeaturePreparationResult(false, List.of(), warnings, failures, MAX_ATTEMPTS);
    }

    private static Validation validate(MapDefinition map, ScenarioSpatialFeaturePlacementModelPort.Proposal proposal) {
        List<ScenarioSpatialFeaturePreparationResult.Placement> placements = new ArrayList<>();
        List<String> requiredFailures = new ArrayList<>();
        List<String> optionalFailures = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ScenarioSpatialFeaturePlacementModelPort.Candidate candidate : proposal == null ? List.<ScenarioSpatialFeaturePlacementModelPort.Candidate>of() : proposal.candidates()) {
            MapDefinition.SpatialFeatureRequirement requirement = map.spatialFeatures().stream()
                    .filter(item -> item.featureId().equals(candidate.featureId())).findFirst().orElse(null);
            String failure = validate(candidate, requirement, map, seen);
            if (failure == null) placements.add(toPlacement(map, requirement, candidate));
            else if (requirement != null && requirement.required()) requiredFailures.add("required spatial feature could not be validated");
            else optionalFailures.add("optional spatial feature was omitted");
        }
        for (MapDefinition.SpatialFeatureRequirement requirement : map.spatialFeatures()) {
            if (placements.stream().noneMatch(item -> item.featureId().equals(requirement.featureId()))) {
                if (requirement.required()) requiredFailures.add("required spatial feature was not proposed");
                else optionalFailures.add("optional spatial feature was omitted");
            }
        }
        return new Validation(placements, requiredFailures, optionalFailures);
    }

    private static String validate(ScenarioSpatialFeaturePlacementModelPort.Candidate candidate,
            MapDefinition.SpatialFeatureRequirement requirement, MapDefinition map, Set<UUID> seen) {
        if (requirement == null) return "candidate is not in the locked Story Plan";
        if (map.source().scenarioPackageVersion().isBlank() || requirement.resolutionUnitId().isBlank()) {
            return "structured spatial evidence is incomplete";
        }
        if (!seen.add(candidate.featureId())) return "duplicate feature candidate";
        if (!requirement.type().equalsIgnoreCase(candidate.type())) return "candidate type is not locked by the Story Plan";
        if (requirement.required() != candidate.required()) return "candidate required policy is not locked by the Story Plan";
        if (candidate.cells().isEmpty()) return "candidate has no cells";
        Set<String> allowed = Set.copyOf(requirement.authoritativeCells());
        if (allowed.isEmpty() || candidate.cells().stream().anyMatch(cell -> !allowed.contains(cell))) return "candidate cell is outside locked evidence";
        Set<String> obstacles = Set.copyOf(map.obstacles());
        if (candidate.cells().stream().anyMatch(obstacles::contains)) return "candidate cell is blocked";
        return null;
    }

    private static ScenarioSpatialFeaturePreparationResult.Placement toPlacement(MapDefinition map,
            MapDefinition.SpatialFeatureRequirement requirement, ScenarioSpatialFeaturePlacementModelPort.Candidate candidate) {
        MapDefinition.SpatialFeatureEvidence evidence = MapDefinition.SpatialFeatureEvidence.from(
                map.source(), requirement.resolutionUnitId(), requirement.evidenceReferences().getFirst(), requirement.authoritativeCells());
        return new ScenarioSpatialFeaturePreparationResult.Placement(requirement.featureId(), requirement.type().toUpperCase(Locale.ROOT),
                requirement.required(), candidate.cells(), new ScenarioSpatialFeaturePreparationResult.Evidence(
                        evidence.sourceDocumentId(), evidence.sourceExtractionVersion(), evidence.sourceLocator(),
                        evidence.resolutionUnitId(), evidence.scenarioPackageVersion(), evidence.allowedCells()),
                requirement.detectionRuleReference(), requirement.detectionDifficulty(), requirement.detectionMode(),
                requirement.triggers(), requirement.durationTurns(), requirement.removalPolicy(), requirement.overlapAllowed(), requirement.repeatable());
    }

    private record Validation(List<ScenarioSpatialFeaturePreparationResult.Placement> placements,
            List<String> requiredFailures, List<String> optionalFailures) {}
}
