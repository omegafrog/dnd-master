package com.dndmaster.aigamemaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf("isOllamaAvailable")
class LocalAiBenchmarkRouteTest {
    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 5;
    private static final long COMPLETION_TARGET_MS = 30_000;
    private static final String PROMPT_TEMPLATE = "Repeat only the word DND separated by one space exactly %d times. "
            + "Output no punctuation, numbering, explanation, conclusion, or other word. Do not stop before the count.";
    private static final String EMBEDDING_INPUT = "DND local AI candidate benchmark probe";

    @Autowired ChatModel chatModel;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;
    @Value("${spring.ai.ollama.chat.options.num-predict}") int numPredict;

    @DynamicPropertySource
    static void overrideBenchmarkProperties(DynamicPropertyRegistry registry) {
        registry.add("local-ai.ollama.request-timeout", () -> env("LOCAL_AI_REQUEST_TIMEOUT", "120s"));
        registry.add("spring.ai.ollama.chat.options.num-predict",
                () -> Integer.parseInt(env("NUM_PREDICT", "32")));
    }

    @Test
    void measuresCandidateCompletionBoundaryThroughProductionSpringModels() {
        Prompt prompt = candidatePrompt();
        for (int iteration = 0; iteration < WARMUP_RUNS; iteration++) {
            timeToFirstText(prompt);
            timeFullCompletion(prompt);
            timeEmbedding();
        }

        long[] ttft = new long[MEASURED_RUNS];
        long[] completion = new long[MEASURED_RUNS];
        long[] embedding = new long[MEASURED_RUNS];
        int[] generatedTokens = new int[MEASURED_RUNS];
        double[] tokensPerSecond = new double[MEASURED_RUNS];
        for (int iteration = 0; iteration < MEASURED_RUNS; iteration++) {
            ttft[iteration] = timeToFirstText(prompt);
            CompletionSample completionSample = timeFullCompletion(prompt);
            completion[iteration] = completionSample.elapsedMillis();
            generatedTokens[iteration] = completionSample.generatedTokens();
            tokensPerSecond[iteration] = completionSample.tokensPerSecond();
            embedding[iteration] = timeEmbedding();
        }

        assertThat(http.getForObject("http://127.0.0.1:" + port + "/actuator/health", String.class))
                .contains("\"status\":\"UP\"");
        assertThat(http.getForEntity("http://127.0.0.1:" + port + "/v3/api-docs", String.class)
                .getStatusCode().is2xxSuccessful()).isTrue();

        long completionP95 = p95(completion);
        boolean capSaturated = Arrays.stream(generatedTokens).allMatch(tokens -> tokens == numPredict);
        System.out.printf(
                "LOCAL_AI_BENCHMARK {\"num_ctx\":4096,\"num_predict\":%d,\"prompt_sha256\":\"%s\",\"warmup_runs\":%d,\"measured_runs\":%d,\"ttft_samples_ms\":%s,\"ttft_p95_ms\":%d,\"completion_samples_ms\":%s,\"completion_p95_ms\":%d,\"embedding_samples_ms\":%s,\"embedding_p95_ms\":%d,\"generated_token_samples\":%s,\"cap_saturated\":%s,\"tokens_per_second_samples\":%s,\"tokens_per_second_p50\":%.3f,\"completion_threshold_ms\":%d,\"pass\":%s}%n",
                numPredict, sha256(prompt.getContents()), WARMUP_RUNS, MEASURED_RUNS,
                Arrays.toString(ttft), p95(ttft), Arrays.toString(completion), completionP95,
                Arrays.toString(embedding), p95(embedding), Arrays.toString(generatedTokens),
                capSaturated, decimalArray(tokensPerSecond), percentile(tokensPerSecond, 0.50), COMPLETION_TARGET_MS,
                completionP95 <= COMPLETION_TARGET_MS && capSaturated);
    }

    private Prompt candidatePrompt() {
        return new Prompt(PROMPT_TEMPLATE.formatted(Math.max(numPredict * 4L, 16_384L)));
    }

    private long timeToFirstText(Prompt prompt) {
        long started = System.nanoTime();
        ChatResponse first = chatModel.stream(prompt)
                .filter(LocalAiBenchmarkRouteTest::hasText)
                .blockFirst(Duration.ofSeconds(120));
        long elapsed = elapsedMillis(started);
        assertThat(first).isNotNull();
        return elapsed;
    }

    private CompletionSample timeFullCompletion(Prompt prompt) {
        long started = System.nanoTime();
        ChatResponse response = chatModel.call(prompt);
        long elapsed = elapsedMillis(started);
        assertThat(response).matches(LocalAiBenchmarkRouteTest::hasText);
        Usage usage = response.getMetadata().getUsage();
        assertThat(usage).isNotNull();
        assertThat(usage.getCompletionTokens()).isNotNull().isPositive();
        int tokens = usage.getCompletionTokens();
        return new CompletionSample(elapsed, tokens, tokens * 1_000.0 / Math.max(elapsed, 1));
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

    private static double percentile(double[] samples, double percentile) {
        double[] ordered = samples.clone();
        Arrays.sort(ordered);
        return ordered[(int) Math.ceil(ordered.length * percentile) - 1];
    }

    private static String decimalArray(double[] samples) {
        String[] values = Arrays.stream(samples).mapToObj(value -> "%.3f".formatted(value)).toArray(String[]::new);
        return "[" + String.join(", ", values) + "]";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record CompletionSample(long elapsedMillis, int generatedTokens, double tokensPerSecond) { }

    static boolean isOllamaAvailable() {
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:11434/api/tags"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .GET()
                    .build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
