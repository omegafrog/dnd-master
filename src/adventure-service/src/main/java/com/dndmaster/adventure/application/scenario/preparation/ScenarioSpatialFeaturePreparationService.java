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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ScenarioSpatialFeaturePreparationService.class);
    public static final int MAX_ATTEMPTS = 3;
    private final ScenarioSpatialFeaturePlacementModelPort model;

    public ScenarioSpatialFeaturePreparationService(ScenarioSpatialFeaturePlacementModelPort model) {
        this.model = Objects.requireNonNull(model, "placement model must not be null");
    }

    public ScenarioSpatialFeaturePreparationResult prepare(MapDefinition map, UUID soloPlayerId) {
        return prepare(map, soloPlayerId, List.of());
    }

    public ScenarioSpatialFeaturePreparationResult prepare(MapDefinition map, UUID soloPlayerId,
            List<ScenarioSpatialFeaturePlacementModelPort.EncounterRequirement> encounters) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(soloPlayerId, "solo player id must not be null");
        encounters = List.copyOf(Objects.requireNonNull(encounters, "encounters must not be null"));
        long startedAt = System.nanoTime();
        if (diagnosticsEnabled()) LOGGER.info("dev_situation_initial_placement outcome=started mapDefinitionId={} expectedFeatureCount={} expectedEncounterCount={} expectedEnemyCount={} attemptLimit={}",
                map.id(), map.spatialFeatures().size(), encounters.size(), encounters.stream().mapToInt(ScenarioSpatialFeaturePlacementModelPort.EncounterRequirement::count).sum(), MAX_ATTEMPTS);
        if (map.spatialFeatures().isEmpty() && encounters.isEmpty()) {
            if (diagnosticsEnabled()) LOGGER.info("dev_situation_initial_placement outcome=completed mapDefinitionId={} expectedFeatureCount=0 actualFeatureCount=0 expectedEnemyCount=0 actualEnemyCount=0 attempts=0 elapsedMs={} recoveryAction=continue_without_initial_placements",
                    map.id(), elapsedMs(startedAt));
            return new ScenarioSpatialFeaturePreparationResult(true, List.of(), List.of(), List.of(), 0);
        }
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ScenarioSpatialFeaturePlacementModelPort.Proposal proposal;
            try {
                if (diagnosticsEnabled()) LOGGER.info("dev_situation_initial_placement outcome=attempt_started mapDefinitionId={} attempt={} expectedEncounterCount={} expectedEnemyCount={} previousFailureCount={}",
                        map.id(), attempt, encounters.size(), encounters.stream().mapToInt(ScenarioSpatialFeaturePlacementModelPort.EncounterRequirement::count).sum(), failures.size());
                proposal = model.propose(new ScenarioSpatialFeaturePlacementModelPort.Context(soloPlayerId, map, attempt, failures, encounters));
            } catch (RuntimeException exception) {
                failures.add("spatial placement proposal was unavailable");
                if (diagnosticsEnabled()) LOGGER.info("dev_situation_initial_placement outcome=attempt_failed mapDefinitionId={} attempt={} expectedFeatureCount={} expectedEncounterCount={} actualProposal=unavailable failureClass={} nextAction={} elapsedMs={}",
                        map.id(), attempt, map.spatialFeatures().size(), encounters.size(), exception.getClass().getSimpleName(),
                        attempt < MAX_ATTEMPTS ? "retry_with_failure_reason" : "block_map_entry", elapsedMs(startedAt));
                continue;
            }
            Validation validation = validate(map, proposal);
            EnemyValidation enemyValidation = validateEnemies(encounters, proposal);
            if (validation.requiredFailures.isEmpty()) {
                warnings.addAll(validation.optionalFailures);
                warnings.addAll(enemyValidation.optionalFailures());
                if (!enemyValidation.requiredFailures().isEmpty()) {
                    failures.addAll(enemyValidation.requiredFailures());
                    continue;
                }
                var result = new ScenarioSpatialFeaturePreparationResult(true, validation.placements, enemyValidation.placements(), warnings, failures, attempt);
                if (diagnosticsEnabled()) LOGGER.info("dev_situation_initial_placement outcome=completed mapDefinitionId={} expectedFeatureCount={} actualFeatureCount={} expectedEnemyCount={} actualEnemyCount={} attempts={} warningCount={} elapsedMs={} recoveryAction=apply_validated_batch",
                        map.id(), map.spatialFeatures().stream().filter(MapDefinition.SpatialFeatureRequirement::required).count(),
                        result.placements().size(), encounters.stream().mapToInt(ScenarioSpatialFeaturePlacementModelPort.EncounterRequirement::count).sum(),
                        result.enemies().stream().mapToInt(item -> item.positions().size()).sum(), attempt, warnings.size(), elapsedMs(startedAt));
                return result;
            }
            failures.addAll(validation.requiredFailures);
            warnings.addAll(validation.optionalFailures);
            failures.addAll(enemyValidation.requiredFailures());
            warnings.addAll(enemyValidation.optionalFailures());
        }
        if (diagnosticsEnabled()) LOGGER.info("dev_situation_initial_placement outcome=blocked mapDefinitionId={} expectedFeatureCount={} expectedEnemyCount={} actualFeatureCount=0 actualEnemyCount=0 attempts={} failureCount={} elapsedMs={} recoveryAction=retain_map_inactive",
                map.id(), map.spatialFeatures().stream().filter(MapDefinition.SpatialFeatureRequirement::required).count(),
                encounters.stream().mapToInt(ScenarioSpatialFeaturePlacementModelPort.EncounterRequirement::count).sum(),
                MAX_ATTEMPTS, failures.size(), elapsedMs(startedAt));
        return new ScenarioSpatialFeaturePreparationResult(false, List.of(), List.of(), warnings, failures, MAX_ATTEMPTS);
    }

    private static boolean diagnosticsEnabled() {
        return Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")));
    }
    private static long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static EnemyValidation validateEnemies(List<ScenarioSpatialFeaturePlacementModelPort.EncounterRequirement> requirements,
            ScenarioSpatialFeaturePlacementModelPort.Proposal proposal) {
        List<ScenarioSpatialFeaturePreparationResult.EnemyPlacement> placements = new ArrayList<>();
        List<String> requiredFailures = new ArrayList<>();
        List<String> optionalFailures = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var candidate : proposal == null ? List.<ScenarioSpatialFeaturePlacementModelPort.EnemyCandidate>of() : proposal.enemies()) {
            var requirement = requirements.stream().filter(item -> item.scenarioId().equals(candidate.scenarioId())).findFirst().orElse(null);
            if (requirement == null || !seen.add(candidate.scenarioId())
                    || requirement != null && candidate.positions().size() != requirement.count()) {
                (requirement != null && requirement.required() ? requiredFailures : optionalFailures)
                        .add("enemy placement did not match a supplied encounter and count");
                continue;
            }
            if (candidate.positions().stream().distinct().count() != candidate.positions().size()) {
                (requirement.required() ? requiredFailures : optionalFailures).add("enemy placements overlap");
                continue;
            }
            placements.add(new ScenarioSpatialFeaturePreparationResult.EnemyPlacement(
                    requirement.scenarioId(), requirement.enemyKey(), candidate.positions()));
        }
        for (var requirement : requirements) if (requirement.required() && !seen.contains(requirement.scenarioId()))
            requiredFailures.add("required encounter placement was not proposed");
        return new EnemyValidation(placements, requiredFailures, optionalFailures);
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
                requirement.triggers(), requirement.durationTurns(), requirement.removalPolicy(), requirement.overlapAllowed(), requirement.repeatable(),
                requirement.detectionDiceExpression(), requirement.detectionModifier());
    }

    private record Validation(List<ScenarioSpatialFeaturePreparationResult.Placement> placements,
            List<String> requiredFailures, List<String> optionalFailures) {}
    private record EnemyValidation(List<ScenarioSpatialFeaturePreparationResult.EnemyPlacement> placements,
            List<String> requiredFailures, List<String> optionalFailures) {}
}
