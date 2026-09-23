package com.dndmaster.aigamemaster.application.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EvidenceRerankerService {
    private final EvidenceModelStageExecutor stage;
    private final ObjectMapper mapper;

    public EvidenceRerankerService(EvidenceModelPort model, ObjectMapper mapper) {
        this.stage = new EvidenceModelStageExecutor(model);
        this.mapper = mapper;
    }

    public EvidenceRerankResponse rerank(EvidenceRerankRequest request) {
        Set<String> candidateIds = request.candidates().stream().map(EvidenceCandidate::evidenceId).collect(java.util.stream.Collectors.toSet());
        EvidenceModelPrompt.Candidates promptCandidates = EvidenceModelPrompt.candidates(request.candidates());
        Set<String> modelIds = Set.copyOf(promptCandidates.evidenceIdByModelId().keySet());
        EvidenceRerankResponse response = stage.execute(request.soloPlayerId(), "evidence-rerank", rerankInstruction(request, promptCandidates),
                raw -> parse(raw, modelIds));
        return new EvidenceRerankResponse(response.orderedCandidateIds().stream()
                .map(promptCandidates::evidenceId).filter(java.util.Objects::nonNull).toList());
    }

    private EvidenceRerankResponse parse(String raw, Set<String> candidateIds) {
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject() || !root.path("orderedCandidateIds").isArray()) throw invalid("orderedCandidateIds array is required");
            List<String> ids = new java.util.ArrayList<>();
            for (JsonNode id : root.path("orderedCandidateIds")) {
                if (!id.isTextual() || id.asText().isBlank()) throw invalid("ordered candidate ID is invalid");
                ids.add(id.asText());
            }
            if (ids.size() > 30 || ids.size() != new HashSet<>(ids).size() || !candidateIds.containsAll(ids)) throw invalid("rerank IDs must be unique candidate IDs with a maximum of 30");
            return new EvidenceRerankResponse(ids);
        } catch (EvidenceModelOutputException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EvidenceModelOutputException("invalid rerank model output", exception);
        }
    }

    private static EvidenceModelOutputException invalid(String message) { return new EvidenceModelOutputException(message); }
    private static String rerankInstruction(EvidenceRerankRequest request, EvidenceModelPrompt.Candidates candidates) {
        return "TASK=EVIDENCE_RERANK\nReturn JSON {\"orderedCandidateIds\":[...]}. "
                + "Return at most 30 unique short c-number IDs, copied exactly from the supplied candidates. "
                + "Order by relevance; do not remove candidates by a score threshold before applying the 30-ID limit. "
                + "QUERY=" + request.query() + "\nTASK_CONTEXT=" + request.taskContext()
                + "\nCANDIDATES=" + candidates.prompt();
    }
}
