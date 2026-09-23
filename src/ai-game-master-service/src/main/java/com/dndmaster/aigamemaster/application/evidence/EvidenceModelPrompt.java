package com.dndmaster.aigamemaster.application.evidence;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps a fixed-model evidence request within its configured context window. */
final class EvidenceModelPrompt {
    private static final int MAX_EXCERPT_CHARACTERS = 240;

    private EvidenceModelPrompt() {}

    static Candidates candidates(List<EvidenceCandidate> candidates) {
        StringBuilder prompt = new StringBuilder("[");
        Map<String, String> modelIdByEvidenceId = new LinkedHashMap<>();
        Map<String, String> evidenceIdByModelId = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            EvidenceCandidate candidate = candidates.get(index);
            String modelId = "c" + (index + 1);
            modelIdByEvidenceId.put(candidate.evidenceId(), modelId);
            evidenceIdByModelId.put(modelId, candidate.evidenceId());
            if (index > 0) prompt.append(", ");
            prompt.append("{id=").append(modelId)
                    .append(", type=").append(candidate.documentType())
                    .append(", locator=").append(candidate.locator())
                    .append(", excerpt=").append(compact(candidate.excerpt())).append('}');
        }
        return new Candidates(prompt.append(']').toString(), Map.copyOf(modelIdByEvidenceId), Map.copyOf(evidenceIdByModelId));
    }

    record Candidates(String prompt, Map<String, String> modelIdByEvidenceId, Map<String, String> evidenceIdByModelId) {
        String modelId(String evidenceId) { return modelIdByEvidenceId.get(evidenceId); }
        String evidenceId(String modelId) { return evidenceIdByModelId.get(modelId); }
    }

    private static String compact(String excerpt) {
        String normalized = excerpt.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_EXCERPT_CHARACTERS
                ? normalized : normalized.substring(0, MAX_EXCERPT_CHARACTERS) + "…";
    }
}
