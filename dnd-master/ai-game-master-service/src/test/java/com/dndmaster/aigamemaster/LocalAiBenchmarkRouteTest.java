package com.dndmaster.aigamemaster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocalAiBenchmarkRouteTest {
    @Autowired ChatModel chatModel;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    @Test
    void callsActualOllamaModelsThroughSpringAndKeepsPublicHealthAvailable() {
        String response = chatModel.call(new Prompt("Reply with a short DND acknowledgement.")).getResult().getOutput().getText();
        float[] embedding = embeddingModel.embed("DND local AI live benchmark probe");

        assertThat(response).isNotBlank();
        assertThat(embedding).isNotEmpty();
        assertThat(embedding.length).isEqualTo(embeddingModel.dimensions());
        for (float value : embedding) {
            assertThat(Float.isFinite(value)).isTrue();
        }
        assertThat(http.getForObject("http://127.0.0.1:" + port + "/actuator/health", String.class))
                .contains("\"status\":\"UP\"");
        assertThat(http.getForEntity("http://127.0.0.1:" + port + "/v3/api-docs", String.class)
                .getStatusCode().is2xxSuccessful()).isTrue();
    }
}
