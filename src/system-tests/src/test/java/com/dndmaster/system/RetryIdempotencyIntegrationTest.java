package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatOperation;
import com.dndmaster.adventure.application.combat.CombatOperationRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException;
import com.dndmaster.aigamemaster.infrastructure.ai.ProviderRateLimitException;
import com.dndmaster.aigamemaster.infrastructure.ai.SafeAiAuditLogger;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.ruleknowledge.application.indexing.IndexingCommand;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexRepository;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.indexing.StructureDetectionPort;
import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationApplicationService;
import com.dndmaster.ruleknowledge.application.registration.RulebookFileStorage;
import com.dndmaster.ruleknowledge.application.registration.RulebookUploadConflictException;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookFile;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndexingPolicy;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RetryIdempotencyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void duplicateIndexingAiFileDiceAndMapRequestsMutateStateOnce() {
        assertIndexingOnce();
        assertAiOnceAfterTransientRetry();
        assertFileOnce();
        assertDiceAndMapOnce();
    }

    @Test
    void databaseOperationKeyIsUniquePerOperationType() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS idempotent_operation");
            statement.execute("CREATE TABLE idempotent_operation (operation_type TEXT, operation_key TEXT, "
                    + "PRIMARY KEY (operation_type, operation_key))");
            statement.execute("INSERT INTO idempotent_operation VALUES ('FILE', 'same-key')");
            assertThrows(java.sql.SQLException.class,
                    () -> statement.execute("INSERT INTO idempotent_operation VALUES ('FILE', 'same-key')"));
        }
    }

    private static void assertIndexingOnce() {
        InMemoryIndexRepository repository = new InMemoryIndexRepository();
        AtomicInteger embeddings = new AtomicInteger();
        RulebookIndexingApplicationService service = new RulebookIndexingApplicationService(
                repository, (chunks, model, dimension) -> {
                    embeddings.incrementAndGet();
                    return chunks.stream()
                            .map(chunk -> new com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding(
                                    chunk.chunkId(), new float[] {1, 0, 0}))
                            .toList();
                },
                text -> StructureDetectionPort.DetectedStructure.none(), 50);
        Rulebook rulebook = Rulebook.acceptUpload(
                RulebookId.generate(), new OwnerPlayerId(UUID.randomUUID()), RulebookFormat.PDF, new FileSize(10));
        rulebook.recordExtraction(ExtractionResult.success("initiative order"));
        IndexingCommand command = new IndexingCommand(
                rulebook, new IndexKey(rulebook.id(), "hash", "embedding", "v1"), 3);
        RulebookIndex first = service.indexContent(command);
        assertSame(first, service.indexContent(command));
        assertEquals(1, embeddings.get());
    }

    private static void assertAiOnceAfterTransientRetry() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) {
                if (calls.incrementAndGet() == 1) throw new ProviderRateLimitException();
                return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
            }
        };
        SpringAiChatAdapter adapter = new SpringAiChatAdapter(model, 3, new SafeAiAuditLogger(ignored -> {}));
        assertEquals("ok", adapter.complete("ai-op", "grounded", value -> value));
        assertEquals("ok", adapter.complete("ai-op", "grounded", value -> value));
        assertEquals(2, calls.get(), "one transient retry followed by a cached duplicate");

        AtomicInteger malformedCalls = new AtomicInteger();
        ChatModel malformed = prompt -> {
            malformedCalls.incrementAndGet();
            throw new ProviderMalformedResponseException("bad response");
        };
        SpringAiChatAdapter noRetry = new SpringAiChatAdapter(malformed, 3, new SafeAiAuditLogger(ignored -> {}));
        assertThrows(ProviderMalformedResponseException.class,
                () -> noRetry.complete("bad-op", "grounded", value -> value));
        assertEquals(1, malformedCalls.get(), "non-transient failures must not be retried");
    }

    private static void assertFileOnce() {
        AtomicInteger stores = new AtomicInteger();
        RulebookFileStorage storage = new RulebookFileStorage() {
            @Override public StoredRulebookFile store(RulebookId rulebookId, byte[] content) {
                    stores.incrementAndGet();
                    return new StoredRulebookFile(rulebookId.value().toString());
            }
            @Override public byte[] read(StoredRulebookFile storedFile) { return new byte[] {1, 2, 3}; }
        };
        RulebookRegistrationApplicationService service = new RulebookRegistrationApplicationService(
                storage,
                (format, content) -> ExtractionResult.success("content"));
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        var first = service.uploadRulebook("file-op", owner, RulebookFormat.PDF, new byte[] {1, 2, 3});
        assertSame(first, service.uploadRulebook("file-op", owner, RulebookFormat.PDF, new byte[] {1, 2, 3}));
        assertEquals(1, stores.get());
        assertThrows(RulebookUploadConflictException.class,
                () -> service.uploadRulebook("file-op", owner, RulebookFormat.PDF, new byte[] {9}));
    }

    private static void assertDiceAndMapOnce() {
        InMemoryCombatRepository repository = new InMemoryCombatRepository();
        AtomicInteger dice = new AtomicInteger();
        AtomicInteger map = new AtomicInteger();
        AtomicInteger ai = new AtomicInteger();
        AiCombatPort aiPort = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand command) { ai.incrementAndGet(); }
            @Override public String adjudicate(CombatActionCommand command, int diceTotal) {
                ai.incrementAndGet();
                return "hit";
            }
        };
        AdventureCombatApplicationService service = new AdventureCombatApplicationService(
                repository, command -> {}, command -> { dice.incrementAndGet(); return 17; },
                command -> map.incrementAndGet(), aiPort);
        CombatActionCommand command = new CombatActionCommand(
                UUID.randomUUID(), AdventureId.generate(), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), CombatActorRole.PLAYER, "attack", "A1>B1");
        service.resolveCombatAction(command);
        service.resolveCombatAction(command);
        assertEquals(1, dice.get());
        assertEquals(1, map.get());
        assertEquals(2, ai.get());
    }

    private static final class InMemoryIndexRepository implements RulebookIndexRepository {
        private final Map<IndexKey, RulebookIndex> indexes = new HashMap<>();
        @Override public RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> factory) {
            return indexes.computeIfAbsent(key, ignored -> factory.get());
        }
        @Override public void save(RulebookIndex index) { indexes.put(index.key(), index); }
        @Override public void saveBatch(RulebookIndex index, List<EmbeddedRulebookChunk> chunks, int totalChunks, int completedChunks) { indexes.put(index.key(), index); }
        @Override public java.util.Set<Integer> completedSequences(RulebookIndex index) { return java.util.Set.of(); }
        @Override public void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks) { indexes.put(index.key(), index); }
    }

    private static final class InMemoryCombatRepository implements CombatOperationRepository {
        private final Map<UUID, CombatOperation> operations = new HashMap<>();
        @Override public Optional<CombatOperation> findById(UUID operationId) {
            return Optional.ofNullable(operations.get(operationId));
        }
        @Override public void save(CombatOperation operation) { operations.put(operation.id(), operation); }
    }
}
