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
    MapModelPort mapModelPort(GmCompletionAdapter adapter) {
        return input -> adapter.complete(
                "map-" + UUID.randomUUID(), input.toString(), text -> {
                    // TODO: implement real JSON parsing from AI response
                    return new MapModelPort.MapOutput(20, 20, text);
                });
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
    AdventureStoryPlanController aiAdventureStoryPlanController(SpringAiChatAdapter adapter, AgentEndpointRegistry endpointRegistry, com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${local-ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${local-ai.ollama.chat-model:qwen3:8b}") String ollamaModel,
            @org.springframework.beans.factory.annotation.Value("${ai.codex.executable:codex}") String codexExecutable,
            @org.springframework.beans.factory.annotation.Value("${ai.codex.work-directory:.}") String codexWorkDirectory,
            @org.springframework.beans.factory.annotation.Value("${ai.codex.timeout:PT5M}") java.time.Duration codexTimeout,
            @org.springframework.beans.factory.annotation.Value("${ai-game-master.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            @org.springframework.beans.factory.annotation.Value("${rule-knowledge.base-url:${RULE_KNOWLEDGE_BASE_URL:http://127.0.0.1:8080/}}") String ruleKnowledgeBaseUrl) {
        return new AdventureStoryPlanController(adapter, objectMapper, endpointRegistry, ollamaBaseUrl, ollamaModel, codexExecutable, codexWorkDirectory, codexTimeout,
                "medium", new ApiRequestGuard(internalToken), ruleKnowledgeBaseUrl);
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

    @Bean
    GmAgentController gmAgentController(GmCompletionAdapter adapter, com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                        @Value("${ai-game-master.integration.internal-token:}") String internalToken) {
        return new GmAgentController(adapter, objectMapper, new ApiRequestGuard(internalToken));
    }
}
