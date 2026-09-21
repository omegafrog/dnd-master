package com.dndmaster.aigamemaster.application.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EvidenceSufficiencyJudgeService {
    private final EvidenceModelStageExecutor stage;
    private final ObjectMapper mapper;

    public EvidenceSufficiencyJudgeService(EvidenceModelPort model, ObjectMapper mapper) {
        this.stage = new EvidenceModelStageExecutor(model);
        this.mapper = mapper;
    }

    public EvidenceSufficiencyResponse judge(EvidenceSufficiencyRequest request) {
        Set<String> candidateIds = request.candidates().stream().map(EvidenceCandidate::evidenceId).collect(java.util.stream.Collectors.toSet());
        return stage.execute("evidence-sufficiency", instruction(request), raw -> parse(raw, candidateIds, request.pinnedEvidenceIds()));
    }

    private EvidenceSufficiencyResponse parse(String raw, Set<String> candidateIds, List<String> pinnedIds) {
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject() || !root.path("sufficient").isBoolean()
                    || !root.path("selectedEvidenceIds").isArray() || !root.path("selectionReasons").isObject()
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
        values.fields().forEachRemaining(entry -> {
            if (entry.getKey().isBlank() || !entry.getValue().isTextual() || entry.getValue().asText().isBlank()) throw invalid("selection reason is invalid");
            result.put(entry.getKey(), entry.getValue().asText());
        });
        return result;
    }

    private static EvidenceModelOutputException invalid(String message) { return new EvidenceModelOutputException(message); }
    private static String instruction(EvidenceSufficiencyRequest request) {
        return request.policy().fixedInstruction() + "\nOUTPUT={sufficient:boolean,selectedEvidenceIds:string[],selectionReasons:object,missing:string}. "
                + "Every selected ID needs exactly one non-empty reason. Preserve pinned IDs. "
                + "TASK_CONTEXT=" + request.taskContext() + "\nPINNED_IDS=" + request.pinnedEvidenceIds() + "\nCANDIDATES=" + request.candidates();
    }
}
