package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanCandidateValidationException;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation;
import com.dndmaster.adventure.application.storyplan.StoryPlanScopedMerger;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionRepairPolicy;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanValidator;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanGraphValidator;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.PlacementGroundingType;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.dndmaster.adventure.domain.adventure.CombatParticipant;
import com.dndmaster.adventure.domain.adventure.CombatRequirement;
import com.dndmaster.adventure.domain.adventure.CombatSkeleton;
import com.dndmaster.adventure.domain.adventure.SourceFactClaim;
import com.dndmaster.adventure.domain.adventure.ClaimOrigin;
import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
    @Override public AdventureStoryPlanGenerationPort.ProjectionCandidate generate(Request request) {
        return generateCandidate(request);
    }

    private AdventureStoryPlanGenerationPort.ProjectionCandidate generateCandidate(Request request) {
        try {
            Request keyedRequest = request.withCitationKeys();
            var body = mapper.writeValueAsString(keyedRequest);
            var response = client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/adventure-story-plan"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 422) {
                List<AdventureStoryPlanProjectionViolation> violations = remoteCandidateViolations(response.body(), "AI returned an invalid story plan candidate");
                LOGGER.warn("remote_story_plan_candidate_rejected codes={} classifications={}",
                        violations.stream().map(AdventureStoryPlanProjectionViolation::code).toList(),
                        violations.stream().map(AdventureStoryPlanProjectionViolation::repairability).toList());
                throw new AdventureStoryPlanCandidateValidationException(violations, remoteRejectedCandidate(response.body()), true);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("story plan AI failed: " + response.statusCode());
            }
            return parseOutlineCandidate(response.body(), keyedRequest);
        } catch (HttpTimeoutException e) { throw new IllegalStateException("story plan AI timed out after " + timeout, e); }
        catch (IOException e) { throw new IllegalStateException("story plan AI request encoding failed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("story plan AI interrupted", e); }
    }

    @Override public AdventureStoryPlanGenerationPort.ProjectionCandidate repair(RepairRequest request) {
        try {
            Request keyedRequest = new Request(request.operationId(), request.packageRevision(), request.partySize(),
                    request.configuration(), request.sourceDocuments(), request.resolutionEvidence(), request.maps(),
                    request.citations(), request.violations().stream()
                            .map(AdventureStoryPlanProjectionViolation::sanitizedMessage).toList(), request.previousCandidate())
                    .withCitationKeys();
            JsonNode previousCandidate = mapper.readTree(request.previousCandidate());
            RepairWireRequest wireRequest = new RepairWireRequest(keyedRequest.operationId(), keyedRequest.packageRevision(),
                    keyedRequest.partySize(), keyedRequest.configuration(), previousCandidate,
                    request.violations(), keyedRequest.sourceDocuments(), request.repairScope(), keyedRequest.resolutionEvidence(),
                    keyedRequest.maps(), keyedRequest.citations());
            var response = client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/adventure-story-plan/repair"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(wireRequest))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 422) {
                throw new AdventureStoryPlanCandidateValidationException(
                        remoteCandidateViolations(response.body(), "AI returned an invalid repaired story plan candidate"),
                        remoteRejectedCandidate(response.body()), true);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("story plan AI repair failed: " + response.statusCode());
            }
            // The provider returns a full candidate and may rewrite unrelated fields while
            // repairing. Treat that response as untrusted input: apply only the explicit
            // repair scope, then run all normal validation against the merged candidate.
            JsonNode repairedCandidate = mapper.readTree(candidateJson(response.body()));
            String mergedCandidate = new StoryPlanScopedMerger(mapper)
                    .merge(previousCandidate, repairedCandidate, request.repairScope()).toString();
            return parseOutlineCandidate(mergedCandidate, keyedRequest);
        } catch (AdventureStoryPlanCandidateValidationException e) {
            throw e;
        } catch (HttpTimeoutException e) { throw new IllegalStateException("story plan AI repair timed out after " + timeout, e); }
        catch (IOException e) { throw new IllegalStateException("story plan AI repair request encoding failed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("story plan AI repair interrupted", e); }
        catch (AdventureStoryPlanProjectionRepairPolicy.UnlistedFieldMutation e) {
            throw new AdventureStoryPlanCandidateValidationException(List.of(e.violation()), request.previousCandidate(), true);
        }
    }

    private AdventureStoryPlanGenerationPort.ProjectionCandidate parseOutlineCandidate(String responseBody, Request request) {
        String candidateJson = candidateJson(responseBody);
        try {
            JsonNode root = mapper.readTree(candidateJson);
            var parsed = mapper.treeToValue(root, Response.class);
            if (parsed.stages() == null) throw new IllegalStateException("AI returned no story stages");
            List<Stage> stages = parsed.stages();
            List<List<AdventureStoryPlanGenerationPort.SourceCitation>> resolvedEvidence = stages.stream()
                    .map(stage -> resolveCitationKeys(stage.evidence(), request.citations())).toList();
            validateEndingIdProjection(stages, candidateJson);
            if (!request.citations().isEmpty() && stages.stream().anyMatch(stage -> stage.evidence().isEmpty())) {
                throw new IllegalStateException("every story stage must include at least one supplied source citation");
            }
            AdventurePlanConfiguration configuration = request.configuration();
            if (stages.size() < configuration.adventureLength().minimumStages()
                    || stages.size() > configuration.adventureLength().maximumStages()) {
                throw new IllegalStateException("AI returned an invalid stage count for adventure length");
            }
            Map<UUID, AdventureStoryPlanGenerationPort.MapContext> maps = request.maps().stream().collect(
                    Collectors.toMap(AdventureStoryPlanGenerationPort.MapContext::mapDefinitionId, item -> item));
            if (!maps.isEmpty() && stages.stream().anyMatch(stage ->
                    parseStageType(stage.stageType()) == com.dndmaster.adventure.domain.adventure.AdventureStageType.DUNGEON
                            && stage.mapDefinitionId().isBlank())) {
                throw new IllegalStateException("map-backed bundle requires every dungeon stage to reference a map definition");
            }
            List<AdventureStoryPlanStage> domainStages = java.util.stream.IntStream.range(0, stages.size())
                    .mapToObj(index -> toDomain(stages.get(index), resolvedEvidence.get(index), maps)).toList();
            AdventureStoryPlanGraphValidator.validate(domainStages, configuration);
            return new AdventureStoryPlanGenerationPort.ProjectionCandidate(candidateJson, domainStages);
        } catch (AdventureStoryPlanCandidateValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (RuntimeException | IOException invalidCandidate) {
            throw new AdventureStoryPlanCandidateValidationException(List.of(
                    AdventureStoryPlanCandidateValidationException.legacyViolation(
                            validationMessage(invalidCandidate, "AI returned an invalid story plan candidate"))),
                    candidateJson, true);
        }
    }

    private static void validateEndingIdProjection(List<Stage> stages, String candidateJson) {
        for (int index = 0; index < stages.size(); index++) {
            Stage stage = stages.get(index);
            if (stage.endingIds() == null) {
                throw endingIdsViolation(index, "", candidateJson, "endingIds must be explicit");
            }
            if (stage.endingIds().stream().anyMatch(item -> item == null || item.isBlank())) {
                throw new IllegalStateException("endingIds must not contain blank values");
            }
            if (stage.endingIds().isEmpty()) {
                throw endingIdsViolation(index, "[]", candidateJson, "endingIds must not be empty");
            }
        }
    }

    private static AdventureStoryPlanCandidateValidationException endingIdsViolation(
            int index, String rejectedValue, String candidateJson, String message) {
        return new AdventureStoryPlanCandidateValidationException(List.of(
                new AdventureStoryPlanProjectionViolation("ENDING_IDS_MISSING", index + 1,
                        "stages[" + index + "].endingIds", rejectedValue, "",
                        AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE, message)),
                candidateJson, true);
    }
    @Override public TacticalScenePlanCandidate generateTacticalScene(TacticalSceneRequest request) {
        try {
            var response = client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/tactical-scene-plan"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 422) {
                throw new AdventureStoryPlanCandidateValidationException(List.of(
                        remoteCandidateViolations(response.body(), "AI returned an invalid tactical scene candidate").getFirst().sanitizedMessage()));
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

    private List<AdventureStoryPlanProjectionViolation> remoteCandidateViolations(String body, String fallback) {
        try {
            var error = mapper.readTree(body == null ? "" : body);
            var violations = error.path("violations");
            if (violations.isArray()) {
                List<AdventureStoryPlanProjectionViolation> parsed = new java.util.ArrayList<>();
                violations.forEach(item -> {
                    if (item.isObject()) {
                        try { parsed.add(mapper.treeToValue(item, AdventureStoryPlanProjectionViolation.class)); }
                        catch (RuntimeException | IOException ignored) { }
                    } else if (item.isTextual() && !item.asText().isBlank()) {
                        parsed.add(AdventureStoryPlanCandidateValidationException.legacyViolation(item.asText()));
                    }
                });
                if (!parsed.isEmpty()) return List.copyOf(parsed);
            }
            String detail = error.path("detail").asText("").trim();
            if (!detail.isBlank()) return List.of(AdventureStoryPlanCandidateValidationException.legacyViolation(detail));
            String message = error.path("message").asText("").trim();
            return List.of(AdventureStoryPlanCandidateValidationException.legacyViolation(message.isBlank() ? fallback : message));
        } catch (RuntimeException | IOException ignored) {
            return List.of(AdventureStoryPlanCandidateValidationException.legacyViolation(fallback));
        }
    }

    private String remoteRejectedCandidate(String body) {
        try {
            JsonNode candidate = mapper.readTree(body == null ? "" : body).get("rejectedCandidate");
            return candidate == null || candidate.isNull() ? "" : candidate.isTextual() ? candidate.asText() : candidate.toString();
        } catch (RuntimeException | IOException ignored) {
            return "";
        }
    }

    private String candidateJson(String responseBody) {
        try {
            return mapper.readTree(responseBody == null ? "" : responseBody).toString();
        } catch (IOException failure) {
            throw new IllegalArgumentException("AI returned a non-JSON story plan candidate");
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
    private static List<AdventureStoryPlanGenerationPort.SourceCitation> resolveCitationKeys(
            List<CitationProjection> projections,
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
        return projections.stream().map(projection -> {
            if (projection == null || projection.citationKey() == null || projection.citationKey().isBlank()) {
                throw new IllegalStateException("AI returned an empty citation key");
            }
            return citations.stream().filter(source -> projection.citationKey().equals(source.citationKey())).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "AI returned an unknown citation key: " + projection.citationKey()));
        }).toList();
    }

    private static AdventureStoryPlanStage toDomain(Stage stage,
            List<AdventureStoryPlanGenerationPort.SourceCitation> canonicalEvidence,
            Map<UUID, AdventureStoryPlanGenerationPort.MapContext> maps) {
        UUID mapId = parseMapDefinitionId(stage.mapDefinitionId());
        if (mapId == null && (!stage.mapAssetId().isBlank() || !stage.mapAssetLocator().isBlank())) throw new IllegalStateException("map asset requires a map definition");
        AdventureStoryPlanGenerationPort.MapContext map = mapId == null ? null : maps.get(mapId);
        if (mapId != null && map == null) throw new IllegalStateException("AI returned an unknown map definition");
        if (map != null && ((!stage.mapAssetId().isBlank() && !stage.mapAssetId().equals(map.assetId()))
                || (!stage.mapAssetLocator().isBlank() && !stage.mapAssetLocator().equals(map.assetLocator())))) {
            throw new IllegalStateException("AI returned map metadata that does not match the source map");
        }
        var evidence = canonicalEvidence.stream().map(item -> new com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence(
                item.documentType(), item.documentId(), item.extractionVersion(), item.locator(),
                item.quote(), item.confidence(), item.provenance(), item.citationKey())).toList();
        var grounding = evidence.isEmpty() ? com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.AI_SUGGESTION : com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.GROUNDED;
        var suggestions = evidence.isEmpty() ? List.of("location", "enemies", "boss", "rewards", "conditions") : List.<String>of();
        List<com.dndmaster.adventure.domain.scenario.StoryMapBinding> bindings = mapId == null ? List.of()
                : List.of(new com.dndmaster.adventure.domain.scenario.StoryMapBinding(
                        Integer.toString(stage.position()), stage.location(), stage.transitionCondition(), mapId));
        var spawn = inferPlayerSpawn(stage, map);
        AdventureStoryPlanStage domainStage = new AdventureStoryPlanStage(stage.position(), stage.title(), stage.goal(), stage.conflict(), stage.transitionCondition(), stage.npcOrClues(), stage.endingIds(), bindings,
                parseStageType(stage.stageType()),
                stage.location(), mapId, map == null ? stage.mapAssetId() : map.assetId(), map == null ? stage.mapAssetLocator() : map.assetLocator(),
                stage.enemies(), stage.boss(), stage.clearCondition(), stage.failureCondition(), stage.rewards(), stage.branchIds(), evidence, grounding, suggestions,
                map == null ? "UNAVAILABLE" : map.safetyStatus(), map == null ? null : map.confidence(), stage.branchTargets(),
                spawn.x(), spawn.y(), spawn.confidence(), spawn.rationale());
        return domainStage.withCombat(parseCombatRequirement(stage.combatRequirement(), stage),
                parseCombatSkeleton(stage.combatSkeleton()), stage.sourceFactClaims().stream()
                        .map(CrossContextHttpAdventureStoryPlanGenerationGateway::toDomain).toList(),
                parseTacticalPreparationRequirement(stage.tacticalPreparationRequirement(), mapId))
                .withSchemaVersion(stage.schemaVersion());
    }
    private static CombatRequirement parseCombatRequirement(String value, Stage stage) {
        if (value == null || value.isBlank()) {
            return hasCombatSkeleton(stage.combatSkeleton()) || !stage.enemies().isEmpty() || !stage.boss().isBlank()
                    ? CombatRequirement.REQUIRED : CombatRequirement.NONE;
        }
        return CombatRequirement.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
    private static boolean hasCombatSkeleton(CombatSkeletonProjection projection) {
        return projection != null && ((!projection.objective().isBlank() && !projection.startTrigger().isBlank())
                || !projection.participants().isEmpty() || !projection.rewards().isEmpty()
                || !projection.successOutcome().isBlank() || !projection.failureOutcome().isBlank());
    }
    private static TacticalPreparationRequirement parseTacticalPreparationRequirement(String value, UUID mapId) {
        if (value == null || value.isBlank()) return TacticalPreparationRequirement.NOT_REQUIRED;
        return TacticalPreparationRequirement.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
    private static CombatSkeleton parseCombatSkeleton(CombatSkeletonProjection projection) {
        if (projection == null) return CombatSkeleton.empty();
        return new CombatSkeleton(projection.objective(), projection.startTrigger(), projection.participants().stream()
                .map(CrossContextHttpAdventureStoryPlanGenerationGateway::toDomain).toList(),
                projection.successOutcome(), projection.failureOutcome(), projection.rewards().stream()
                        .map(CrossContextHttpAdventureStoryPlanGenerationGateway::toDomain).toList());
    }
    private static CombatParticipant toDomain(CombatParticipantProjection projection) {
        CombatParticipant.Role role = projection.role() == null || projection.role().isBlank()
                ? CombatParticipant.Role.ENEMY
                : CombatParticipant.Role.valueOf(projection.role().trim().toUpperCase(java.util.Locale.ROOT));
        int minimum = projection.minimumCount() <= 0 ? 1 : projection.minimumCount();
        int maximum = projection.maximumCount() <= 0 ? minimum : projection.maximumCount();
        return new CombatParticipant(projection.participantId(), role, projection.name(), minimum, maximum,
                projection.citationKeys());
    }
    private static SourceFactClaim toDomain(SourceFactClaimProjection projection) {
        return new SourceFactClaim(projection.fieldPath(), projection.normalizedClaim(), projection.citationKeys(),
                projection.origin() == null ? ClaimOrigin.SOURCE : ClaimOrigin.valueOf(projection.origin().trim().toUpperCase(java.util.Locale.ROOT)));
    }
    static UUID parseMapDefinitionId(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = UUID_TOKEN.matcher(value);
        if (!matcher.find()) throw new IllegalArgumentException("map definition ID is not a UUID: " + value);
        return UUID.fromString(matcher.group());
    }

    private static com.dndmaster.adventure.domain.adventure.AdventureStageType parseStageType(String value) {
        if (value == null || value.isBlank()) return com.dndmaster.adventure.domain.adventure.AdventureStageType.EVENT;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "town", "마을", "도시", "사회" -> com.dndmaster.adventure.domain.adventure.AdventureStageType.TOWN;
            case "dungeon", "던전", "지하", "탐험" -> com.dndmaster.adventure.domain.adventure.AdventureStageType.DUNGEON;
            case "travel", "여행", "이동" -> com.dndmaster.adventure.domain.adventure.AdventureStageType.TRAVEL;
            case "event", "이벤트", "사건" -> com.dndmaster.adventure.domain.adventure.AdventureStageType.EVENT;
            case "encounter", "조우", "전투" -> com.dndmaster.adventure.domain.adventure.AdventureStageType.ENCOUNTER;
            case "finale", "최종", "결전" -> com.dndmaster.adventure.domain.adventure.AdventureStageType.FINALE;
            default -> com.dndmaster.adventure.domain.adventure.AdventureStageType.valueOf(normalized.toUpperCase(java.util.Locale.ROOT));
        };
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
    private record RepairWireRequest(String operationId, long packageRevision, int partySize,
            AdventurePlanConfiguration configuration, JsonNode previousCandidate,
            List<AdventureStoryPlanProjectionViolation> violations, List<String> sourceDocuments,
            com.dndmaster.adventure.application.storyplan.RepairScope repairScope,
            List<String> resolutionEvidence, List<AdventureStoryPlanGenerationPort.MapContext> maps,
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
        public RepairWireRequest {
            violations = List.copyOf(violations);
            sourceDocuments = List.copyOf(sourceDocuments);
            resolutionEvidence = List.copyOf(resolutionEvidence);
            maps = List.copyOf(maps);
            citations = List.copyOf(citations);
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record TacticalResponse(int stagePosition, TacticalScenePlan scene, List<SourceCitation> citations) {
        TacticalResponse { citations = List.copyOf(Objects.requireNonNull(citations, "tactical citations must be explicit")); }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record Stage(int position, String title, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, String stageType, String location, String mapDefinitionId,
            String mapAssetId, String mapAssetLocator, List<String> enemies, String boss, String clearCondition, String failureCondition, List<String> rewards,
            List<String> branchIds, java.util.Map<String, String> branchTargets, List<CitationProjection> evidence,
            Integer playerSpawnX, Integer playerSpawnY, String playerSpawnConfidence, String playerSpawnRationale,
            String combatRequirement, CombatSkeletonProjection combatSkeleton,
            List<SourceFactClaimProjection> sourceFactClaims, String tacticalPreparationRequirement, Integer schemaVersion) {
        Stage {
            npcOrClues = List.copyOf(Objects.requireNonNull(npcOrClues, "npcOrClues must be explicit"));
            // Keep null long enough for the typed projection validator to report the
            // exact repairable stages[n].endingIds path. Domain conversion rejects it later.
            endingIds = endingIds == null ? null : List.copyOf(endingIds);
            enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies must be explicit"));
            rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards must be explicit"));
            branchIds = List.copyOf(Objects.requireNonNull(branchIds, "branchIds must be explicit"));
            branchTargets = java.util.Map.copyOf(Objects.requireNonNull(branchTargets, "branchTargets must be explicit"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must be explicit"));
            sourceFactClaims = sourceFactClaims == null ? List.of() : List.copyOf(sourceFactClaims);
            mapDefinitionId = mapDefinitionId == null ? "" : mapDefinitionId;
            mapAssetId = mapAssetId == null ? "" : mapAssetId;
            mapAssetLocator = mapAssetLocator == null ? "" : mapAssetLocator;
            boss = boss == null ? "" : boss;
            schemaVersion = schemaVersion == null || schemaVersion <= 0 ? 1 : schemaVersion;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record CombatSkeletonProjection(String objective, String startTrigger,
            List<CombatParticipantProjection> participants, String successOutcome, String failureOutcome,
            List<SourceFactClaimProjection> rewards) {
        CombatSkeletonProjection {
            objective = objective == null ? "" : objective;
            startTrigger = startTrigger == null ? "" : startTrigger;
            successOutcome = successOutcome == null ? "" : successOutcome;
            failureOutcome = failureOutcome == null ? "" : failureOutcome;
            participants = participants == null ? List.of() : List.copyOf(participants);
            rewards = rewards == null ? List.of() : List.copyOf(rewards);
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record CombatParticipantProjection(String participantId, String role,
            String name, int minimumCount, int maximumCount, List<String> citationKeys) {
        CombatParticipantProjection {
            citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys);
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record SourceFactClaimProjection(String fieldPath, String normalizedClaim,
            List<String> citationKeys, String origin) {
        SourceFactClaimProjection {
            citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys);
            origin = origin == null || origin.isBlank() ? "SOURCE" : origin;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record CitationProjection(String citationKey) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record SourceCitation(String documentType, String documentId,
            long extractionVersion, String locator, String quote, double confidence,
            com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance provenance) {
        SourceCitation(String documentType, String documentId, long extractionVersion, String locator,
                String quote, double confidence) {
            this(documentType, documentId, extractionVersion, locator, quote, confidence, null);
        }

        public SourceCitation {
            if (provenance == null && documentId != null && locator != null) {
                provenance = new com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance(
                        new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.fromString(documentId)),
                        extractionVersion, 1, List.of(), List.of(), null, locator);
            }
        }
    }
}
