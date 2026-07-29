package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.aigamemaster.api.AiStreamingResponseService;
import com.dndmaster.aigamemaster.infrastructure.ai.SafeAiAuditLogger;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.ruleknowledge.application.indexing.IndexingCommand;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexRepository;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.indexing.StructureDetectionPort;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.IndexStatus;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndexingPolicy;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ResponseAndIndexingPerformanceTest {
    private static final Duration FIRST_BYTE_LIMIT = Duration.ofSeconds(10);
    private static final Duration INDEXING_LIMIT = Duration.ofMinutes(5);
    private static final String RAW_RULE_TEXT = "ANCIENT_DRAGON_RULE_TEXT";
    private static final String PRIVATE_MAP_DATA = "GM_ONLY_TRAP_DOOR";

    @Test
    void firstByteP95AndHundredMegabyteIndexingMeetTargetsWithoutSensitiveTelemetry() throws Exception {
        Properties policy = telemetryPolicy();
        var telemetry = new TelemetryProbe(policy);
        var logs = new ArrayList<String>();
        var adapter = new SpringAiChatAdapter(
                new ImmediateStreamingModel(), 1,
                new SafeAiAuditLogger(message -> { logs.add(message); telemetry.log(message); }));
        var streaming = new AiStreamingResponseService(adapter);
        List<Long> firstByteNanos = new ArrayList<>();
        String privatePrompt = RAW_RULE_TEXT + " " + PRIVATE_MAP_DATA;

        for (int sample = 0; sample < 20; sample++) {
            long start = System.nanoTime();
            String first = streaming.stream("perf-" + sample, privatePrompt).blockFirst(FIRST_BYTE_LIMIT);
            long duration = System.nanoTime() - start;
            assertEquals("grounded answer", first);
            firstByteNanos.add(duration);
            telemetry.firstByte("perf-" + sample, duration);
        }

        firstByteNanos.sort(Comparator.naturalOrder());
        long p95 = firstByteNanos.get((int) Math.ceil(firstByteNanos.size() * 0.95) - 1);
        assertTrue(p95 <= FIRST_BYTE_LIMIT.toNanos(), () -> "first-byte p95 was " + Duration.ofNanos(p95));

        var repository = new MemoryIndexRepository();
        var embeddedCharacters = new AtomicLong();
        var indexing = new RulebookIndexingApplicationService(
                repository,
                (chunks, model, dimension) -> { chunks.forEach(chunk -> embeddedCharacters.addAndGet(chunk.content().length())); return List.of(); },
                text -> StructureDetectionPort.DetectedStructure.none(), 16_384);
        Rulebook rulebook = hundredMegabyteDeclaredRulebook();
        IndexKey key = new IndexKey(rulebook.id(), "deterministic-content-hash", "fake-e2e", "v1");
        long indexingStart = System.nanoTime();
        RulebookIndex index = indexing.indexContent(new IndexingCommand(rulebook, key, 3));
        long indexingDuration = System.nanoTime() - indexingStart;
        telemetry.indexing(rulebook.id().value().toString(), indexingDuration, index.status().name());

        assertEquals(IndexStatus.READY, index.status());
        assertEquals(rulebook.extractionResult().orElseThrow().content().orElseThrow().length(), embeddedCharacters.get());
        assertTrue(indexingDuration <= INDEXING_LIMIT.toNanos(),
                () -> "100MB-or-smaller indexing took " + Duration.ofNanos(indexingDuration));
        assertTrue(logs.stream().allMatch(log -> log.contains("payload=[REDACTED]")));

        String exported = telemetry.export();
        assertFalse(exported.contains(RAW_RULE_TEXT), "telemetry exposed raw rulebook text");
        assertFalse(exported.contains(PRIVATE_MAP_DATA), "telemetry exposed private map data");
        assertFalse(exported.toLowerCase().contains("grounded answer"), "telemetry exposed AI response text");
        assertTrue(exported.contains(policy.getProperty("metric.name")));
        assertTrue(policy.getProperty("redacted.fields").contains("map.private.layers"));
    }

    private static Rulebook hundredMegabyteDeclaredRulebook() {
        Rulebook rulebook = Rulebook.acceptUpload(
                RulebookId.generate(), new OwnerPlayerId(UUID.randomUUID()), RulebookFormat.PDF,
                new FileSize(RulebookIndexingPolicy.AUTOMATIC_SPLIT_THRESHOLD_BYTES));
        rulebook.recordExtraction(ExtractionResult.success("indexed rule paragraph ".repeat(50_000)));
        return rulebook;
    }

    private static Properties telemetryPolicy() throws Exception {
        Path path = Path.of(System.getProperty("dnd.observability.policy"));
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static final class ImmediateStreamingModel implements ChatModel {
        @Override public ChatResponse call(Prompt prompt) { throw new UnsupportedOperationException(); }
        @Override public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("grounded answer")))));
        }
    }

    private static final class MemoryIndexRepository implements RulebookIndexRepository {
        private final Map<IndexKey, RulebookIndex> indexes = new HashMap<>();
        @Override public RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> factory) {
            return indexes.computeIfAbsent(key, ignored -> factory.get());
        }
        @Override public void save(RulebookIndex index) { indexes.put(index.key(), index); }
        @Override public void saveBatch(RulebookIndex index, List<EmbeddedRulebookChunk> chunks) { indexes.put(index.key(), index); }
        @Override public void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks) { indexes.put(index.key(), index); }
    }

    private static final class TelemetryProbe {
        private final Properties policy;
        private final List<String> metrics = new ArrayList<>();
        private final List<String> traces = new ArrayList<>();
        private final List<String> logs = new ArrayList<>();

        private TelemetryProbe(Properties policy) { this.policy = policy; }
        void firstByte(String operationId, long nanos) {
            metrics.add(policy.getProperty("metric.name") + " operation.id=" + operationId
                    + " status=success value=" + (nanos / 1_000_000_000.0));
            traces.add("operation.id=" + operationId + " status=success duration.ms=" + (nanos / 1_000_000));
        }
        void indexing(String operationId, long nanos, String status) {
            metrics.add("dnd.rulebook.indexing.seconds operation.id=" + operationId
                    + " status=" + status + " value=" + (nanos / 1_000_000_000.0));
        }
        void log(String value) { logs.add(value); }
        String export() { return String.join("\n", metrics) + "\n" + String.join("\n", traces)
                + "\n" + String.join("\n", logs); }
    }
}
