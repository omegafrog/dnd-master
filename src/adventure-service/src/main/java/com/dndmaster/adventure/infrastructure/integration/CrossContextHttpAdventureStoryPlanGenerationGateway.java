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
            if (!knownCitations.isEmpty() && parsed.stages().stream().anyMatch(stage -> stage.evidence().isEmpty())) {
                throw new IllegalStateException("AI returned a story stage without source evidence");
            }
            if (parsed.stages().stream().flatMap(stage -> stage.evidence().stream()).anyMatch(item -> !knownCitations.contains(item.documentId() + ":" + item.locator()))) {
                throw new IllegalStateException("AI returned an unknown source citation");
            }
            return parsed.stages().stream().map(stage -> new AdventureStoryPlanStage(stage.position(), stage.title(), stage.goal(), stage.conflict(), stage.transitionCondition(), stage.npcOrClues(), stage.endingIds(), List.of(),
                    stage.stageType() == null ? com.dndmaster.adventure.domain.adventure.AdventureStageType.EVENT : com.dndmaster.adventure.domain.adventure.AdventureStageType.valueOf(stage.stageType()),
                    stage.location(), stage.mapDefinitionId() == null || stage.mapDefinitionId().isBlank() ? null : UUID.fromString(stage.mapDefinitionId()), stage.mapAssetId(), stage.mapAssetLocator(),
                    stage.enemies(), stage.boss(), stage.clearCondition(), stage.failureCondition(), stage.rewards(), stage.branchIds(),
                    stage.evidence().stream().map(item -> new com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence(item.documentType(), UUID.fromString(item.documentId()), item.extractionVersion(), item.locator(), item.quote(), item.confidence())).toList())).toList();
        } catch (HttpTimeoutException e) { throw new IllegalStateException("story plan AI timed out after " + timeout, e); }
        catch (IOException e) { throw new IllegalStateException("story plan AI response malformed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("story plan AI interrupted", e); }
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
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record SourceCitation(String documentType, String documentId, long extractionVersion, String locator, String quote, double confidence) {}
}
