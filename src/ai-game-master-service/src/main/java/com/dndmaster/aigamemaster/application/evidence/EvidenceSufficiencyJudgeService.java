package com.dndmaster.aigamemaster.application.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EvidenceSufficiencyJudgeService {
    private final EvidenceModelStageExecutor stage;
    private final ObjectMapper mapper;

    public EvidenceSufficiencyJudgeService(EvidenceModelPort model, ObjectMapper mapper) {
        this.stage = new EvidenceModelStageExecutor(model);
        this.mapper = mapper;
    }

    public EvidenceSufficiencyResponse judge(EvidenceSufficiencyRequest request) {
        return judge(request, null);
    }

    public EvidenceSufficiencyResponse judge(EvidenceSufficiencyRequest request, UUID soloPlayerId) {
        Set<String> candidateIds = request.candidates().stream().map(EvidenceCandidate::evidenceId).collect(java.util.stream.Collectors.toSet());
        return stage.execute(soloPlayerId, "evidence-sufficiency", instruction(request), sufficiencySchema(), raw -> parse(raw, candidateIds, request.pinnedEvidenceIds()));
    }

    private EvidenceSufficiencyResponse parse(String raw, Set<String> candidateIds, List<String> pinnedIds) {
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject() || !root.path("sufficient").isBoolean()
                    || !root.path("selectedEvidenceIds").isArray() || !root.path("selectionReasons").isArray()
                    || !root.path("missing").isTextual()) throw invalid("required sufficiency fields are missing");
            List<String> selected = stringArray(root.path("selectedEvidenceIds"), "selected evidence ID");
            Map<String, String> reasons = reasons(root.path("selectionReasons"));
            String missing = root.path("missing").asText().trim();
            boolean sufficient = root.path("sufficient").booleanValue();
            if (selected.size() != new HashSet<>(selected).size() || !candidateIds.containsAll(selected)
                    || !selected.containsAll(pinnedIds) || !reasons.keySet().equals(new HashSet<>(selected))) throw invalid("selected evidence and reasons must match candidate IDs and pinned IDs");
            if (sufficient && selected.isEmpty()) throw invalid("sufficient output requires selected evidence");
            if (!sufficient && missing.isBlank()) throw invalid("insufficient output requires missing information");
            return new EvidenceSufficiencyResponse(sufficient, selected, reasons, missing);
        } catch (EvidenceModelOutputException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EvidenceModelOutputException("invalid sufficiency model output", exception);
        }
    }

    private static List<String> stringArray(JsonNode values, String name) {
        var result = new java.util.ArrayList<String>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) throw invalid(name + " is invalid");
            result.add(value.asText());
        }
        return result;
    }

    private static Map<String, String> reasons(JsonNode values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            if (!value.isObject() || !value.path("evidenceId").isTextual()
                    || value.path("evidenceId").asText().isBlank()
                    || !value.path("reason").isTextual() || value.path("reason").asText().isBlank()) {
                throw invalid("selection reason is invalid");
            }
            String evidenceId = value.path("evidenceId").asText();
            if (result.put(evidenceId, value.path("reason").asText()) != null) {
                throw invalid("selection reason contains duplicate evidence ID");
            }
        }
        return result;
    }

    private static EvidenceModelOutputException invalid(String message) { return new EvidenceModelOutputException(message); }
    private JsonNode sufficiencySchema() {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        var properties = schema.putObject("properties");
        properties.putObject("sufficient").put("type", "boolean");
        var ids = properties.putObject("selectedEvidenceIds");
        ids.put("type", "array");
        ids.putObject("items").put("type", "string");
        var reasons = properties.putObject("selectionReasons");
        reasons.put("type", "array");
        var reason = reasons.putObject("items");
        reason.put("type", "object");
        var reasonProperties = reason.putObject("properties");
        reasonProperties.putObject("evidenceId").put("type", "string");
        reasonProperties.putObject("reason").put("type", "string");
        reason.putArray("required").add("evidenceId").add("reason");
        reason.put("additionalProperties", false);
        properties.putObject("missing").put("type", "string");
        schema.putArray("required").add("sufficient").add("selectedEvidenceIds").add("selectionReasons").add("missing");
        schema.put("additionalProperties", false);
        return schema;
    }
    private static String instruction(EvidenceSufficiencyRequest request) {
        return request.policy().fixedInstruction() + "\nOUTPUT={sufficient:boolean,selectedEvidenceIds:string[],selectionReasons:{evidenceId:string,reason:string}[],missing:string}. "
                + "Every selected ID needs exactly one non-empty reason. Preserve pinned IDs. "
                + "TASK_CONTEXT=" + request.taskContext() + "\nPINNED_IDS=" + request.pinnedEvidenceIds() + "\nCANDIDATES=" + request.candidates();
    }
}
