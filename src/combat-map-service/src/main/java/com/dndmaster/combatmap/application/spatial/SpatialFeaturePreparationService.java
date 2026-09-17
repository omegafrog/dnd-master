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
        return prepare(map, SpatialFeaturePreparationInput.empty(storyPlanReference), createdTurn);
    }

    public Result prepare(CombatMap map, SpatialFeaturePreparationInput input, long createdTurn) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(input, "spatial preparation input must not be null");
        if (createdTurn < 0) throw new IllegalArgumentException("created turn must not be negative");
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            SpatialFeaturePlacementProposal proposal;
            try {
                proposal = model.propose(
                        new SpatialFeaturePlacementContext(map, input.storyPlanReference(), attempt, failures, input.requirements()));
            } catch (RuntimeException exception) {
                boolean required = input.requirements().stream().anyMatch(SpatialFeaturePreparationInput.Requirement::required);
                if (required || input.requirements().isEmpty()) {
                    failures.add("AI placement proposal failed on attempt " + attempt);
                } else {
                    warnings.add("optional spatial feature proposal failed on attempt " + attempt);
                    map.completeSpatialPreparation();
                    return new Result(map, attempt, true, warnings, failures);
                }
                continue;
            }
            Validation validation = validate(map, proposal, input.requirements());
            if (validation.requiredFailures().isEmpty()) {
                List<SpatialFeature> features = validation.validCandidates().stream()
                        .map(candidate -> SpatialFeature.hidden(candidate.candidate().featureId(), candidate.requirement().type(),
                                candidate.candidate().cells(), candidate.requirement().detectionSpec(), candidate.requirement().triggers(),
                                SpatialFeatureProvenance.storyPlan(candidate.candidate().evidenceReference(), createdTurn, map.version())))
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

    private static Validation validate(CombatMap map, SpatialFeaturePlacementProposal proposal,
            List<SpatialFeaturePreparationInput.Requirement> requirements) {
        List<ValidCandidate> valid = new ArrayList<>();
        List<String> requiredFailures = new ArrayList<>();
        List<String> optionalFailures = new ArrayList<>();
        Set<java.util.UUID> ids = new HashSet<>();
        if (proposal == null) {
            List<String> required = requirements.stream().filter(SpatialFeaturePreparationInput.Requirement::required)
                    .map(requirement -> requirement.featureId() + ": AI placement proposal is missing").toList();
            List<String> optional = requirements.stream().filter(requirement -> !requirement.required())
                    .map(requirement -> requirement.featureId() + ": optional feature was omitted").toList();
            return new Validation(List.of(), required, optional);
        }
        for (SpatialFeaturePlacementProposal.Candidate candidate : proposal.candidates()) {
            SpatialFeaturePreparationInput.Requirement requirement = requirements.stream()
                    .filter(item -> item.featureId().equals(candidate.featureId())).findFirst().orElse(null);
            String failure = validateCandidate(map, candidate, requirement, ids);
            if (failure == null) valid.add(new ValidCandidate(candidate, requirement));
            else if (candidate.required() || (requirement != null && requirement.required())) requiredFailures.add(candidate.featureId() + ": " + failure);
            else optionalFailures.add(candidate.featureId() + ": " + failure);
        }
        for (SpatialFeaturePreparationInput.Requirement requirement : requirements) {
            if (valid.stream().noneMatch(candidate -> candidate.candidate().featureId().equals(requirement.featureId()))) {
                String failure = "required feature was not proposed";
                if (requirement.required()) requiredFailures.add(requirement.featureId() + ": " + failure);
                else optionalFailures.add(requirement.featureId() + ": " + failure);
            }
        }
        return new Validation(valid, requiredFailures, optionalFailures);
    }

    private static String validateCandidate(CombatMap map, SpatialFeaturePlacementProposal.Candidate candidate,
            SpatialFeaturePreparationInput.Requirement requirement, Set<java.util.UUID> ids) {
        if (!ids.add(candidate.featureId()) || map.spatialFeatures().stream().anyMatch(feature -> feature.id().equals(candidate.featureId()))) {
            return "duplicate feature id";
        }
        if (requirement == null && !candidate.evidenceReference().isBlank()) return "feature is not part of authoritative preparation input";
        if (requirement == null) return "feature is not part of authoritative preparation input";
        if (candidate.type() != requirement.type()) return "feature type does not match authoritative input";
        if (candidate.required() != requirement.required()) return "required policy does not match authoritative input";
        if (candidate.cells().isEmpty()) return "no evidence-based occupied cell was proposed";
        if (candidate.evidenceReference().isBlank()
                || !requirement.evidenceReferences().contains(candidate.evidenceReference())) {
            return "placement evidence does not match authoritative source";
        }
        if (candidate.cells().stream().anyMatch(cell -> !map.grid().contains(cell))) return "occupied cell is outside the map";
        if (candidate.cells().stream().anyMatch(map.obstacles()::contains)) return "occupied cell is blocked";
        return null;
    }

    private record ValidCandidate(SpatialFeaturePlacementProposal.Candidate candidate,
            SpatialFeaturePreparationInput.Requirement requirement) {}

    private record Validation(List<ValidCandidate> validCandidates,
            List<String> requiredFailures, List<String> optionalFailures) {}

    public record Result(CombatMap map, int attempts, boolean activationAllowed,
            List<String> warnings, List<String> failures) {
        public Result {
            warnings = List.copyOf(warnings);
            failures = List.copyOf(failures);
        }
    }
}
