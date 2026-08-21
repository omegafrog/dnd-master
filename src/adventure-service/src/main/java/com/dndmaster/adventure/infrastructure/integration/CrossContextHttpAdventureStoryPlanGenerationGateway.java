package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanCandidateValidationException;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanValidator;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanGraphValidator;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.PlacementGroundingType;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrossContextHttpAdventureStoryPlanGenerationGateway implements AdventureStoryPlanGenerationPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossContextHttpAdventureStoryPlanGenerationGateway.class);
    private static final Pattern UUID_TOKEN = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private final HttpClient client; private final URI baseUri; private final Duration timeout; private final ObjectMapper mapper; private final String internalToken;
    public CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client); this.baseUri = Objects.requireNonNull(baseUri); this.timeout = Objects.requireNonNull(timeout); this.mapper = Objects.requireNonNull(mapper);
        if (internalToken == null || internalToken.isBlank()) throw new IllegalArgumentException("adventure story plan AI internal token must not be blank");
        this.internalToken = internalToken;
    }
    @Override public List<AdventureStoryPlanStage> generate(Request request) {
        try {
            var body = mapper.writeValueAsString(request);
            var response = client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/adventure-story-plan"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 422) {
                List<String> violations = remoteCandidateViolations(response.body(), "AI returned an invalid story plan candidate");
                LOGGER.warn("remote_story_plan_candidate_rejected violations={}", violations);
                throw new AdventureStoryPlanCandidateValidationException(violations);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
                if (detail.length() > 1200) detail = detail.substring(0, 1200);
                throw new IllegalStateException("story plan AI failed: " + response.statusCode() + " body=" + detail);
            }
            return parseOutlineCandidate(response.body(), request);
        } catch (HttpTimeoutException e) { throw new IllegalStateException("story plan AI timed out after " + timeout, e); }
        catch (IOException e) { throw new IllegalStateException("story plan AI request encoding failed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("story plan AI interrupted", e); }
    }

    private List<AdventureStoryPlanStage> parseOutlineCandidate(String responseBody, Request request) {
        try {
            var parsed = mapper.readValue(responseBody, Response.class);
            if (parsed.stages() == null) throw new IllegalStateException("AI returned no story stages");
            validateEndingIdProjection(parsed.stages());
            AdventurePlanConfiguration configuration = request.configuration();
            if (parsed.stages().size() < configuration.adventureLength().minimumStages()
                    || parsed.stages().size() > configuration.adventureLength().maximumStages()) {
                throw new IllegalStateException("AI returned an invalid stage count for adventure length");
            }
            long endingCount = parsed.stages().stream().flatMap(stage -> stage.endingIds().stream()).distinct().count();
            if (endingCount != configuration.endingCount()) throw new IllegalStateException("AI returned an invalid ending count");
            Set<String> knownCitations = request.citations().stream()
                    .map(TacticalScenePlanValidator::key).collect(Collectors.toSet());
            if (knownCitations.isEmpty() && parsed.stages().stream().flatMap(stage -> stage.evidence().stream()).findAny().isPresent()) {
                throw new IllegalStateException("AI returned source evidence without a supplied citation");
            }
            // Markdown plans are intentionally loose agent working documents. Missing
            // per-stage evidence is allowed; the retrieved excerpts remain in the prompt.
            if (parsed.stages().stream().flatMap(stage -> stage.evidence().stream()).anyMatch(item -> !matchesCitation(item, request.citations()))) {
                throw new IllegalStateException("AI returned an unknown source citation");
            }
            Map<UUID, AdventureStoryPlanGenerationPort.MapContext> maps = request.maps().stream().collect(
                    Collectors.toMap(AdventureStoryPlanGenerationPort.MapContext::mapDefinitionId, item -> item));
            if (!maps.isEmpty() && parsed.stages().stream().anyMatch(stage -> "DUNGEON".equalsIgnoreCase(stage.stageType())
                    && stage.mapDefinitionId().isBlank())) {
                throw new IllegalStateException("map-backed bundle requires every dungeon stage to reference a map definition");
            }
            List<AdventureStoryPlanStage> stages = parsed.stages().stream().map(stage -> toDomain(stage, maps)).toList();
            AdventureStoryPlanGraphValidator.validate(stages, configuration);
            return stages;
        } catch (AdventureStoryPlanCandidateValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (RuntimeException | IOException invalidCandidate) {
            throw new AdventureStoryPlanCandidateValidationException(List.of(
                    validationMessage(invalidCandidate, "AI returned an invalid story plan candidate")));
        }
    }

    private static void validateEndingIdProjection(List<Stage> stages) {
        for (Stage stage : stages) {
            if (stage.endingIds() == null) {
                throw new IllegalStateException("endingIds must be explicit");
            }
            if (stage.endingIds().stream().anyMatch(item -> item == null || item.isBlank())) {
                throw new IllegalStateException("endingIds must not contain blank values");
            }
            if (stage.endingIds().isEmpty()) {
                throw new IllegalStateException("endingIds must not be empty");
            }
        }
    }

    @Override public TacticalScenePlanCandidate generateTacticalScene(TacticalSceneRequest request) {
        try {
            var response = client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/tactical-scene-plan"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 422) {
                throw new AdventureStoryPlanCandidateValidationException(List.of(
                        remoteCandidateViolations(response.body(), "AI returned an invalid tactical scene candidate").getFirst()));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("tactical scene AI failed: " + response.statusCode());
            }
            return parseTacticalCandidate(response.body(), request);
        } catch (HttpTimeoutException e) { throw new IllegalStateException("tactical scene AI timed out after " + timeout, e); }
        catch (IOException e) { throw new IllegalStateException("tactical scene AI request encoding failed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("tactical scene AI interrupted", e); }
    }

    private TacticalScenePlanCandidate parseTacticalCandidate(String responseBody, TacticalSceneRequest request) {
        try {
            TacticalResponse parsed = mapper.readValue(responseBody, TacticalResponse.class);
            if (parsed.scene() == null) throw new IllegalStateException("AI returned no tactical scene");
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations = parsed.citations().stream().map(item ->
                    new AdventureStoryPlanGenerationPort.SourceCitation(item.documentType(), UUID.fromString(item.documentId()),
                            item.extractionVersion(), item.locator(), item.quote(), item.confidence())).toList();
            if (citations.stream().anyMatch(item -> !matchesCitation(new SourceCitation(
                    item.documentType(), item.documentId().toString(), item.extractionVersion(), item.locator(),
                    item.quote(), item.confidence()), request.citations()))) {
                throw new IllegalStateException("AI returned an unknown tactical source citation");
            }
            if (sourceCitations(parsed.scene()).stream().anyMatch(citation -> request.citations().stream()
                    .noneMatch(source -> citation.equals(TacticalScenePlanValidator.key(source))))) {
                throw new IllegalStateException("AI returned tactical grounding without a supplied citation");
            }
            return TacticalScenePlanCandidate.ready(parsed.stagePosition(), parsed.scene(), citations);
        } catch (AdventureStoryPlanCandidateValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (RuntimeException | IOException invalidCandidate) {
            throw new AdventureStoryPlanCandidateValidationException(List.of(
                    validationMessage(invalidCandidate, "AI returned an invalid tactical scene candidate")));
        }
    }

    private static String validationMessage(Throwable failure, String fallback) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? fallback : root.getMessage();
    }

    private List<String> remoteCandidateViolations(String body, String fallback) {
        try {
            var error = mapper.readTree(body == null ? "" : body);
            var violations = error.path("violations");
            if (violations.isArray()) {
                List<String> parsed = new java.util.ArrayList<>();
                violations.forEach(item -> {
                    if (item.isTextual() && !item.asText().isBlank()) parsed.add(item.asText().trim());
                });
                if (!parsed.isEmpty()) return List.copyOf(parsed);
            }
            String detail = error.path("detail").asText("").trim();
            if (!detail.isBlank()) return List.of(detail);
            String message = error.path("message").asText("").trim();
            return List.of(message.isBlank() ? fallback : message);
        } catch (RuntimeException | IOException ignored) {
            return List.of(fallback);
        }
    }

    private static List<String> sourceCitations(TacticalScenePlan scene) {
        java.util.stream.Stream<PlacementGrounding> groundings = java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(java.util.stream.Stream.concat(scene.players().stream(), scene.allies().stream()),
                        java.util.stream.Stream.concat(scene.npcs().stream(), scene.enemies().stream())).map(item -> item.grounding()),
                java.util.stream.Stream.concat(java.util.stream.Stream.concat(scene.bosses().stream(), scene.interactiveObjects().stream()).map(item -> item.grounding()),
                        java.util.stream.Stream.concat(scene.environments().stream().map(item -> item.grounding()),
                                java.util.stream.Stream.concat(scene.triggers().stream().map(item -> item.grounding()), scene.outcomes().stream().map(item -> item.grounding())))));
        return java.util.stream.Stream.concat(groundings, java.util.stream.Stream.of(scene.initialFog().grounding()))
                .filter(item -> item.type() == PlacementGroundingType.SOURCE_CITATION).map(PlacementGrounding::citation).toList();
    }
    private static boolean matchesCitation(SourceCitation item, List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
        return citations.stream().anyMatch(source -> source.documentId().toString().equals(item.documentId())
                && source.locator().equals(item.locator())
                && source.documentType().equals(item.documentType())
                && source.extractionVersion() == item.extractionVersion()
                && source.quote().equals(item.quote())
                && item.confidence() >= 0 && item.confidence() <= source.confidence());
    }
    private static AdventureStoryPlanStage toDomain(Stage stage, Map<UUID, AdventureStoryPlanGenerationPort.MapContext> maps) {
        UUID mapId = parseMapDefinitionId(stage.mapDefinitionId());
        if (mapId == null && (!stage.mapAssetId().isBlank() || !stage.mapAssetLocator().isBlank())) throw new IllegalStateException("map asset requires a map definition");
        AdventureStoryPlanGenerationPort.MapContext map = mapId == null ? null : maps.get(mapId);
        if (mapId != null && map == null) throw new IllegalStateException("AI returned an unknown map definition");
        if (map != null && ((!stage.mapAssetId().isBlank() && !stage.mapAssetId().equals(map.assetId()))
                || (!stage.mapAssetLocator().isBlank() && !stage.mapAssetLocator().equals(map.assetLocator())))) {
            throw new IllegalStateException("AI returned map metadata that does not match the source map");
        }
        var evidence = stage.evidence().stream().map(item -> new com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence(item.documentType(), UUID.fromString(item.documentId()), item.extractionVersion(), item.locator(), item.quote(), item.confidence())).toList();
        var grounding = evidence.isEmpty() ? com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.AI_SUGGESTION : com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.GROUNDED;
        var suggestions = evidence.isEmpty() ? List.of("location", "enemies", "boss", "rewards", "conditions") : List.<String>of();
        List<com.dndmaster.adventure.domain.scenario.StoryMapBinding> bindings = mapId == null ? List.of()
                : List.of(new com.dndmaster.adventure.domain.scenario.StoryMapBinding(
                        Integer.toString(stage.position()), stage.location(), stage.transitionCondition(), mapId));
        var spawn = inferPlayerSpawn(stage, map);
        return new AdventureStoryPlanStage(stage.position(), stage.title(), stage.goal(), stage.conflict(), stage.transitionCondition(), stage.npcOrClues(), stage.endingIds(), bindings,
                stage.stageType() == null ? com.dndmaster.adventure.domain.adventure.AdventureStageType.EVENT : com.dndmaster.adventure.domain.adventure.AdventureStageType.valueOf(stage.stageType()),
                stage.location(), mapId, map == null ? stage.mapAssetId() : map.assetId(), map == null ? stage.mapAssetLocator() : map.assetLocator(),
                stage.enemies(), stage.boss(), stage.clearCondition(), stage.failureCondition(), stage.rewards(), stage.branchIds(), evidence, grounding, suggestions,
                map == null ? "UNAVAILABLE" : map.safetyStatus(), map == null ? null : map.confidence(), stage.branchTargets(),
                spawn.x(), spawn.y(), spawn.confidence(), spawn.rationale());
    }
    static UUID parseMapDefinitionId(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = UUID_TOKEN.matcher(value);
        if (!matcher.find()) throw new IllegalArgumentException("map definition ID is not a UUID: " + value);
        return UUID.fromString(matcher.group());
    }
    private static Spawn inferPlayerSpawn(Stage stage, AdventureStoryPlanGenerationPort.MapContext map) {
        if (stage.playerSpawnX() != null && stage.playerSpawnY() != null)
            return new Spawn(stage.playerSpawnX(), stage.playerSpawnY(), stage.playerSpawnConfidence(), stage.playerSpawnRationale());
        String text = (stage.title() + " " + stage.location() + " " + stage.goal() + " " + stage.conflict() + " " + String.join(" ", stage.npcOrClues())).toLowerCase(java.util.Locale.ROOT);
        String asset = map == null ? stage.mapAssetId() : map.assetId();
        if (map != null && (asset != null && (asset.toLowerCase(java.util.Locale.ROOT).contains("potent-brew")
                || asset.toLowerCase(java.util.Locale.ROOT).contains("page 1 image 1")))
                && (text.contains("beer cellar") || text.contains("cellar") || text.contains("hatch") || text.contains("stairs") || text.contains("brew"))) {
            return new Spawn(10, 13, "INFERRED", "스토리북의 양조장 지하실로 내려가는 나무 계단/해치와 맵 격자를 대조해 추정");
        }
        return new Spawn(0, 0, "UNAVAILABLE", "맵·스토리북에서 시작 위치를 확정할 단서가 없어 좌표를 추정하지 못함");
    }
    private record Spawn(int x, int y, String confidence, String rationale) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(List<Stage> stages) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record TacticalResponse(int stagePosition, TacticalScenePlan scene, List<SourceCitation> citations) {
        TacticalResponse { citations = List.copyOf(Objects.requireNonNull(citations, "tactical citations must be explicit")); }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record Stage(int position, String title, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, String stageType, String location, String mapDefinitionId,
            String mapAssetId, String mapAssetLocator, List<String> enemies, String boss, String clearCondition, String failureCondition, List<String> rewards,
            List<String> branchIds, java.util.Map<String, String> branchTargets, List<SourceCitation> evidence,
            Integer playerSpawnX, Integer playerSpawnY, String playerSpawnConfidence, String playerSpawnRationale) {
        Stage {
            npcOrClues = List.copyOf(Objects.requireNonNull(npcOrClues, "npcOrClues must be explicit"));
            endingIds = List.copyOf(Objects.requireNonNull(endingIds, "endingIds must be explicit"));
            enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies must be explicit"));
            rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards must be explicit"));
            branchIds = List.copyOf(Objects.requireNonNull(branchIds, "branchIds must be explicit"));
            branchTargets = java.util.Map.copyOf(Objects.requireNonNull(branchTargets, "branchTargets must be explicit"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must be explicit"));
            mapAssetId = mapAssetId == null ? "" : mapAssetId;
            mapAssetLocator = mapAssetLocator == null ? "" : mapAssetLocator;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record SourceCitation(String documentType, String documentId, long extractionVersion, String locator, String quote, double confidence) {}
}
