package com.dndmaster.adventure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AdventureApiConfigurationTest {
    private final AdventureApiConfiguration configuration = new AdventureApiConfiguration();

    @Test
    void crossContextAdaptersUseProviderConfigRatherThanFixedPorts() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        URI identityAccessUri = URI.create("http://identity-access.internal:18081/");
        URI ruleKnowledgeUri = URI.create("http://rule-knowledge.internal:18083/");
        URI aiGameMasterUri = URI.create("http://ai-game-master.internal:18084/");

        assertThat(baseUri(configuration.playerSessionLookupPort(objectMapper, identityAccessUri.toString())))
                .isEqualTo(identityAccessUri);
        assertThat(baseUri(configuration.knowledgeDocumentLookupPort(objectMapper, ruleKnowledgeUri.toString())))
                .isEqualTo(ruleKnowledgeUri);
        assertThat(baseUri(configuration.legacyScenarioIngestionPort(objectMapper, ruleKnowledgeUri.toString())))
                .isEqualTo(ruleKnowledgeUri);
        assertThat(baseUri(configuration.scenarioSourceExcerptPort(objectMapper, ruleKnowledgeUri.toString())))
                .isEqualTo(ruleKnowledgeUri);
        assertThat(baseUri(configuration.resolutionExtractionPort(objectMapper, aiGameMasterUri.toString())))
                .isEqualTo(aiGameMasterUri);
        assertThat(baseUri(configuration.ruleIntentClassificationPort(objectMapper, aiGameMasterUri.toString())))
                .isEqualTo(aiGameMasterUri);
        assertThat(baseUri(configuration.initialSourceContextProposalPort(objectMapper, aiGameMasterUri.toString())))
                .isEqualTo(aiGameMasterUri);
    }

    @Test
    void scenarioCompilationAdaptersUseConfiguredTimeout() throws Exception {
        Duration timeout = Duration.ofSeconds(120);

        assertThat(timeout(configuration.scenarioSourceExcerptPort(
                new ObjectMapper(), "http://rule-knowledge.internal/", timeout))).isEqualTo(timeout);
        assertThat(timeout(configuration.resolutionExtractionPort(
                new ObjectMapper(), "http://ai-game-master.internal/", timeout))).isEqualTo(timeout);
    }

    private static URI baseUri(Object gateway) throws ReflectiveOperationException {
        Field field = gateway.getClass().getDeclaredField("baseUri");
        field.setAccessible(true);
        return (URI) field.get(gateway);
    }

    private static Duration timeout(Object gateway) throws ReflectiveOperationException {
        Field field = gateway.getClass().getDeclaredField("timeout");
        field.setAccessible(true);
        return (Duration) field.get(gateway);
    }
}
