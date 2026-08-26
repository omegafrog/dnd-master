package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import com.dndmaster.adventure.domain.scenario.StoryMapBinding;

public record AdventureStoryPlanStage(
        int position,
        String title,
        String goal,
        String conflict,
        String transitionCondition,
        List<String> npcOrClues,
        List<String> endingIds,
        List<StoryMapBinding> mapBindings,
        AdventureStageType stageType,
        String location,
        UUID mapDefinitionId,
        String mapAssetId,
        String mapAssetLocator,
        List<String> enemies,
        String boss,
        String clearCondition,
        String failureCondition,
        List<String> rewards,
        List<String> branchIds,
        List<AdventurePlanEvidence> evidence,
        AdventureGroundingStatus groundingStatus,
        List<String> aiSuggestions,
        String mapSafetyStatus,
        Double mapConfidence,
        Map<String, String> branchTargets,
        Integer playerSpawnX,
        Integer playerSpawnY,
        String playerSpawnConfidence,
        String playerSpawnRationale,
        TacticalScenePlan tacticalScenePlan) {
    public AdventureStoryPlanStage(int position, String title, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds) {
        this(position, title, goal, conflict, transitionCondition, npcOrClues, endingIds, List.of());
    }
    public AdventureStoryPlanStage(int position, String title, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, List<StoryMapBinding> mapBindings) {
        this(position, title, goal, conflict, transitionCondition, npcOrClues, endingIds, mapBindings,
                AdventureStageType.EVENT, title, null, "", "", List.of(), "", transitionCondition, "", List.of(), endingIds, List.of(), AdventureGroundingStatus.AI_SUGGESTION, List.of(), "UNAVAILABLE", null, Map.of(), 0, 0, "UNAVAILABLE", "", TacticalScenePlan.absent());
    }
    public AdventureStoryPlanStage {
        if (position < 1) throw new IllegalArgumentException("stage position must be positive");
        title = required(title, "stage title");
        goal = required(goal, "stage goal");
        conflict = required(conflict, "stage conflict");
        transitionCondition = required(transitionCondition, "stage transition condition");
        npcOrClues = List.copyOf(Objects.requireNonNull(npcOrClues));
        endingIds = List.copyOf(Objects.requireNonNull(endingIds));
        mapBindings = mapBindings == null ? List.of() : List.copyOf(mapBindings);
        stageType = stageType == null ? AdventureStageType.EVENT : stageType;
        location = location == null || location.isBlank() ? title : location.trim();
        mapAssetId = mapAssetId == null ? "" : mapAssetId.trim();
        mapAssetLocator = mapAssetLocator == null ? "" : mapAssetLocator.trim();
        enemies = immutable(enemies);
        boss = boss == null ? "" : boss.trim();
        clearCondition = clearCondition == null || clearCondition.isBlank() ? transitionCondition : clearCondition.trim();
        failureCondition = failureCondition == null ? "" : failureCondition.trim();
        rewards = immutable(rewards);
        branchIds = branchIds == null ? endingIds : List.copyOf(branchIds);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        groundingStatus = groundingStatus == null ? (evidence.isEmpty() ? AdventureGroundingStatus.AI_SUGGESTION : AdventureGroundingStatus.GROUNDED) : groundingStatus;
        aiSuggestions = aiSuggestions == null ? List.of() : List.copyOf(aiSuggestions);
        mapSafetyStatus = mapSafetyStatus == null || mapSafetyStatus.isBlank() ? (mapDefinitionId == null ? "UNAVAILABLE" : "UNKNOWN") : mapSafetyStatus.trim();
        if (mapConfidence != null && (mapConfidence < 0 || mapConfidence > 1)) throw new IllegalArgumentException("map confidence must be between 0 and 1");
        branchTargets = branchTargets == null ? Map.of() : Map.copyOf(branchTargets);
        playerSpawnX = playerSpawnX == null ? 0 : playerSpawnX;
        playerSpawnY = playerSpawnY == null ? 0 : playerSpawnY;
        if (playerSpawnX < 0 || playerSpawnY < 0) throw new IllegalArgumentException("player spawn coordinates must be non-negative");
        playerSpawnConfidence = playerSpawnConfidence == null || playerSpawnConfidence.isBlank() ? "UNAVAILABLE" : playerSpawnConfidence.trim();
        playerSpawnRationale = playerSpawnRationale == null ? "" : playerSpawnRationale.trim();
        tacticalScenePlan = tacticalScenePlan == null ? TacticalScenePlan.absent() : tacticalScenePlan;
    }

    /** Compatibility constructor for persisted story plans before tactical scene snapshots existed. */
    public AdventureStoryPlanStage(int position, String title, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, List<StoryMapBinding> mapBindings, AdventureStageType stageType,
            String location, UUID mapDefinitionId, String mapAssetId, String mapAssetLocator, List<String> enemies, String boss,
            String clearCondition, String failureCondition, List<String> rewards, List<String> branchIds, List<AdventurePlanEvidence> evidence,
            AdventureGroundingStatus groundingStatus, List<String> aiSuggestions, String mapSafetyStatus, Double mapConfidence,
            Map<String, String> branchTargets, Integer playerSpawnX, Integer playerSpawnY, String playerSpawnConfidence,
            String playerSpawnRationale) {
        this(position, title, goal, conflict, transitionCondition, npcOrClues, endingIds, mapBindings, stageType, location, mapDefinitionId,
                mapAssetId, mapAssetLocator, enemies, boss, clearCondition, failureCondition, rewards, branchIds, evidence, groundingStatus,
                aiSuggestions, mapSafetyStatus, mapConfidence, branchTargets, playerSpawnX, playerSpawnY, playerSpawnConfidence,
                playerSpawnRationale, TacticalScenePlan.absent());
    }

    public AdventureStoryPlanStage(int position, String title, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, List<StoryMapBinding> mapBindings, AdventureStageType stageType,
            String location, UUID mapDefinitionId, String mapAssetId, String mapAssetLocator, List<String> enemies, String boss,
            String clearCondition, String failureCondition, List<String> rewards, List<String> branchIds, List<AdventurePlanEvidence> evidence,
            AdventureGroundingStatus groundingStatus, List<String> aiSuggestions, String mapSafetyStatus, Double mapConfidence) {
        this(position, title, goal, conflict, transitionCondition, npcOrClues, endingIds, mapBindings, stageType, location, mapDefinitionId,
                mapAssetId, mapAssetLocator, enemies, boss, clearCondition, failureCondition, rewards, branchIds, evidence, groundingStatus,
                aiSuggestions, mapSafetyStatus, mapConfidence, Map.of(), 0, 0, "UNAVAILABLE", "", TacticalScenePlan.absent());
    }

    /** Compatibility overload for callers compiled before player-spawn metadata was added. */
    public AdventureStoryPlanStage(int position, String title, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, List<StoryMapBinding> mapBindings, AdventureStageType stageType,
            String location, UUID mapDefinitionId, String mapAssetId, String mapAssetLocator, List<String> enemies, String boss,
            String clearCondition, String failureCondition, List<String> rewards, List<String> branchIds, List<AdventurePlanEvidence> evidence,
            AdventureGroundingStatus groundingStatus, List<String> aiSuggestions, String mapSafetyStatus, Double mapConfidence,
            Map<String, String> branchTargets) {
        this(position, title, goal, conflict, transitionCondition, npcOrClues, endingIds, mapBindings, stageType, location,
                mapDefinitionId, mapAssetId, mapAssetLocator, enemies, boss, clearCondition, failureCondition, rewards, branchIds,
                evidence, groundingStatus, aiSuggestions, mapSafetyStatus, mapConfidence, branchTargets, 0, 0, "UNAVAILABLE", "", TacticalScenePlan.absent());
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public AdventureStoryPlanStage withTacticalScenePlan(TacticalScenePlan scene) {
        return new AdventureStoryPlanStage(position, title, goal, conflict, transitionCondition, npcOrClues, endingIds, mapBindings,
                stageType, location, mapDefinitionId, mapAssetId, mapAssetLocator, enemies, boss, clearCondition, failureCondition,
                rewards, branchIds, evidence, groundingStatus, aiSuggestions, mapSafetyStatus, mapConfidence, branchTargets,
                playerSpawnX, playerSpawnY, playerSpawnConfidence, playerSpawnRationale, scene);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
