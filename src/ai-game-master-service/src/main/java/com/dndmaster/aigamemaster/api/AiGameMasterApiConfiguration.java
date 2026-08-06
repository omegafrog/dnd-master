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
import com.dndmaster.aigamemaster.infrastructure.ai.OpenAiGmProvider;
import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class AiGameMasterApiConfiguration {

    @Bean
    SceneModelPort sceneModelPort(SpringAiChatAdapter adapter) {
        return prompt -> adapter.complete(
                "scene-" + UUID.randomUUID(), prompt.value(), text -> {
                    // TODO: implement real JSON parsing from AI response
                    return new SceneOutput(UUID.randomUUID(), UUID.randomUUID(),
                            ScenarioAlignment.WITHIN_SELECTED_SCENARIO, text, List.of());
                });
    }

    @Bean
    RuleAnswerModelPort ruleAnswerModelPort(SpringAiChatAdapter adapter) {
        return request -> adapter.complete(
                "rule-" + UUID.randomUUID(), request.situation(), text -> {
                    // TODO: implement real JSON parsing from AI response
                    return new RuleAnswerOutput(EvidenceStatus.INSUFFICIENT, null, List.of(), List.of(), true);
                });
    }

    @Bean
    AdjudicationModelPort adjudicationModelPort(SpringAiChatAdapter adapter) {
        return input -> adapter.complete(
                "adjudicate-" + UUID.randomUUID(), input.toString(), text -> {
                    // TODO: implement real JSON parsing from AI response
                    return new AdjudicationModelPort.AdjudicationOutput(text, "parsed-rule-basis");
                });
    }

    @Bean
    MapModelPort mapModelPort(SpringAiChatAdapter adapter) {
        return input -> adapter.complete(
                "map-" + UUID.randomUUID(), input.toString(), text -> {
                    // TODO: implement real JSON parsing from AI response
                    return new MapModelPort.MapOutput(20, 20, text);
                });
    }

    @Bean
    IntentClassificationModelPort intentClassificationModelPort(SpringAiChatAdapter adapter) {
        return input -> adapter.complete(
                "intent-" + UUID.randomUUID(), input.question(), IntentClassificationOutput::fromModelText);
    }

    @Bean
    ScenarioPromptFactory scenarioPromptFactory() {
        return new ScenarioPromptFactory();
    }

    @Bean
    AdventureStoryPlanController aiAdventureStoryPlanController(SpringAiChatAdapter adapter, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new AdventureStoryPlanController(adapter, objectMapper);
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
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new ResolutionCandidateController(adapter, objectMapper);
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
    GmCompletionAdapter gmCompletionAdapter(SpringAiChatAdapter ollama, GmProviderProperties properties) {
        properties.validate();
        if (properties.provider().equals("ollama")) return ollama;
        return new OpenAiGmProvider(HttpClient.newHttpClient(), properties.baseUrl(), properties.apiKey(),
                properties.model(), properties.reasoning(), properties.timeout());
    }

    @Bean
    GmAgentController gmAgentController(GmCompletionAdapter adapter, com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                        @Value("${ai-game-master.integration.internal-token:}") String internalToken) {
        return new GmAgentController(adapter, objectMapper, new ApiRequestGuard(internalToken));
    }
}
