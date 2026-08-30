package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.CandidateCompleteness;
import com.dndmaster.adventure.domain.scenario.CandidateRecoverability;
import com.dndmaster.adventure.domain.scenario.CandidateValidation;
import com.dndmaster.adventure.domain.scenario.CompilationCandidate;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Maps the legacy unit projection to durable typed candidate diagnostics. */
public final class CompilationCandidateFactory {
    private CompilationCandidateFactory() {}

    public static List<CompilationCandidate> from(UUID compilationId,
            List<? extends com.dndmaster.adventure.domain.scenario.ResolutionCandidate> requested,
            List<ScenarioResolutionUnit> validated) {
        List<CompilationCandidate> result = new ArrayList<>();
        for (int index = 0; index < validated.size(); index++) {
            ScenarioResolutionUnit unit = validated.get(index);
            var source = index < requested.size() ? requested.get(index) : null;
            String key = source == null ? "candidate-" + index : source.candidateKey();
            String baseKey = key;
            int suffix = 1;
            while (result.stream().map(CompilationCandidate::candidateKey).toList().contains(key)) {
                key = baseKey + "-" + suffix++;
            }
            List<CandidateValidation> validations = unit.validationMessages().stream()
                    .map(message -> validation(message, unit.status())).toList();
            CandidateCompleteness completeness = switch (unit.status()) {
                case COMPLETE -> CandidateCompleteness.COMPLETE;
                case PARTIAL -> CandidateCompleteness.PARTIAL;
                case INVALID -> CandidateCompleteness.INVALID;
            };
            CandidateRecoverability recoverability = validations.isEmpty()
                    ? CandidateRecoverability.NON_REPAIRABLE
                    : validations.stream().map(CandidateValidation::recoverability)
                            .reduce(CompilationCandidateFactory::leastRecoverable)
                            .orElse(CandidateRecoverability.NON_REPAIRABLE);
            result.add(CompilationCandidate.of(compilationId, key,
                    unit.kind() == null ? "UNKNOWN" : unit.kind().name(),
                    source == null || source.required(), completeness, validations, recoverability,
                    reference(unit), reference(unit)));
        }
        return List.copyOf(result);
    }

    private static CandidateValidation validation(String message,
            com.dndmaster.adventure.domain.scenario.ResolutionStatus status) {
        String code = switch (message) {
            case "dice expression is invalid" -> "DICE_EXPRESSION_INVALID";
            case "recharge range is invalid" -> "RECHARGE_RANGE_INVALID";
            case "source reference is missing" -> "SOURCE_REFERENCE_MISSING";
            case "source excerpt is unavailable" -> "SOURCE_EXCERPT_UNAVAILABLE";
            case "source quote cannot be verified against referenced excerpt" -> "SOURCE_QUOTE_UNVERIFIED";
            case "candidate is null" -> "CANDIDATE_MISSING";
            default -> (status == com.dndmaster.adventure.domain.scenario.ResolutionStatus.INVALID ? "INVALID_" : "INCOMPLETE_")
                    + message.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        };
        CandidateRecoverability recoverability = switch (code) {
            case "DICE_EXPRESSION_INVALID", "RECHARGE_RANGE_INVALID", "SOURCE_EXCERPT_UNAVAILABLE" -> CandidateRecoverability.REPAIRABLE;
            case "SOURCE_QUOTE_UNVERIFIED" -> CandidateRecoverability.MAYBE_REPAIRABLE;
            default -> CandidateRecoverability.NON_REPAIRABLE;
        };
        return new CandidateValidation(code, message, recoverability);
    }

    private static CandidateRecoverability leastRecoverable(CandidateRecoverability left,
            CandidateRecoverability right) {
        return rank(left) >= rank(right) ? left : right;
    }

    private static int rank(CandidateRecoverability value) {
        return switch (value) {
            case REPAIRABLE -> 0;
            case MAYBE_REPAIRABLE -> 1;
            case NON_REPAIRABLE -> 2;
        };
    }

    private static String reference(ScenarioResolutionUnit unit) {
        return unit.sourceRefs().isEmpty() ? null : unit.sourceRefs().getFirst().locator();
    }
}
