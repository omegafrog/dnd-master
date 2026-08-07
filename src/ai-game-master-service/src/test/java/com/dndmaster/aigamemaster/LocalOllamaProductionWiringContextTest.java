package com.dndmaster.aigamemaster;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dndmaster.aigamemaster.infrastructure.ai.ObservedOllamaChatModel;
import com.dndmaster.aigamemaster.infrastructure.ai.ObservedOllamaEmbeddingModel;
import com.dndmaster.aigamemaster.infrastructure.ai.OllamaCallObservability;
import com.dndmaster.aigamemaster.infrastructure.ai.OllamaCircuitOpenException;
import com.dndmaster.aigamemaster.infrastructure.ai.OllamaModelObservabilityPostProcessor;
import com.dndmaster.aigamemaster.infrastructure.ai.ProviderTimeoutException;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

@ExtendWith(OutputCaptureExtension.class)
@ContextConfiguration(initializers = LocalOllamaProductionWiringContextTest.BackendInitializer.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "local-ai.ollama.base-url=http://127.0.0.1:18089",
        "local-ai.ollama.request-timeout=3s",
        "local-ai.ollama.circuit-failure-threshold=2",
        "local-ai.ollama.circuit-reset-timeout=30s",
        "local-ai.ollama.retry-max-attempts=1"
})
class LocalOllamaProductionWiringContextTest {
    private static final String SECRET_PROMPT = "PRIVATE_PROMPT_DO_NOT_LOG";
    private static final String SECRET_RESPONSE = "PRIVATE_RESPONSE_DO_NOT_LOG";
    private static final WireMockServer OLLAMA = startBackend();

    @Autowired ChatModel chatModel;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired SpringAiChatAdapter chatAdapter;
    @Autowired OllamaModelObservabilityPostProcessor observabilityPostProcessor;
    @Autowired Environment environment;

    public static final class BackendInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            if (!OLLAMA.isRunning()) {
                throw new IllegalStateException("Controlled Ollama backend did not start");
            }
        }
    }

    @AfterAll
    static void stopBackend() {
        OLLAMA.stop();
    }

    @Test
    void productionBeansUseOneValidatedSourceAndObserveSuccessTimeoutAndCircuit(CapturedOutput output) {
        assertThat(chatModel).isInstanceOf(ObservedOllamaChatModel.class);
        assertThat(((ObservedOllamaChatModel) chatModel).delegate().getClass().getName()).contains("OllamaChatModel");
        assertThat(embeddingModel).isInstanceOf(ObservedOllamaEmbeddingModel.class);
        assertThat(((ObservedOllamaEmbeddingModel) embeddingModel).delegate().getClass().getName())
                .contains("OllamaEmbeddingModel");
        assertThat(environment.getProperty("spring.ai.ollama.base-url"))
                .isEqualTo(environment.getProperty("local-ai.ollama.base-url"));
        assertThat(environment.getProperty("spring.ai.retry.max-attempts", Integer.class)).isEqualTo(1);

        assertThat(chatModel.call(new Prompt("safe success probe"))).isNotNull();
        assertThat(embeddingModel.embed("safe embedding probe")).hasSize(3);

        OLLAMA.stubFor(post(urlEqualTo("/api/chat")).atPriority(1)
                .willReturn(aResponse().withFixedDelay(5_000).withHeader("Content-Type", "application/json")
                        .withBody(chatResponse())));

        assertThatThrownBy(() -> chatAdapter.complete("timeout-one", SECRET_PROMPT, value -> value))
                .isInstanceOf(ProviderTimeoutException.class);
        assertThat(observabilityPostProcessor.chatObservability().state())
                .isEqualTo(OllamaCallObservability.CircuitState.CLOSED);
        assertThatThrownBy(() -> chatAdapter.complete("timeout-two", SECRET_PROMPT, value -> value))
                .isInstanceOf(ProviderTimeoutException.class);
        assertThat(observabilityPostProcessor.chatObservability().state())
                .isEqualTo(OllamaCallObservability.CircuitState.OPEN);
        assertThatThrownBy(() -> chatModel.call(new Prompt(SECRET_PROMPT)))
                .isInstanceOf(OllamaCircuitOpenException.class);

        OLLAMA.verify(3, postRequestedFor(urlEqualTo("/api/chat")));
        OLLAMA.verify(1, postRequestedFor(urlEqualTo("/api/embed")));
        OllamaCallObservability.Snapshot chat = observabilityPostProcessor.chatObservability().snapshot();
        assertThat(chat.successes()).isEqualTo(1);
        assertThat(chat.failures()).isEqualTo(2);
        assertThat(chat.timeouts()).isEqualTo(2);
        assertThat(chat.circuitRejections()).isEqualTo(1);
        assertThat(observabilityPostProcessor.embeddingObservability().snapshot().successes()).isEqualTo(1);
        assertThat(output).doesNotContain(SECRET_PROMPT, SECRET_RESPONSE, "secret-token");
        assertThat(output).contains("payload=[REDACTED]");
    }

    @Test
    void rejectsIndependentAndRemoteBaseUrlBeforeApplicationContextStarts() {
        assertStartupRejected("--local-ai.ollama.base-url=http://127.0.0.1:18089",
                "--spring.ai.ollama.base-url=http://127.0.0.1:18090");
        assertStartupRejected("--local-ai.ollama.base-url=http://ollama.example:11434");
    }

    private static void assertStartupRejected(String... arguments) {
        SpringApplication application = new SpringApplication(AiGameMasterServiceApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        assertThatThrownBy(() -> application.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ollama base URL");
    }

    private static WireMockServer startBackend() {
        WireMockServer server = new WireMockServer(
                WireMockConfiguration.options().port(18089).http2PlainDisabled(true));
        server.start();
        server.stubFor(get(urlEqualTo("/api/tags")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"models\":[{\"name\":\""
                        + com.dndmaster.aigamemaster.configuration.LocalOllamaProperties.DEFAULT_CHAT_MODEL + "\"},"
                        + "{\"name\":\"qwen3-embedding:0.6b\"}]}")));
        server.stubFor(post(urlEqualTo("/api/chat")).atPriority(5).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(chatResponse())));
        server.stubFor(post(urlEqualTo("/api/embed")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"model\":\"qwen3-embedding:0.6b\",\"embeddings\":[[0.1,0.2,0.3]]}")));
        return server;
    }

    private static String chatResponse() {
        return "{\"model\":\"" + com.dndmaster.aigamemaster.configuration.LocalOllamaProperties.DEFAULT_CHAT_MODEL
                + "\",\"created_at\":\"2026-07-19T00:00:00Z\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + SECRET_RESPONSE
                + "\"},\"done\":true}";
    }
}
