package com.dndmaster.aigamemaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocalAiBenchmarkRouteTest {
    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 5;
    private static final long TTFT_TARGET_MS = 10_000;
    private static final Prompt CHAT_PROMPT = new Prompt("Reply with a short DND acknowledgement.");
    private static final String EMBEDDING_INPUT = "DND local AI live benchmark probe";

    @Autowired ChatModel chatModel;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    @Test
    void measuresStreamingResponseStartCompletionAndEmbeddingThroughSpring() {
        for (int iteration = 0; iteration < WARMUP_RUNS; iteration++) {
            timeToFirstText();
            timeFullCompletion();
            timeEmbedding();
        }

        long[] ttft = new long[MEASURED_RUNS];
        long[] completion = new long[MEASURED_RUNS];
        long[] embedding = new long[MEASURED_RUNS];
        for (int iteration = 0; iteration < MEASURED_RUNS; iteration++) {
            ttft[iteration] = timeToFirstText();
            completion[iteration] = timeFullCompletion();
            embedding[iteration] = timeEmbedding();
        }

        assertThat(http.getForObject("http://127.0.0.1:" + port + "/actuator/health", String.class))
                .contains("\"status\":\"UP\"");
        assertThat(http.getForEntity("http://127.0.0.1:" + port + "/v3/api-docs", String.class)
                .getStatusCode().is2xxSuccessful()).isTrue();

        long ttftP95 = p95(ttft);
        System.out.printf(
                "LOCAL_AI_BENCHMARK {\"warmup_runs\":%d,\"measured_runs\":%d,\"ttft_samples_ms\":%s,\"ttft_p95_ms\":%d,\"completion_samples_ms\":%s,\"completion_p95_ms\":%d,\"embedding_samples_ms\":%s,\"embedding_p95_ms\":%d}%n",
                WARMUP_RUNS, MEASURED_RUNS, Arrays.toString(ttft), ttftP95,
                Arrays.toString(completion), p95(completion), Arrays.toString(embedding), p95(embedding));
        assertThat(ttftP95).isLessThanOrEqualTo(TTFT_TARGET_MS);
    }

    private long timeToFirstText() {
        long started = System.nanoTime();
        ChatResponse first = chatModel.stream(CHAT_PROMPT)
                .filter(LocalAiBenchmarkRouteTest::hasText)
                .blockFirst(Duration.ofSeconds(30));
        long elapsed = elapsedMillis(started);
        assertThat(first).isNotNull();
        return elapsed;
    }

    private long timeFullCompletion() {
        long started = System.nanoTime();
        ChatResponse response = chatModel.call(CHAT_PROMPT);
        long elapsed = elapsedMillis(started);
        assertThat(response).matches(LocalAiBenchmarkRouteTest::hasText);
        return elapsed;
    }

    private long timeEmbedding() {
        long started = System.nanoTime();
        float[] embedding = embeddingModel.embed(EMBEDDING_INPUT);
        long elapsed = elapsedMillis(started);
        assertThat(embedding).isNotEmpty();
        assertThat(embedding.length).isEqualTo(embeddingModel.dimensions());
        for (float value : embedding) {
            assertThat(Float.isFinite(value)).isTrue();
        }
        return elapsed;
    }

    private static boolean hasText(ChatResponse response) {
        return response != null && response.getResult() != null && response.getResult().getOutput() != null
                && response.getResult().getOutput().getText() != null
                && !response.getResult().getOutput().getText().isBlank();
    }

    private static long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static long p95(long[] samples) {
        long[] ordered = samples.clone();
        Arrays.sort(ordered);
        return ordered[(int) Math.ceil(ordered.length * 0.95) - 1];
    }
}
