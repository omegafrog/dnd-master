package com.dndmaster.adventure.application.storyplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Applies an untrusted full repair candidate only inside the deterministic repair scope. */
public final class StoryPlanScopedMerger {
    private final ObjectMapper mapper;

    public StoryPlanScopedMerger() {
        this(new ObjectMapper());
    }

    public StoryPlanScopedMerger(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public JsonNode merge(String previousCandidate, String repairedCandidate, RepairScope scope) {
        try {
            return merge(mapper.readTree(previousCandidate), mapper.readTree(repairedCandidate), scope);
        } catch (Exception failure) {
            throw new IllegalArgumentException("story plan scoped merge requires valid JSON candidates", failure);
        }
    }

    public JsonNode merge(JsonNode previousCandidate, JsonNode repairedCandidate, RepairScope scope) {
        Objects.requireNonNull(previousCandidate, "previous candidate must not be null");
        Objects.requireNonNull(repairedCandidate, "repaired candidate must not be null");
        Objects.requireNonNull(scope, "repair scope must not be null");
        if (!previousCandidate.isObject() || !repairedCandidate.isObject()) {
            throw new IllegalArgumentException("story plan candidates must be JSON objects");
        }
        return mergeNode(previousCandidate, repairedCandidate, "", scope);
    }

    private JsonNode mergeNode(JsonNode previous, JsonNode repaired, String path, RepairScope scope) {
        if (scope.allows(path)) return repaired.deepCopy();
        if (previous.isObject() && repaired.isObject()) {
            ObjectNode result = previous.deepCopy();
            repaired.fieldNames().forEachRemaining(field -> {
                String childPath = path.isBlank() ? field : path + "." + field;
                JsonNode repairedValue = repaired.get(field);
                JsonNode previousValue = previous.get(field);
                if (previousValue == null) {
                    if (scope.allows(childPath)) result.set(field, repairedValue.deepCopy());
                } else {
                    result.set(field, mergeNode(previousValue, repairedValue, childPath, scope));
                }
            });
            return result;
        }
        if (previous.isArray() && repaired.isArray()) {
            ArrayNode result = previous.deepCopy();
            if (scope.allows(path)) return repaired.deepCopy();
            int common = Math.min(previous.size(), repaired.size());
            for (int index = 0; index < common; index++) {
                result.set(index, mergeNode(previous.get(index), repaired.get(index), path + "[" + index + "]", scope));
            }
            return result;
        }
        return previous.deepCopy();
    }
}
