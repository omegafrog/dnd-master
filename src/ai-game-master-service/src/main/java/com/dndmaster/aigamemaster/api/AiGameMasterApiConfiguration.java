package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.ports.AdjudicationModelPort;
import com.dndmaster.aigamemaster.application.ports.MapModelPort;
import com.dndmaster.aigamemaster.application.ports.MapEntryPlacementModelPort;
import com.dndmaster.aigamemaster.application.intent.IntentClassificationModelPort;
import com.dndmaster.aigamemaster.application.intent.IntentClassificationOutput;
import com.dndmaster.aigamemaster.application.rule.*;
import com.dndmaster.aigamemaster.application.scene.*;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.CharacterTagCompletionPort;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionRouter;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexAppServerClient;
import com.dndmaster.aigamemaster.infrastructure.ai.GmPrompt;
import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import com.dndmaster.aigamemaster.configuration.LocalOllamaProperties;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointStore;
import com.dndmaster.aigamemaster.infrastructure.endpoint.InMemoryAgentEndpointStore;
import com.dndmaster.aigamemaster.infrastructure.endpoint.JdbcAgentEndpointStore;
import org.springframework.beans.factory.ObjectProvider;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class AiGameMasterApiConfiguration {
    private static final double MIN_REVIEW_CANDIDATE_CONFIDENCE = .78;

    @Bean
    AgentEndpointStore agentEndpointStore(ObjectProvider<DataSource> dataSource) {
        DataSource available = dataSource.getIfAvailable();
        return available == null ? new InMemoryAgentEndpointStore() : new JdbcAgentEndpointStore(available);
    }

    @Bean
    AgentEndpointRegistry agentEndpointRegistry(AgentEndpointStore store, GmProviderProperties defaults, LocalOllamaProperties localOllama) {
        AgentEndpointRegistry registry = new AgentEndpointRegistry(store);
        if (registry.list().isEmpty()) {
            AgentEndpoint.Provider provider = defaults.provider().equals("ollama") ? AgentEndpoint.Provider.OLLAMA
                    : defaults.provider().equals("codex-cli") ? AgentEndpoint.Provider.CODEX_CLI : AgentEndpoint.Provider.OPENAI_COMPATIBLE;
            registry.save(new AgentEndpoint(UUID.randomUUID(), "default", provider, provider == AgentEndpoint.Provider.OLLAMA ? localOllama.baseUrl() : defaults.baseUrl(), defaults.model(),
                    provider == AgentEndpoint.Provider.OPENAI_COMPATIBLE ? "OPENAI_API_KEY" : null, true, java.time.Instant.now()));
        }
        return registry;
    }

    @Bean(destroyMethod = "close")
    CodexAppServerClient codexAppServerClient(
            @Value("${ai.codex.executable:codex}") String codexExecutable,
            @Value("${ai.codex.work-directory:/tmp}") String codexWorkDirectory,
            @Value("${ai.codex.timeout:PT5M}") java.time.Duration codexTimeout,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return CodexAppServerClient.shared(codexExecutable, java.nio.file.Path.of(codexWorkDirectory), codexTimeout, objectMapper);
    }

    @Bean
    SceneModelPort sceneModelPort(GmCompletionAdapter adapter, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return prompt -> {
            String grounded = adapter.complete("scene-" + UUID.randomUUID(), prompt.value(),
                    text -> groundedScene(mapper, text, evidenceCount(prompt.value())));
            ScenarioAlignment alignment = grounded.lines().anyMatch(line -> line.startsWith("[RUNTIME_FACT]"))
                    ? ScenarioAlignment.RUNTIME_INTERACTION : ScenarioAlignment.WITHIN_SELECTED_SCENARIO;
            return new SceneOutput(prompt.scenarioId(), prompt.ruleSetId(), alignment, grounded, List.of());
        };
    }

    private static String groundedScene(com.fasterxml.jackson.databind.ObjectMapper mapper, String text, int evidenceCount) {
        try {
            var root = mapper.readTree(text);
            var facts = root.path("facts");
            var choices = root.path("choices");
            if (!facts.isArray() || facts.size() != 2) throw new IllegalArgumentException("exactly two facts required");
            if (!choices.isArray() || choices.size() != 3) throw new IllegalArgumentException("exactly three choices required");
            var numbers = new java.util.HashSet<Integer>();
            for (var item : choices) {
                if (!item.has("number") || !item.path("number").canConvertToInt()) {
                    throw new IllegalArgumentException("choice number is required");
                }
                int number = item.path("number").intValue();
                if (number < 1 || number > 3 || !numbers.add(number)) {
                    throw new IllegalArgumentException("choice numbers must be unique integers from one to three");
                }
            }
            var lines = new java.util.ArrayList<String>();
            for (var item : facts) lines.add(groundedSceneLine(item, evidenceCount, false));
            for (var item : choices) lines.add(groundedSceneLine(item, evidenceCount, true));
            if (lines.size() != 5) throw new IllegalArgumentException("five grounded lines required");
            return String.join("\n", lines);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid grounded scene JSON", e);
        }
    }

    private static String groundedSceneLine(com.fasterxml.jackson.databind.JsonNode item, int evidenceCount, boolean choice) {
        String grounding = item.path("grounding").asText("CANONICAL").trim().toUpperCase(java.util.Locale.ROOT);
        String text = item.path("text").asText("").trim();
        if (text.isBlank()) throw new IllegalArgumentException("grounded scene text is required");
        if ("RUNTIME".equals(grounding) || "NPC".equals(grounding)) {
            String marker = choice ? "[RUNTIME] " : "[RUNTIME_FACT] ";
            return marker + (choice ? item.path("number").asInt() + ". " : "") + text;
        }
        if (!"CANONICAL".equals(grounding)) throw new IllegalArgumentException("invalid scene grounding");
        return "[E" + evidence(item, evidenceCount) + "] "
                + (choice ? item.path("number").asInt() + ". " : "") + text;
    }
    private static int evidence(com.fasterxml.jackson.databind.JsonNode item,int count){String value=item.path("evidence").asText().replaceAll("\\D","");if(value.isBlank()&&count==1)return 1;int number=value.isBlank()?0:Integer.parseInt(value);if(number<1||number>count)throw new IllegalArgumentException("invalid evidence");return number;}
    private static int evidenceCount(String prompt){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\[E(\\d+)]").matcher(prompt);int count=0;while(m.find())count=Math.max(count,Integer.parseInt(m.group(1)));return count;}

    @Bean
    RuleAnswerModelPort ruleAnswerModelPort(GmCompletionAdapter adapter) {
        return request -> adapter.complete(
                "rule-" + UUID.randomUUID(), request.situation(), text -> {
                    // TODO: implement real JSON parsing from AI response
                    return new RuleAnswerOutput(EvidenceStatus.INSUFFICIENT, null, List.of(), List.of(), true);
                });
    }

    @Bean
    AdjudicationModelPort adjudicationModelPort(GmCompletionAdapter adapter) {
        return input -> adapter.complete(
                "adjudicate-" + UUID.randomUUID(), input.toString(), text -> {
                    // TODO: implement real JSON parsing from AI response
                    return new AdjudicationModelPort.AdjudicationOutput(text, "parsed-rule-basis");
                });
    }

    @Bean
    MapModelPort mapModelPort(GmCompletionAdapter adapter, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return input -> {
            String raw;
            try {
                raw = java.util.concurrent.CompletableFuture.supplyAsync(() -> adapter.complete(
                "map-" + UUID.randomUUID(),
                new GmPrompt("ROLE=" + mapRole(input) + "\n"
                        + "SCENARIO=" + input.selectedScenario() + "\n"
                        + "CURRENT_CONTEXT=" + input.currentContext() + "\n"
                        + "MAP_DATA=" + input.mapData() + "\n"
                        + "MAP_IMAGE=" + (input.imageDataUri().isBlank() ? "not provided" : "provided; inspect the attached image") + "\n"
                        + "OUTPUT_CONTRACT=Return exactly one JSON object with width, height, boundaries, obstacles, doors, playerStart, playerStartProposal, and rationale. "
                        + "When MAP_DATA.gridConfirmed is true, MAP_DATA.gridWidth, MAP_DATA.gridHeight, MAP_DATA.gridOriginX, MAP_DATA.gridOriginY, and MAP_DATA.gridCellSize are the user's confirmed image geometry. Preserve width and height exactly, inspect only the rectangle covered by that grid, and never recalculate or move the grid. "
                        + "If MAP_DATA.crop is non-empty, use it as the user's saved image crop while interpreting the same confirmed grid. "
                        + "When MAP_DATA.gridConfirmed is false, treat the dimensions as a rough initial suggestion and preserve them when no image is provided. "
                        + "boundaries must be an array of shared cell sides written x,y,HORIZONTAL,WALL or x,y,VERTICAL,DOOR. A HORIZONTAL side x,y spans above cell x,y; a VERTICAL side x,y spans left of cell x,y. Put visible walls and closed doors in boundaries. obstacles and doors must be empty arrays unless an entire cell is blocked. "
                        + "playerStart is retained only for input compatibility and must not determine runtime placement. playerStartProposal must be an object with status (PROPOSED or UNRESOLVED), position (x,y or empty), evidence (array of concrete scenario/map facts), confidence (0 to 1), and source. "
                        + "Determine the scenario's actual entry path from CURRENT_CONTEXT, especially FIRST_NARRATION, PLAYER_ACTION, and GM_NARRATION. On map-entry activation these lines are the authoritative evidence for the party's first position. Use FIRST_NARRATION to identify the starting place and PLAYER_ACTION/GM_NARRATION to identify the transition into the map. If they explicitly describe opening/crossing a door, descending stairs, or entering a room, inspect the attached map and MUST return a non-empty playerStartProposal for the cell where the movement ends. For a completed descent, choose the destination floor cell immediately beyond the bottom end of the visible stairs; do not choose the staircase graphic itself, the room center, or the geometric map center. For a completed door crossing, choose the first floor cell immediately beyond the doorway. The proposal evidence must quote the relevant narrative fact and describe the matching map feature. If several cells are possible, choose the closest destination cell and give confidence at least 0.5. Do not return an empty proposal merely because the feature is not aligned to a map edge. Never infer a map side from words such as 지하, cellar, basement, upstairs, downstairs, left, right, upper, or lower, but do use those words as room-entry evidence when paired with a described door, stair, or passage. Only use an empty proposal when CURRENT_CONTEXT contains no explicit map-entry action. "
                        + "For MAP_ENTRY_PLACEMENT_AGENT, preserve the supplied map geometry and use the response only to provide playerStartProposal; do not redesign the map. "
                        + "Use only the supplied map data and scenario evidence. Do not invent a structure that is not supported by the supplied data. "
                        + "When MAP_IMAGE is provided, inspect the attached image and convert only clearly visible, continuous wall and door lines to boundaries. Be conservative: a dark floor texture, furniture edge, shadow, grid line, or decoration is not a wall. When unsure, omit it. "
                        + "Treat authored obstacle, door, boundary, and player-start coordinates as user-confirmed evidence and preserve them. If the image is unclear, omit the uncertain cell instead of guessing. "
                        + "For image-derived candidates, optionally include candidates with x, y, orientation, kind, confidence (0 to 1), evidence, and source. Confidence is a review score, not a guarantee. "
                        + "Keep every coordinate inside the returned width and height. A closed door cell must not also be an obstacle. "
                        + "Do not use markdown or any text outside the JSON object.", input.imageDataUri()),
                text -> text))
                        .orTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .join();
            } catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException failure) {
                // Map preparation must remain usable when the optional GM provider
                // is unavailable or returns malformed output. Authored map data is
                // still preserved by the combat-map service after this fallback.
                return deterministicMapFallback(input, mapper);
            }
            MapModelPort.MapOutput parsed;
            try {
                parsed = parseMap(mapper, raw);
            } catch (RuntimeException malformed) {
                // A malformed optional provider response must not discard the
                // user's confirmed geometry; use the same deterministic review
                // draft as a timeout/unavailable provider.
                if (isMapContractViolation(malformed)) throw malformed;
                return deterministicMapFallback(input, mapper);
            }
            parsed = preserveAuthoredMap(input, mapper, parsed);
            if (!input.imageDataUri().isBlank()) {
                ImageBoundaryDetector.Detection detection = ImageBoundaryDetector.detect(input, mapper, parsed.width(), parsed.height());
                AuthoredMap authored = authoredMap(input, mapper, parsed.width(), parsed.height());
                List<MapModelPort.MapBoundaryCandidate> candidates = withoutAuthoredCandidates(
                        mergeCandidates(parsed.candidates(), detection.candidates()), authored.boundaries());
                if (parsed.boundaries().isEmpty() && !detection.boundaries().isEmpty()) {
                    return new MapModelPort.MapOutput(parsed.width(), parsed.height(),
                            detection.rationale(), parsed.obstacles(), parsed.doors(),
                            detection.boundaries(), parsed.playerStart(), candidates, parsed.playerStartProposal());
                }
                if (!candidates.equals(parsed.candidates())) {
                    return new MapModelPort.MapOutput(parsed.width(), parsed.height(), parsed.structuredLayers(),
                            parsed.obstacles(), parsed.doors(), parsed.boundaries(), parsed.playerStart(), candidates, parsed.playerStartProposal());
                }
            }
            AuthoredMap authored = authoredMap(input, mapper, parsed.width(), parsed.height());
            List<MapModelPort.MapBoundaryCandidate> candidates = withoutAuthoredCandidates(parsed.candidates(), authored.boundaries());
            return candidates.equals(parsed.candidates()) ? parsed : new MapModelPort.MapOutput(parsed.width(), parsed.height(),
                    parsed.structuredLayers(), parsed.obstacles(), parsed.doors(), parsed.boundaries(), parsed.playerStart(), candidates, parsed.playerStartProposal());
        };
    }

    @Bean
    MapEntryPlacementModelPort mapEntryPlacementModelPort(GmCompletionAdapter adapter,
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return input -> {
            try {
                MapEntryPlacementModelPort.EntryPlacementOutput output = java.util.concurrent.CompletableFuture.supplyAsync(() -> adapter.complete(
                    "map-entry-placement-" + UUID.randomUUID(),
                    new GmPrompt("ROLE=MAP_ENTRY_PLACEMENT_AGENT\n"
                            + "TARGET_SCENE=" + input.targetScene() + "\n"
                            + "PLAYER_ACTION=" + input.action() + "\n"
                            + "GM_JUDGMENT=" + input.judgment() + "\n"
                            + "GM_NARRATION=" + input.narration() + "\n"
                            + "MAP_DATA=" + input.mapData() + "\n"
                            + "MAP_IMAGE=" + (input.imageDataUri().isBlank() ? "not provided" : "provided; inspect the attached image") + "\n"
                            + "TASK=Interpret the completed entry transition, find the visual entry anchor on the supplied map image, project it to the nearest walkable destination cell, and return ranked candidates. The player has already completed the action. For DESCEND_STAIRS choose the first walkable destination-floor cell immediately beyond the bottom end of the visible stairs. For a door crossing choose the first walkable cell immediately inside the doorway. Use the map data only to validate the projected cell; do not redesign the map or choose the room center. Do not infer a side from words such as north, south, upper, lower, cellar, or basement. Those words are evidence about the transition only when paired with a visible stair, hatch, door, or passage.\n"
                            + "OUTPUT_CONTRACT=Return exactly one JSON object with status, entryInterpretation, candidates, and reason. status must be RESOLVED, AMBIGUOUS, or UNRESOLVED. entryInterpretation must contain transition, targetScene, anchor, placementRelation, and evidence. candidates must be an array of at most three ranked objects with x, y, confidence, source, anchor, reason, and evidence. x and y are zero-based GRID CELL INDICES, not image pixels, not image coordinates, and not map measurements; with the supplied 20x20 grid every candidate must satisfy 0 <= x < 20 and 0 <= y < 20. evidence must contain concrete quoted narrative facts and a description of the matching map feature. Return UNRESOLVED with an empty candidates array only when the action does not describe a completed map entry or no entry anchor can be localized in the supplied image. If the anchor is visible but several adjacent cells are possible, return them ranked instead of returning an empty array.\n"
                            + "Do not use markdown, code fences, or any text outside the JSON object.", input.imageDataUri()),
                    text -> parseEntryPlacement(mapper, text)))
                        .orTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                        .join();
                return output;
            } catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException failure) {
                return new MapEntryPlacementModelPort.EntryPlacementOutput("UNRESOLVED",
                        new MapEntryPlacementModelPort.EntryInterpretation("", input.targetScene(), "", "", ""),
                        List.of(), "진입 위치 분석 제공자가 응답하지 않았습니다.");
            }
        };
    }

    private static MapEntryPlacementModelPort.EntryPlacementOutput parseEntryPlacement(
            com.fasterxml.jackson.databind.ObjectMapper mapper, String text) {
        try {
            var root = mapper.readTree(text);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("entry placement response must be an object");
            String status = root.path("status").asText("UNRESOLVED");
            var interpretationNode = root.path("entryInterpretation");
            if (!interpretationNode.isObject()) interpretationNode = root.path("interpretation");
            MapEntryPlacementModelPort.EntryInterpretation interpretation = new MapEntryPlacementModelPort.EntryInterpretation(
                    interpretationNode.path("transition").asText(""), interpretationNode.path("targetScene").asText(""),
                    interpretationNode.path("anchor").asText(""), interpretationNode.path("placementRelation").asText(""),
                    interpretationNode.path("evidence").asText(""));
            List<MapEntryPlacementModelPort.Candidate> candidates = new java.util.ArrayList<>();
            if (root.path("candidates").isArray()) for (var node : root.path("candidates")) {
                List<String> evidence = new java.util.ArrayList<>();
                if (node.path("evidence").isArray()) for (var item : node.path("evidence")) evidence.add(item.asText());
                candidates.add(new MapEntryPlacementModelPort.Candidate(node.path("x").asInt(-1), node.path("y").asInt(-1),
                        node.path("confidence").asDouble(Double.NaN), node.path("source").asText("MAP_IMAGE"),
                        node.path("anchor").asText(""), node.path("reason").asText(""), evidence));
            }
            return new MapEntryPlacementModelPort.EntryPlacementOutput(status, interpretation, candidates,
                    root.path("reason").asText(""));
        } catch (RuntimeException | java.io.IOException exception) {
            throw new IllegalArgumentException("invalid map entry placement response", exception);
        }
    }

    private static String mapRole(MapModelPort.MapInput input) {
        return "MAP_ENTRY_PLACEMENT_AGENT".equals(input.selectedScenario())
                ? "MAP_ENTRY_PLACEMENT_AGENT" : "MAP_LAYOUT_GM";
    }

    private static MapModelPort.MapOutput deterministicMapFallback(MapModelPort.MapInput input,
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        int width = extractPositive(input.mapData(), "gridWidth", 20);
        int height = extractPositive(input.mapData(), "gridHeight", 20);
        ImageBoundaryDetector.Detection detection = ImageBoundaryDetector.detect(input, mapper, width, height);
        AuthoredMap authored = authoredMap(input, mapper, width, height);
        List<String> boundaries = new java.util.ArrayList<>(authored.boundaries());
        detection.boundaries().forEach(boundary -> {
            if (boundaries.stream().noneMatch(existing -> sameBoundarySide(existing, boundary))) boundaries.add(boundary);
        });
        return new MapModelPort.MapOutput(width, height,
                boundaries.isEmpty()
                        ? "AI 제공자가 응답하지 않았고 지도에서 확실한 선을 찾지 못했습니다."
                        : "GM 제공자가 응답하지 않아 이미지 선분 분석으로 벽·문 초안을 만들었습니다.",
                authored.obstacles(), authored.doors(), boundaries, authored.playerStart(),
                withoutAuthoredCandidates(detection.candidates(), authored.boundaries()));
    }

    private static MapModelPort.MapOutput preserveAuthoredMap(MapModelPort.MapInput input,
            com.fasterxml.jackson.databind.ObjectMapper mapper, MapModelPort.MapOutput parsed) {
        AuthoredMap authored = authoredMap(input, mapper, parsed.width(), parsed.height());
        List<String> obstacles = new java.util.ArrayList<>(parsed.obstacles());
        authored.obstacles().forEach(value -> { if (!obstacles.contains(value)) obstacles.add(value); });
        List<String> doors = new java.util.ArrayList<>(parsed.doors());
        authored.doors().forEach(value -> { if (!doors.contains(value)) doors.add(value); });
        List<String> boundaries = new java.util.ArrayList<>(parsed.boundaries());
        for (String value : authored.boundaries()) {
            boundaries.removeIf(existing -> sameBoundarySide(existing, value));
            boundaries.add(value);
        }
        if (obstacles.stream().anyMatch(doors::contains)) throw new IllegalArgumentException("door cannot be an obstacle");
        String playerStart = parsed.playerStart().isBlank() ? authored.playerStart() : parsed.playerStart();
        if (!playerStart.isBlank() && (obstacles.contains(playerStart) || doors.contains(playerStart))) {
            throw new IllegalArgumentException("player start cannot be blocked");
        }
        return new MapModelPort.MapOutput(parsed.width(), parsed.height(), parsed.structuredLayers(),
                obstacles, doors, boundaries, playerStart, parsed.candidates(), parsed.playerStartProposal());
    }

    private static boolean isMapContractViolation(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("door cannot be an obstacle")
                || message.contains("player start cannot be an obstacle")
                || message.contains("player start cannot be a door"));
    }

    private static List<MapModelPort.MapBoundaryCandidate> mergeCandidates(
            List<MapModelPort.MapBoundaryCandidate> primary,
            List<MapModelPort.MapBoundaryCandidate> supplemental) {
        List<MapModelPort.MapBoundaryCandidate> merged = new java.util.ArrayList<>(primary);
        for (MapModelPort.MapBoundaryCandidate candidate : supplemental) {
            if (merged.stream().noneMatch(existing -> existing.x() == candidate.x()
                    && existing.y() == candidate.y()
                    && existing.orientation().equals(candidate.orientation()))) {
                merged.add(candidate);
            }
        }
        return List.copyOf(merged);
    }

    private static List<MapModelPort.MapBoundaryCandidate> withoutAuthoredCandidates(
            List<MapModelPort.MapBoundaryCandidate> candidates, List<String> authoredBoundaries) {
        return candidates.stream().filter(candidate -> candidate.confidence() >= MIN_REVIEW_CANDIDATE_CONFIDENCE)
                .filter(candidate -> authoredBoundaries.stream().noneMatch(authored -> {
            String[] parts = authored.split(",", -1);
            return parts.length >= 3 && Integer.toString(candidate.x()).equals(parts[0].trim())
                    && Integer.toString(candidate.y()).equals(parts[1].trim())
                    && candidate.orientation().equalsIgnoreCase(parts[2].trim());
        })).toList();
    }

    private static AuthoredMap authoredMap(MapModelPort.MapInput input,
            com.fasterxml.jackson.databind.ObjectMapper mapper, int width, int height) {
        try {
            var root = mapper.readTree(input.mapData());
            List<String> obstacles = authoredPositions(root.path("authoredObstacles"), width, height);
            List<String> doors = authoredPositions(root.path("authoredDoors"), width, height);
            List<String> boundaries = new java.util.ArrayList<>();
            if (root.path("authoredBoundaries").isArray()) {
                for (var item : root.path("authoredBoundaries")) {
                    try {
                        String value = item.asText("").trim();
                        if (!value.isBlank()) {
                            String[] parts = value.split(",", -1);
                            if (parts.length == 4 || parts.length == 5) {
                                int x = Integer.parseInt(parts[0].trim());
                                int y = Integer.parseInt(parts[1].trim());
                                String orientation = parts[2].trim().toUpperCase(java.util.Locale.ROOT);
                                String kind = parts[3].trim().toUpperCase(java.util.Locale.ROOT);
                                if (("HORIZONTAL".equals(orientation) && x >= 0 && x < width && y >= 0 && y <= height)
                                        || ("VERTICAL".equals(orientation) && x >= 0 && x <= width && y >= 0 && y < height)) {
                                    if (!"WALL".equals(kind) && !"DOOR".equals(kind)) continue;
                                    String encoded = x + "," + y + "," + orientation + "," + kind
                                            + "," + (parts.length == 5 && Boolean.parseBoolean(parts[4].trim()));
                                    if (boundaries.stream().noneMatch(existing -> sameBoundarySide(existing, encoded))) boundaries.add(encoded);
                                }
                            }
                        }
                    } catch (RuntimeException ignored) { /* keep other authored lines intact */ }
                }
            }
            String player = root.path("authoredPlayerStart").asText("").trim();
            if (!player.isBlank()) {
                try { validatePosition(player, width, height, "authoredPlayerStart"); }
                catch (RuntimeException ignored) { player = ""; }
            }
            return new AuthoredMap(List.copyOf(obstacles), List.copyOf(doors), List.copyOf(boundaries), player);
        } catch (Exception ignored) {
            return new AuthoredMap(List.of(), List.of(), List.of(), "");
        }
    }

    private static List<String> authoredPositions(com.fasterxml.jackson.databind.JsonNode node, int width, int height) {
        if (!node.isArray()) return List.of();
        List<String> result = new java.util.ArrayList<>();
        for (var item : node) {
            String value = item.isTextual() ? item.asText().trim() : item.path("x").asText("") + "," + item.path("y").asText("");
            try { validatePosition(value, width, height, "authored map"); if (!result.contains(value)) result.add(value); }
            catch (RuntimeException ignored) { /* malformed authored values are ignored by the provider boundary */ }
        }
        return List.copyOf(result);
    }

    private static boolean sameBoundarySide(String left, String right) {
        String[] a = left.split(",", -1);
        String[] b = right.split(",", -1);
        return a.length >= 3 && b.length >= 3 && a[0].trim().equals(b[0].trim())
                && a[1].trim().equals(b[1].trim()) && a[2].trim().equalsIgnoreCase(b[2].trim());
    }

    private record AuthoredMap(List<String> obstacles, List<String> doors, List<String> boundaries, String playerStart) {}

    private static int extractPositive(String text, String key, int fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\"" + key + "\\\"\\s*:\\s*(\\d+)")
                .matcher(text == null ? "" : text);
        if (!matcher.find()) return fallback;
        try { return Math.max(1, Integer.parseInt(matcher.group(1))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static MapModelPort.MapOutput parseMap(com.fasterxml.jackson.databind.ObjectMapper mapper, String text) {
        try {
            var root = mapper.readTree(text);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("map model response must be an object");
            int width = root.path("width").asInt(0);
            int height = root.path("height").asInt(0);
            if (width < 1 || height < 1) throw new IllegalArgumentException("map model dimensions must be positive");
            List<String> obstacles = positions(root.path("obstacles"), width, height, "obstacles");
            List<String> doors = positions(root.path("doors"), width, height, "doors");
            List<String> boundaries = boundaries(root.path("boundaries"), width, height);
            List<MapModelPort.MapBoundaryCandidate> candidates = candidates(root.path("candidates"), width, height);
            if (obstacles.stream().anyMatch(doors::contains)) throw new IllegalArgumentException("door cannot be an obstacle");
            String playerStart = root.path("playerStart").asText("").trim();
            if (!playerStart.isBlank()) validatePosition(playerStart, width, height, "playerStart");
            if (obstacles.contains(playerStart)) throw new IllegalArgumentException("player start cannot be an obstacle");
            if (doors.contains(playerStart)) throw new IllegalArgumentException("player start cannot be a door");
            MapModelPort.PlayerStartProposal proposal = playerStartProposal(root.path("playerStartProposal"), width, height);
            if (proposal != null && !proposal.position().isBlank()
                    && (obstacles.contains(proposal.position()) || doors.contains(proposal.position()))) {
                throw new IllegalArgumentException("player start proposal is blocked");
            }
            return new MapModelPort.MapOutput(width, height, root.path("rationale").asText(""), obstacles, doors, boundaries, playerStart, candidates, proposal);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid map model response", exception);
        }
    }

    private static List<String> positions(com.fasterxml.jackson.databind.JsonNode node, int width, int height, String field) {
        if (!node.isArray()) throw new IllegalArgumentException(field + " must be an array");
        List<String> result = new java.util.ArrayList<>();
        for (var value : node) {
            String position = value.isTextual() ? value.asText().trim()
                    : value.path("x").asText("") + "," + value.path("y").asText("");
            validatePosition(position, width, height, field);
            if (!result.contains(position)) result.add(position);
        }
        return List.copyOf(result);
    }

    private static MapModelPort.PlayerStartProposal playerStartProposal(com.fasterxml.jackson.databind.JsonNode node,
            int width, int height) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (!node.isObject()) throw new IllegalArgumentException("playerStartProposal must be an object");
        String position = node.path("position").asText("").trim();
        if (position.isBlank()) return null;
        validatePosition(position, width, height, "playerStartProposal.position");
        List<String> evidence = new java.util.ArrayList<>();
        if (node.path("evidence").isArray()) for (var item : node.path("evidence")) evidence.add(item.asText());
        return new MapModelPort.PlayerStartProposal(position, node.path("confidence").asDouble(Double.NaN), evidence,
                node.path("source").asText("SCENARIO_ENTRY"), node.path("status").asText("PROPOSED"));
    }

    private static List<String> boundaries(com.fasterxml.jackson.databind.JsonNode node, int width, int height) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) throw new IllegalArgumentException("boundaries must be an array");
        List<String> result = new java.util.ArrayList<>();
        for (var value : node) {
            String boundary = value.isTextual() ? value.asText().trim()
                    : value.path("x").asText("") + "," + value.path("y").asText("") + ","
                    + value.path("orientation").asText("") + "," + value.path("kind").asText("");
            String[] parts = boundary.split(",", -1);
            if (parts.length != 4) throw new IllegalArgumentException("boundaries contains an invalid edge");
            int x = Integer.parseInt(parts[0].trim()); int y = Integer.parseInt(parts[1].trim());
            boolean horizontal = "HORIZONTAL".equals(parts[2].trim()); boolean vertical = "VERTICAL".equals(parts[2].trim());
            boolean wall = "WALL".equals(parts[3].trim()); boolean door = "DOOR".equals(parts[3].trim());
            if ((!horizontal && !vertical) || (!wall && !door) || (horizontal && (x < 0 || x >= width || y < 0 || y > height)) || (vertical && (x < 0 || x > width || y < 0 || y >= height))) throw new IllegalArgumentException("boundaries contains an invalid edge");
            String encoded = x + "," + y + "," + parts[2].trim() + "," + parts[3].trim() + ",false";
            if (!result.contains(encoded)) result.add(encoded);
        }
        return List.copyOf(result);
    }

    private static List<MapModelPort.MapBoundaryCandidate> candidates(com.fasterxml.jackson.databind.JsonNode node,
            int width, int height) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) throw new IllegalArgumentException("candidates must be an array");
        List<MapModelPort.MapBoundaryCandidate> result = new java.util.ArrayList<>();
        for (var value : node) {
            if (!value.isObject()) throw new IllegalArgumentException("candidates contains an invalid item");
            int x = value.path("x").asInt(-1);
            int y = value.path("y").asInt(-1);
            String orientation = value.path("orientation").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            String kind = value.path("kind").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            boolean inside = ("HORIZONTAL".equals(orientation) && x >= 0 && x < width && y >= 0 && y <= height)
                    || ("VERTICAL".equals(orientation) && x >= 0 && x <= width && y >= 0 && y < height);
            if (!inside) throw new IllegalArgumentException("candidates contains an out-of-grid edge");
            List<String> evidence = new java.util.ArrayList<>();
            if (value.path("evidence").isArray()) for (var item : value.path("evidence")) evidence.add(item.asText());
            result.add(new MapModelPort.MapBoundaryCandidate(x, y, orientation, kind,
                    value.path("confidence").asDouble(Double.NaN), evidence,
                    value.path("source").asText("IMAGE_RULES")));
        }
        return List.copyOf(result);
    }

    private static void validatePosition(String value, int width, int height, String field) {
        String[] pair = value.split(",", -1);
        if (pair.length != 2) throw new IllegalArgumentException(field + " contains an invalid cell");
        try {
            int x = Integer.parseInt(pair[0].trim());
            int y = Integer.parseInt(pair[1].trim());
            if (x < 0 || y < 0 || x >= width || y >= height) throw new IllegalArgumentException(field + " contains an out-of-grid cell");
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " contains an invalid cell", exception);
        }
    }

    @Bean
    IntentClassificationModelPort intentClassificationModelPort(GmCompletionAdapter adapter) {
        return input -> adapter.complete(
                "intent-" + UUID.randomUUID(), input.question(), IntentClassificationOutput::fromModelText);
    }

    @Bean
    ScenarioPromptFactory scenarioPromptFactory() {
        return new ScenarioPromptFactory();
    }

    @Bean
    ScenarioBoundSceneService scenarioBoundSceneService(
            ScenarioPromptFactory prompts, SceneModelPort model) {
        return new ScenarioBoundSceneService(prompts, model);
    }

    @Bean
    GroundedRuleAnswerService groundedRuleAnswerService(RuleAnswerModelPort model) {
        return new GroundedRuleAnswerService(model);
    }

    @Bean
    ResolutionCandidateController resolutionCandidateController(
            com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter adapter,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            AgentEndpointRegistry endpointRegistry,
            @org.springframework.beans.factory.annotation.Value("${ai.codex.executable:codex}") String codexExecutable,
            @org.springframework.beans.factory.annotation.Value("${ai.codex.work-directory:.}") String codexWorkDirectory,
            @org.springframework.beans.factory.annotation.Value("${ai.codex.timeout:PT5M}") java.time.Duration codexTimeout) {
        return new ResolutionCandidateController(adapter, objectMapper, endpointRegistry, codexExecutable, codexWorkDirectory, codexTimeout);
    }

    @Bean
    CharacterInputTagController characterInputTagController(CharacterTagCompletionPort adapter, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new CharacterInputTagController(adapter, objectMapper);
    }

    @Bean
    AiGameMasterController aiGameMasterController(
            ScenarioBoundSceneService sceneService,
            AdjudicationModelPort adjudicationPort,
            GroundedRuleAnswerService ruleAnswerService,
            MapModelPort mapPort,
            IntentClassificationModelPort intentClassificationPort,
            MapEntryPlacementModelPort mapEntryPlacementPort) {
        return new AiGameMasterController(sceneService, adjudicationPort, ruleAnswerService, mapPort, intentClassificationPort,
                mapEntryPlacementPort);
    }

    @Bean
    @Primary
    GmCompletionAdapter gmCompletionAdapter(SpringAiChatAdapter ollama, GmProviderProperties properties, AgentEndpointRegistry endpointRegistry,
                                             @Value("${ai.codex.executable:codex}") String codexExecutable,
                                             @Value("${ai.codex.work-directory:.}") String codexWorkDirectory,
                                             @Value("${ai.codex.timeout:PT5M}") java.time.Duration codexTimeout) {
        properties.validate();
        return new GmCompletionRouter(ollama, properties, endpointRegistry, codexExecutable, java.nio.file.Path.of(codexWorkDirectory), codexTimeout);
    }

}
