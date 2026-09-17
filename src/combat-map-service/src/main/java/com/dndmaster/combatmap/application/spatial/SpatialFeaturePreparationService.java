package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureProvenance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Story Plan 공간 요소를 세 번 이내 검증하고, 성공한 batch만 지도에 반영한다. */
public final class SpatialFeaturePreparationService {
    public static final int MAX_ATTEMPTS = 3;
    private final SpatialFeaturePlacementModelPort model;

    public SpatialFeaturePreparationService(SpatialFeaturePlacementModelPort model) {
        this.model = Objects.requireNonNull(model, "placement model must not be null");
    }

    public Result prepare(CombatMap map, String storyPlanReference, long createdTurn) {
        Objects.requireNonNull(map, "map must not be null");
        if (createdTurn < 0) throw new IllegalArgumentException("created turn must not be negative");
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            SpatialFeaturePlacementProposal proposal = model.propose(
                    new SpatialFeaturePlacementContext(map, storyPlanReference, attempt, failures));
            Validation validation = validate(map, proposal);
            if (validation.requiredFailures().isEmpty()) {
                List<SpatialFeature> features = validation.validCandidates().stream()
                        .map(candidate -> SpatialFeature.hidden(candidate.featureId(), candidate.type(), candidate.cells(),
                                candidate.detectionSpec(), candidate.triggers(), SpatialFeatureProvenance.storyPlan(
                                        candidate.evidenceReference(), createdTurn, map.version())))
                        .toList();
                map.materializeSpatialFeatures(features);
                map.completeSpatialPreparation();
                warnings.addAll(validation.optionalFailures());
                return new Result(map, attempt, true, warnings, failures);
            }
            failures.addAll(validation.requiredFailures());
            warnings.addAll(validation.optionalFailures());
        }
        map.blockSpatialPreparation();
        return new Result(map, MAX_ATTEMPTS, false, warnings, failures);
    }

    private static Validation validate(CombatMap map, SpatialFeaturePlacementProposal proposal) {
        List<SpatialFeaturePlacementProposal.Candidate> valid = new ArrayList<>();
        List<String> requiredFailures = new ArrayList<>();
        List<String> optionalFailures = new ArrayList<>();
        Set<java.util.UUID> ids = new HashSet<>();
        for (SpatialFeaturePlacementProposal.Candidate candidate : proposal.candidates()) {
            String failure = validateCandidate(map, candidate, ids);
            if (failure == null) valid.add(candidate);
            else if (candidate.required()) requiredFailures.add(candidate.featureId() + ": " + failure);
            else optionalFailures.add(candidate.featureId() + ": " + failure);
        }
        return new Validation(valid, requiredFailures, optionalFailures);
    }

    private static String validateCandidate(CombatMap map, SpatialFeaturePlacementProposal.Candidate candidate,
            Set<java.util.UUID> ids) {
        if (!ids.add(candidate.featureId()) || map.spatialFeatures().stream().anyMatch(feature -> feature.id().equals(candidate.featureId()))) {
            return "duplicate feature id";
        }
        if (candidate.cells().isEmpty()) return "no evidence-based occupied cell was proposed";
        if (candidate.evidenceReference().isBlank()) return "placement evidence is required";
        if (candidate.cells().stream().anyMatch(cell -> !map.grid().contains(cell))) return "occupied cell is outside the map";
        if (candidate.cells().stream().anyMatch(map.obstacles()::contains)) return "occupied cell is blocked";
        return null;
    }

    private record Validation(List<SpatialFeaturePlacementProposal.Candidate> validCandidates,
            List<String> requiredFailures, List<String> optionalFailures) {}

    public record Result(CombatMap map, int attempts, boolean activationAllowed,
            List<String> warnings, List<String> failures) {
        public Result {
            warnings = List.copyOf(warnings);
            failures = List.copyOf(failures);
        }
    }
}
