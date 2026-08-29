package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ensures the serialized full projection and its domain projection cannot diverge. */
final class AdventureStoryPlanProjectionCandidateConsistency {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern UUID_TOKEN = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private AdventureStoryPlanProjectionCandidateConsistency() { }

    static void assertEquivalent(String serializedCandidate, List<AdventureStoryPlanStage> stages) {
        final JsonNode root;
        try {
            root = MAPPER.readTree(serializedCandidate);
        } catch (Exception failure) {
            throw new IllegalArgumentException("full projection candidate must be valid JSON", failure);
        }
        JsonNode serializedStages = root == null ? null : root.get("stages");
        if (serializedStages == null || !serializedStages.isArray()) {
            throw new IllegalArgumentException("full projection candidate must contain stages");
        }
        if (serializedStages.size() != stages.size()) {
            throw new IllegalArgumentException("serialized projection stage count does not match domain stages");
        }
        for (int index = 0; index < stages.size(); index++) {
            if (!canonicalSerialized(serializedStages.get(index)).equals(canonicalDomain(stages.get(index)))) {
                throw new IllegalArgumentException("serialized projection stage does not match domain stage at index " + index);
            }
        }
    }

    static String serialize(List<AdventureStoryPlanStage> stages) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", AdventureStoryPlanStage.CURRENT_SCHEMA_VERSION);
        ArrayNode serializedStages = root.putArray("stages");
        stages.forEach(stage -> {
            ObjectNode serialized = canonicalDomain(stage);
            int evidenceCount = serialized.remove("evidenceCount").asInt(0);
            ArrayNode evidence = serialized.putArray("evidence");
            for (int index = 0; index < evidenceCount; index++) evidence.addObject();
            serializedStages.add(serialized);
        });
        return root.toString();
    }

    private static ObjectNode canonicalSerialized(JsonNode source) {
        ObjectNode result = MAPPER.createObjectNode();
        String title = text(source, "title", "");
        result.put("position", source.path("position").asInt(0));
        result.put("title", title);
        result.put("goal", text(source, "goal", ""));
        result.put("conflict", text(source, "conflict", ""));
        result.put("transitionCondition", text(source, "transitionCondition", ""));
        copyArray(source, result, "npcOrClues");
        copyArray(source, result, "endingIds");
        result.put("stageType", normalizeStageType(text(source, "stageType", "EVENT")));
        result.put("location", text(source, "location", title));
        result.put("mapDefinitionId", normalizeMapId(text(source, "mapDefinitionId", "")));
        result.put("mapAssetId", text(source, "mapAssetId", ""));
        result.put("mapAssetLocator", text(source, "mapAssetLocator", ""));
        copyArray(source, result, "enemies");
        result.put("boss", text(source, "boss", ""));
        result.put("clearCondition", text(source, "clearCondition", text(source, "transitionCondition", "")));
        result.put("failureCondition", text(source, "failureCondition", ""));
        copyArray(source, result, "rewards");
        copyArray(source, result, "branchIds");
        copyObject(source, result, "branchTargets");
        result.put("schemaVersion", source == null ? 1 : source.path("schemaVersion").asInt(1));
        result.put("combatRequirement", text(source, "combatRequirement", "NONE"));
        result.set("combatSkeleton", canonicalSkeleton(source == null ? null : source.get("combatSkeleton")));
        copySourceFactClaims(source, result);
        result.put("tacticalPreparationRequirement", text(source, "tacticalPreparationRequirement", "NOT_REQUIRED"));
        result.put("evidenceCount", source.path("evidence").isArray() ? source.path("evidence").size() : 0);
        return result;
    }

    private static ObjectNode canonicalDomain(AdventureStoryPlanStage stage) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("position", stage.position());
        result.put("title", stage.title());
        result.put("goal", stage.goal());
        result.put("conflict", stage.conflict());
        result.put("transitionCondition", stage.transitionCondition());
        putArray(result, "npcOrClues", stage.npcOrClues());
        putArray(result, "endingIds", stage.endingIds());
        result.put("stageType", stage.stageType().name());
        result.put("location", stage.location());
        result.put("mapDefinitionId", stage.mapDefinitionId() == null ? "" : stage.mapDefinitionId().toString());
        result.put("mapAssetId", stage.mapAssetId());
        result.put("mapAssetLocator", stage.mapAssetLocator());
        putArray(result, "enemies", stage.enemies());
        result.put("boss", stage.boss());
        result.put("clearCondition", stage.clearCondition());
        result.put("failureCondition", stage.failureCondition());
        putArray(result, "rewards", stage.rewards());
        putArray(result, "branchIds", stage.branchIds());
        putObject(result, "branchTargets", stage.branchTargets());
        result.put("schemaVersion", stage.schemaVersion());
        result.put("combatRequirement", stage.combatRequirement().name());
        result.set("combatSkeleton", skeleton(stage.combatSkeleton()));
        ArrayNode claims = result.putArray("sourceFactClaims");
        stage.sourceFactClaims().forEach(claim -> {
            ObjectNode value = claims.addObject();
            value.put("fieldPath", claim.fieldPath());
            value.put("normalizedClaim", claim.normalizedClaim());
            putArray(value, "citationKeys", claim.citationKeys());
        });
        result.put("tacticalPreparationRequirement", stage.tacticalPreparationRequirement().name());
        result.put("evidenceCount", stage.evidence().size());
        return result;
    }

    private static ObjectNode canonicalSkeleton(JsonNode source) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("objective", text(source, "objective", ""));
        result.put("startTrigger", text(source, "startTrigger", ""));
        copyArray(source, result, "participants");
        result.put("successOutcome", text(source, "successOutcome", ""));
        result.put("failureOutcome", text(source, "failureOutcome", ""));
        copyArray(source, result, "rewards");
        return result;
    }

    private static ObjectNode skeleton(com.dndmaster.adventure.domain.adventure.CombatSkeleton value) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("objective", value.objective());
        result.put("startTrigger", value.startTrigger());
        ArrayNode participants = result.putArray("participants");
        value.participants().forEach(participant -> {
            ObjectNode item = participants.addObject();
            item.put("participantId", participant.participantId());
            item.put("role", participant.role().name());
            item.put("name", participant.name());
            item.put("minimumCount", participant.minimumCount());
            item.put("maximumCount", participant.maximumCount());
            putArray(item, "citationKeys", participant.citationKeys());
        });
        result.put("successOutcome", value.successOutcome());
        result.put("failureOutcome", value.failureOutcome());
        ArrayNode rewards = result.putArray("rewards");
        value.rewards().forEach(claim -> {
            ObjectNode item = rewards.addObject();
            item.put("fieldPath", claim.fieldPath());
            item.put("normalizedClaim", claim.normalizedClaim());
            putArray(item, "citationKeys", claim.citationKeys());
        });
        return result;
    }

    private static void copySourceFactClaims(JsonNode source, ObjectNode target) {
        JsonNode claims = source == null ? null : source.get("sourceFactClaims");
        if (claims != null && claims.isArray()) target.set("sourceFactClaims", claims);
        else target.putArray("sourceFactClaims");
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node == null ? "" : node.path(field).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private static String normalizeMapId(String value) {
        if (value == null || value.isBlank()) return "";
        Matcher matcher = UUID_TOKEN.matcher(value);
        return matcher.find() ? UUID.fromString(matcher.group()).toString() : value.trim();
    }

    private static String normalizeStageType(String value) {
        String normalized = value == null ? "EVENT" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "town", "마을", "도시", "사회" -> AdventureStageType.TOWN.name();
            case "dungeon", "던전", "지하", "탐험" -> AdventureStageType.DUNGEON.name();
            case "travel", "여행", "이동" -> AdventureStageType.TRAVEL.name();
            case "encounter", "조우", "전투" -> AdventureStageType.ENCOUNTER.name();
            case "finale", "최종", "결전" -> AdventureStageType.FINALE.name();
            default -> AdventureStageType.EVENT.name();
        };
    }

    private static void copyArray(JsonNode source, ObjectNode target, String field) {
        JsonNode values = source == null ? null : source.get(field);
        if (values != null && values.isArray()) target.set(field, values);
        else target.putArray(field);
    }

    private static void copyObject(JsonNode source, ObjectNode target, String field) {
        JsonNode values = source == null ? null : source.get(field);
        if (values != null && values.isObject()) target.set(field, values);
        else target.putObject(field);
    }

    private static void putArray(ObjectNode target, String field, List<String> values) {
        ArrayNode array = target.putArray(field);
        values.forEach(array::add);
    }

    private static void putObject(ObjectNode target, String field, Map<String, String> values) {
        ObjectNode object = target.putObject(field);
        values.forEach(object::put);
    }
}
