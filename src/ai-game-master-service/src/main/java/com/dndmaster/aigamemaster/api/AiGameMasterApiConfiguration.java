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
import com.dndmaster.aigamemaster.configuration.GmProviderProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class AiGameMasterApiConfiguration {

    @Bean
    SceneModelPort sceneModelPort(SpringAiChatAdapter adapter, com.fasterxml.jackson.databind.ObjectMapper mapper) {
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
    RuleAnswerModelPort ruleAnswerModelPort(SpringAiChatAdapter adapter) {
        return request -> adapter.complete(
                "rule-" + UUID.randomUUID(), request.situation(), text -> {
                    // TODO: implement real JSON parsing from AI response
                    return new RuleAnswerOutput(EvidenceStatus.INSUFFICIENT, null, List.of(), List.of(), true);
                });
    }

    @Bean
    AdjudicationModelPort adjudicationModelPort() {
        return input -> new AdjudicationModelPort.AdjudicationOutput(
                "pending: authoritative runtime resolution required", "deterministic-runtime-boundary");
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
    AdventureStoryPlanController aiAdventureStoryPlanController(GmCompletionAdapter adapter, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
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
        return new GmCompletionRouter(ollama, properties);
    }

    @Bean
    GmAgentController gmAgentController(GmCompletionAdapter adapter, com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                        @Value("${ai-game-master.integration.internal-token:}") String internalToken) {
        return new GmAgentController(adapter, objectMapper, new ApiRequestGuard(internalToken));
    }
}
