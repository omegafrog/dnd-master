package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.ports.AdjudicationModelPort;
import com.dndmaster.aigamemaster.application.ports.MapModelPort;
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
        return prompt -> new SceneOutput(prompt.scenarioId(), prompt.ruleSetId(),
                ScenarioAlignment.WITHIN_SELECTED_SCENARIO,
                adapter.complete("scene-" + UUID.randomUUID(), prompt.value(), text -> groundedScene(mapper, text, evidenceCount(prompt.value()))), List.of());
    }

    private static String groundedScene(com.fasterxml.jackson.databind.ObjectMapper mapper, String text, int evidenceCount) {
        try { var root=mapper.readTree(text); var lines=new java.util.ArrayList<String>(); for(var item:root.path("facts")){lines.add("[E"+evidence(item,evidenceCount)+"] "+item.path("text").asText());} for(var item:root.path("choices")){lines.add("[E"+evidence(item,evidenceCount)+"] "+item.path("number").asInt()+". "+item.path("text").asText());} if(lines.size()!=5)throw new IllegalArgumentException("five grounded lines required"); return String.join("\n",lines); } catch(Exception e){ throw new IllegalArgumentException("invalid grounded scene JSON",e); }
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
                new GmPrompt("ROLE=MAP_LAYOUT_GM\n"
                        + "SCENARIO=" + input.selectedScenario() + "\n"
                        + "CURRENT_CONTEXT=" + input.currentContext() + "\n"
                        + "MAP_DATA=" + input.mapData() + "\n"
                        + "MAP_IMAGE=" + (input.imageDataUri().isBlank() ? "not provided" : "provided; inspect the attached image") + "\n"
                        + "OUTPUT_CONTRACT=Return exactly one JSON object with width, height, boundaries, obstacles, doors, playerStart, and rationale. "
                        + "Treat MAP_DATA.gridWidth and MAP_DATA.gridHeight as a rough initial suggestion. When MAP_IMAGE is provided, choose a practical width and height that fit the visible map layout; when it is not provided, preserve the suggested dimensions. "
                        + "boundaries must be an array of shared cell sides written x,y,HORIZONTAL,WALL or x,y,VERTICAL,DOOR. A HORIZONTAL side x,y spans above cell x,y; a VERTICAL side x,y spans left of cell x,y. Put visible walls and closed doors in boundaries. obstacles and doors must be empty arrays unless an entire cell is blocked. "
                        + "playerStart must be one grid cell written as x,y or an empty string. "
                        + "Use only the supplied map data and scenario evidence. Do not invent a structure that is not supported by the supplied data. "
                        + "When MAP_IMAGE is provided, inspect the attached image and convert clearly visible wall and door lines to boundaries. Prefer continuous visible wall lines. "
                        + "Treat authored obstacle, door, and player-start coordinates as user-confirmed evidence and preserve them. If the image is unclear, omit the uncertain cell instead of guessing. "
                        + "Keep every coordinate inside the returned width and height. A closed door cell must not also be an obstacle. "
                        + "Do not use markdown or any text outside the JSON object.", input.imageDataUri()),
                text -> text))
                        .orTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .join();
            } catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException failure) {
                // Map preparation must remain usable when the optional GM provider
                // is unavailable or returns malformed output. Authored map data is
                // still preserved by the combat-map service after this fallback.
                return deterministicMapFallback(input);
            }
            return parseMap(mapper, raw);
        };
    }

    private static MapModelPort.MapOutput deterministicMapFallback(MapModelPort.MapInput input) {
        int width = extractPositive(input.mapData(), "gridWidth", 20);
        int height = extractPositive(input.mapData(), "gridHeight", 20);
        return new MapModelPort.MapOutput(width, height,
                "GM provider unavailable; deterministic empty layout used.", List.of(), List.of(), "");
    }

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
            if (obstacles.stream().anyMatch(doors::contains)) throw new IllegalArgumentException("door cannot be an obstacle");
            String playerStart = root.path("playerStart").asText("").trim();
            if (!playerStart.isBlank()) validatePosition(playerStart, width, height, "playerStart");
            if (obstacles.contains(playerStart)) throw new IllegalArgumentException("player start cannot be an obstacle");
            if (doors.contains(playerStart)) throw new IllegalArgumentException("player start cannot be a door");
            return new MapModelPort.MapOutput(width, height, root.path("rationale").asText(""), obstacles, doors, boundaries, playerStart);
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
            IntentClassificationModelPort intentClassificationPort) {
        return new AiGameMasterController(sceneService, adjudicationPort, ruleAnswerService, mapPort, intentClassificationPort);
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
