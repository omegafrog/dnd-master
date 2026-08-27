package com.dndmaster.aigamemaster.application.rule;

import com.dndmaster.aigamemaster.application.GroundingViolationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class GroundedRuleAnswerService {
    private final RuleAnswerModelPort model;

    public GroundedRuleAnswerService(RuleAnswerModelPort model) {
        this.model = Objects.requireNonNull(model);
    }

    public RuleAnswerOutput compose(RuleAnswerRequest request) {
        RuleAnswerOutput output = model.compose(request);
        if (output.evidenceStatus() != request.evidenceStatus()) {
            throw new GroundingViolationException("model hid or changed evidence status");
        }
        Set<Citation> allowed = request.evidence().stream().map(SourceEvidence::citation).collect(Collectors.toUnmodifiableSet());
        if (output.evidenceStatus() == EvidenceStatus.SUFFICIENT) {
            if (output.conclusion() == null || output.conclusion().isBlank() || output.conclusionCitations().isEmpty()) {
                throw new GroundingViolationException("uncited conclusion forbidden");
            }
            validate(output.conclusionCitations(), allowed);
            if (!output.candidates().isEmpty()) {
                throw new GroundingViolationException("sufficient answer must not disclose conflicting candidates");
            }
        } else {
            if (output.conclusion() != null && !output.conclusion().isBlank()) {
                throw new GroundingViolationException("uncertain evidence cannot produce conclusion");
            }
            if (!output.uncertaintyDisclosed() || output.candidates().isEmpty()) {
                throw new GroundingViolationException("uncertainty and candidates must be disclosed");
            }
            for (RuleCandidate candidate : output.candidates()) {
                if (candidate.citations().isEmpty()) throw new GroundingViolationException("candidate citation required");
                validate(candidate.citations(), allowed);
            }
        }
        validateBindings(output, request.evidence());
        return output;
    }

    private static void validate(List<Citation> citations, Set<Citation> allowed) {
        if (citations.stream().anyMatch(citation -> !allowed.contains(citation))) {
            throw new GroundingViolationException("CITATION_NOT_IN_EVIDENCE_PACK");
        }
    }

    private static void validateBindings(RuleAnswerOutput output, List<SourceEvidence> evidence) {
        if (output.citationBindings().isEmpty()) return;
        Map<String, SourceEvidence> byKey = evidence.stream().filter(evidenceItem -> evidenceItem.citationKey() != null)
                .collect(Collectors.toMap(SourceEvidence::citationKey, evidenceItem -> evidenceItem, (first, ignored) -> first));
        for (GmCitationBinding binding : output.citationBindings()) {
            SourceEvidence source = byKey.get(binding.citationKey());
            if (source == null) throw new GroundingViolationException("CITATION_NOT_IN_EVIDENCE_PACK");
            if (!supports(binding.claimText(), source.excerpt())) {
                throw new GroundingViolationException("UNSUPPORTED_CLAIM_CITATION");
            }
        }
    }

    private static boolean supports(String claim, String excerpt) {
        Set<String> claimTokens = tokens(claim);
        Set<String> excerptTokens = tokens(excerpt);
        long overlap = claimTokens.stream().filter(excerptTokens::contains).count();
        return !claimTokens.isEmpty() && (overlap >= Math.min(2, claimTokens.size()) || excerptTokens.containsAll(claimTokens));
    }

    private static Set<String> tokens(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .map(String::trim).map(GroundedRuleAnswerService::normalizeToken)
                .filter(token -> token.length() > 1).collect(Collectors.toSet());
    }

    private static String normalizeToken(String token) {
        return token.replaceFirst("(에서는|에서|으로|에게|부터|까지|입니다|합니다|됩니다|은|는|이|가|을|를|에|의|로|과|와|도|만)$", "");
    }
}
