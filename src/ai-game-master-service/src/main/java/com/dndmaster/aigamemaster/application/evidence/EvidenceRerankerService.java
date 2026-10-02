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
            if (root == null || !root.isObject() || !root.path("orderedCandidateIds").isArray()) {
                String rootType = root == null ? "NULL" : root.getNodeType().name();
                String fieldType = root == null || !root.isObject() || !root.has("orderedCandidateIds")
                        ? "MISSING" : root.get("orderedCandidateIds").getNodeType().name();
                throw invalid(EvidenceModelOutputException.Category.INVALID_SCHEMA,
                        "orderedCandidateIds must be an array; rootType=" + rootType + " fieldType=" + fieldType);
            }
            List<String> ids = new java.util.ArrayList<>();
            for (JsonNode id : root.path("orderedCandidateIds")) {
                if (!id.isTextual() || id.asText().isBlank()) throw invalid(
                        EvidenceModelOutputException.Category.INVALID_IDENTIFIER,
                        "ordered candidate ID must be nonblank text; index=" + ids.size()
                                + " valueType=" + id.getNodeType().name());
                ids.add(id.asText());
            }
            if (ids.size() > 30) throw invalid(EvidenceModelOutputException.Category.INVALID_IDENTIFIER,
                    "ordered candidate ID count exceeds 30; idCount=" + ids.size());
            if (ids.size() != new HashSet<>(ids).size()) throw invalid(
                    EvidenceModelOutputException.Category.INVALID_IDENTIFIER,
                    "ordered candidate IDs contain duplicates; idCount=" + ids.size());
            long unknownIdCount = ids.stream().filter(id -> !candidateIds.contains(id)).count();
            if (unknownIdCount > 0) throw invalid(EvidenceModelOutputException.Category.INVALID_IDENTIFIER,
                    "ordered candidate IDs include IDs outside the supplied candidates; unknownIdCount=" + unknownIdCount);
            return new EvidenceRerankResponse(ids);
        } catch (EvidenceModelOutputException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EvidenceModelOutputException(EvidenceModelOutputException.Category.MALFORMED_JSON,
                    "model response is not valid JSON", exception);
        }
    }

    private static EvidenceModelOutputException invalid(EvidenceModelOutputException.Category category, String message) {
        return new EvidenceModelOutputException(category, message);
    }
    private static String rerankInstruction(EvidenceRerankRequest request, EvidenceModelPrompt.Candidates candidates) {
        return "TASK=EVIDENCE_RERANK\nReturn JSON {\"orderedCandidateIds\":[...]}. "
                + "Return at most 30 unique short c-number IDs, copied exactly from the supplied candidates. "
                + "Order by relevance; do not remove candidates by a score threshold before applying the 30-ID limit. "
                + "QUERY=" + request.query() + "\nTASK_CONTEXT=" + request.taskContext()
                + "\nCANDIDATES=" + candidates.prompt();
    }
}
