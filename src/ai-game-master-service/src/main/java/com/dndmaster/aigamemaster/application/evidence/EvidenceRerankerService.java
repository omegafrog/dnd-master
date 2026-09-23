package com.dndmaster.aigamemaster.application.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EvidenceRerankerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceRerankerService.class);
    private final EvidenceModelStageExecutor stage;
    private final ObjectMapper mapper;

    public EvidenceRerankerService(EvidenceModelPort model, ObjectMapper mapper) {
        this.stage = new EvidenceModelStageExecutor(model);
        this.mapper = mapper;
    }

    public EvidenceRerankResponse rerank(EvidenceRerankRequest request) {
        return rerank(request, null);
    }

    public EvidenceRerankResponse rerank(EvidenceRerankRequest request, UUID soloPlayerId) {
        Set<String> candidateIds = request.candidates().stream().map(EvidenceCandidate::evidenceId).collect(java.util.stream.Collectors.toSet());
        return stage.execute(soloPlayerId, "evidence-rerank", rerankInstruction(request), rerankSchema(), raw -> parse(raw, candidateIds));
    }

    private EvidenceRerankResponse parse(String raw, Set<String> candidateIds) {
        LOGGER.info("evidence_rerank_output_received\n"
                + "----- evidence reranker Codex response start -----\n{}\n"
                + "----- evidence reranker Codex response end -----", raw);
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject() || !root.path("orderedCandidateIds").isArray()) {
                throw invalid("orderedCandidateIds array is required");
            }
            List<String> ids = new java.util.ArrayList<>();
            for (JsonNode id : root.path("orderedCandidateIds")) {
                if (!id.isTextual() || id.asText().isBlank()) throw invalid("ordered candidate ID is invalid");
                ids.add(id.asText());
            }
            List<String> rawIds = List.copyOf(ids);
            List<String> duplicateIds = rawIds.stream().filter(id -> java.util.Collections.frequency(rawIds, id) > 1).distinct().toList();
            if (!duplicateIds.isEmpty()) {
                LOGGER.warn("evidence_rerank_output_duplicates_removed duplicateIds={} returnedCount={}", duplicateIds, ids.size());
            }
            ids = new LinkedHashSet<>(ids).stream().toList();
            List<String> unknownIds = ids.stream().filter(id -> !candidateIds.contains(id)).toList();
            int unknownCount = unknownIds.size();
            if (ids.size() > 30 || unknownCount > 0) {
                throw invalid("rerank IDs must be candidate IDs with a maximum of 30 unique IDs"
                        + "; candidateCount=" + candidateIds.size()
                        + "; returnedCount=" + ids.size()
                        + "; duplicateIds=" + duplicateIds
                        + "; unknownIds=" + unknownIds);
            }
            return new EvidenceRerankResponse(ids);
        } catch (EvidenceModelOutputException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("evidence_rerank_output_invalid reason=unable to parse response", exception);
            throw new EvidenceModelOutputException("invalid rerank model output", exception);
        }
    }

    private static EvidenceModelOutputException invalid(String message) {
        LOGGER.warn("evidence_rerank_output_invalid reason={}", message);
        return new EvidenceModelOutputException(message);
    }
    private JsonNode rerankSchema() {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        var ids = properties.putObject("orderedCandidateIds");
        ids.put("type", "array");
        ids.putObject("items").put("type", "string");
        schema.putArray("required").add("orderedCandidateIds");
        schema.put("additionalProperties", false);
        return schema;
    }
    private static String rerankInstruction(EvidenceRerankRequest request) {
        String candidateIds = request.candidates().stream().map(EvidenceCandidate::evidenceId).collect(java.util.stream.Collectors.joining(","));
        return "TASK=EVIDENCE_RERANK\nReturn JSON {\"orderedCandidateIds\":[...]}. "
                + "Return at most 30 IDs. If more than 30 candidates are supplied, return the 30 most relevant IDs in relevance order. "
                + "Do not remove candidates by a score threshold; the required top-30 cutoff is the only bound. "
                + "Copy IDs exactly from CANDIDATE_IDS, never invent, alter, or duplicate an ID, and return only the array with no explanation. "
                + "QUERY=" + request.query() + "\nTASK_CONTEXT=" + request.taskContext() + "\nCANDIDATES=" + request.candidates()
                + "\nCANDIDATE_IDS=" + candidateIds;
    }
}
