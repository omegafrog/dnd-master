package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
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

public final class CrossContextHttpAdventureStoryPlanGenerationGateway implements AdventureStoryPlanGenerationPort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout; private final ObjectMapper mapper;
    public CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper) {
        this.client = Objects.requireNonNull(client); this.baseUri = Objects.requireNonNull(baseUri); this.timeout = Objects.requireNonNull(timeout); this.mapper = Objects.requireNonNull(mapper);
    }
    @Override public List<AdventureStoryPlanStage> generate(Request request) {
        try {
            var body = mapper.writeValueAsString(request);
            var response = client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/adventure-story-plan"))
                    .timeout(timeout).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("story plan AI failed: " + response.statusCode());
            var parsed = mapper.readValue(response.body(), Response.class);
            if (parsed.stages() == null) throw new IllegalStateException("AI returned no story stages");
            AdventurePlanConfiguration configuration = request.configuration();
            if (parsed.stages().size() < configuration.adventureLength().minimumStages()
                    || parsed.stages().size() > configuration.adventureLength().maximumStages()) {
                throw new IllegalStateException("AI returned an invalid stage count for adventure length");
            }
            long endingCount = parsed.stages().stream().flatMap(stage -> stage.endingIds().stream()).distinct().count();
            if (endingCount != configuration.endingCount()) throw new IllegalStateException("AI returned an invalid ending count");
            Set<String> knownCitations = request.citations().stream().map(item -> item.documentId() + ":" + item.locator()).collect(Collectors.toSet());
            if (knownCitations.isEmpty() && parsed.stages().stream().flatMap(stage -> stage.evidence().stream()).findAny().isPresent()) {
                throw new IllegalStateException("AI returned source evidence without a supplied citation");
            }
            if (!knownCitations.isEmpty() && parsed.stages().stream().anyMatch(stage -> stage.evidence().isEmpty())) {
                throw new IllegalStateException("AI returned a story stage without source evidence");
            }
            if (parsed.stages().stream().flatMap(stage -> stage.evidence().stream()).anyMatch(item -> !matchesCitation(item, request.citations()))) {
                throw new IllegalStateException("AI returned an unknown source citation");
            }
            Map<UUID, AdventureStoryPlanGenerationPort.MapContext> maps = request.maps().stream().collect(Collectors.toMap(AdventureStoryPlanGenerationPort.MapContext::mapDefinitionId, item -> item));
            return parsed.stages().stream().map(stage -> toDomain(stage, maps)).toList();
        } catch (HttpTimeoutException e) { throw new IllegalStateException("story plan AI timed out after " + timeout, e); }
        catch (IOException e) { throw new IllegalStateException("story plan AI response malformed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("story plan AI interrupted", e); }
    }
    private static boolean matchesCitation(SourceCitation item, List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
        return citations.stream().anyMatch(source -> source.documentId().equals(item.documentId())
                && source.locator().equals(item.locator())
                && source.documentType().equals(item.documentType())
                && source.extractionVersion() == item.extractionVersion()
                && source.quote().equals(item.quote())
                && item.confidence() >= 0 && item.confidence() <= source.confidence());
    }
    private static AdventureStoryPlanStage toDomain(Stage stage, Map<UUID, AdventureStoryPlanGenerationPort.MapContext> maps) {
        UUID mapId = stage.mapDefinitionId() == null || stage.mapDefinitionId().isBlank() ? null : UUID.fromString(stage.mapDefinitionId());
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
        return new AdventureStoryPlanStage(stage.position(), stage.title(), stage.goal(), stage.conflict(), stage.transitionCondition(), stage.npcOrClues(), stage.endingIds(), List.of(),
                stage.stageType() == null ? com.dndmaster.adventure.domain.adventure.AdventureStageType.EVENT : com.dndmaster.adventure.domain.adventure.AdventureStageType.valueOf(stage.stageType()),
                stage.location(), mapId, map == null ? stage.mapAssetId() : map.assetId(), map == null ? stage.mapAssetLocator() : map.assetLocator(),
                stage.enemies(), stage.boss(), stage.clearCondition(), stage.failureCondition(), stage.rewards(), stage.branchIds(), evidence, grounding, suggestions,
                map == null ? "UNAVAILABLE" : map.safetyStatus(), map == null ? null : map.confidence());
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(List<Stage> stages) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Stage(int position, String title, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, String stageType, String location, String mapDefinitionId,
            String mapAssetId, String mapAssetLocator, List<String> enemies, String boss, String clearCondition, String failureCondition, List<String> rewards,
            List<String> branchIds, List<SourceCitation> evidence) {
        Stage {
            npcOrClues = npcOrClues == null ? List.of() : npcOrClues;
            endingIds = endingIds == null ? List.of() : endingIds;
            enemies = enemies == null ? List.of() : enemies;
            rewards = rewards == null ? List.of() : rewards;
            branchIds = branchIds == null ? endingIds : branchIds;
            evidence = evidence == null ? List.of() : evidence;
            mapAssetId = mapAssetId == null ? "" : mapAssetId;
            mapAssetLocator = mapAssetLocator == null ? "" : mapAssetLocator;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record SourceCitation(String documentType, String documentId, long extractionVersion, String locator, String quote, double confidence) {}
}
