package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.application.indexing.IndexingCommand;
import com.dndmaster.ruleknowledge.application.indexing.IndexingFailedException;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexRepository;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.indexing.StructureDetectionPort;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.IndexStatus;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndexingPolicy;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class RulebookIndexingApplicationServiceTest {
    private static final int DIMENSION = 3;

    @Test
    void automaticallySplitsFilesLargerThanOneHundredMegabytesBeforeCompletingIndex() {
        InMemoryIndexRepository repository = new InMemoryIndexRepository();
        RecordingEmbeddingPort embeddings = new RecordingEmbeddingPort(0);
        RulebookIndexingApplicationService service = service(repository, embeddings, 1_000);
        IndexingCommand command = command(oversizedRulebook("abcdefghij"));

        RulebookIndex result = service.indexContent(command);

        assertEquals(IndexStatus.READY, result.status());
        assertEquals(2, result.chunks().size());
        assertEquals(List.of(0, 1), result.chunks().stream().map(RulebookChunk::sequence).toList());
        assertEquals(List.of(IndexStatus.EMBEDDING, IndexStatus.READY), repository.savedStatuses);
        assertEquals(1, embeddings.calls);
    }

    @Test
    void embedsAndPersistsChunksInBoundedBatchesBeforeFinalReadyState() {
        InMemoryIndexRepository repository = new InMemoryIndexRepository();
        RecordingEmbeddingPort embeddings = new RecordingEmbeddingPort(0);
        RulebookIndexingApplicationService service = service(repository, embeddings, 4, 2);
        IndexingCommand command = command(oversizedRulebook("abcdefghijkl"));

        RulebookIndex result = service.indexContent(command);

        assertEquals(IndexStatus.READY, result.status());
        assertEquals(List.of(2, 1), embeddings.batchSizes);
        assertEquals(List.of(2, 1), repository.savedBatchSizes);
        assertEquals(List.of(IndexStatus.EMBEDDING, IndexStatus.READY), repository.savedStatuses);
    }

    @Test
    void embeddingFailureStaysFailedUntilExplicitRetryAndCannotPublishPartialChunks() {
        InMemoryIndexRepository repository = new InMemoryIndexRepository();
        RecordingEmbeddingPort embeddings = new RecordingEmbeddingPort(1);
        RulebookIndexingApplicationService service = service(repository, embeddings, 4);
        IndexingCommand command = command(oversizedRulebook("abcdefghijkl"));

        assertThrows(IndexingFailedException.class, () -> service.indexContent(command));

        RulebookIndex failed = repository.get(command.key());
        assertEquals(IndexStatus.FAILED, failed.status());
        assertEquals(1, failed.attempts());
        assertEquals(List.of(), failed.chunks());
        assertEquals(List.of(IndexStatus.EMBEDDING, IndexStatus.FAILED), repository.savedStatuses);
        assertThrows(IllegalStateException.class, () -> service.indexContent(command));
        assertEquals(1, embeddings.calls);

        RulebookIndex completed = service.retryIndexing(command);

        assertEquals(IndexStatus.READY, completed.status());
        assertEquals(2, completed.attempts());
        assertEquals(3, completed.chunks().size());
        assertEquals(List.of(
                        IndexStatus.EMBEDDING,
                        IndexStatus.FAILED,
                        IndexStatus.EMBEDDING,
                        IndexStatus.READY),
                repository.savedStatuses);
        assertEquals(2, embeddings.calls);
    }

    @Test
    void duplicateCompletedRequestReturnsSameIndexWithoutReembeddingOrChangingChunks() {
        InMemoryIndexRepository repository = new InMemoryIndexRepository();
        RecordingEmbeddingPort embeddings = new RecordingEmbeddingPort(0);
        RulebookIndexingApplicationService service = service(repository, embeddings, 4);
        IndexingCommand command = command(oversizedRulebook("abcdefghijkl"));
        RulebookIndex first = service.indexContent(command);
        List<RulebookChunk> completedChunks = first.chunks();

        RulebookIndex duplicate = service.indexContent(command);

        assertSame(first, duplicate);
        assertSame(completedChunks, duplicate.chunks());
        assertEquals(IndexStatus.READY, duplicate.status());
        assertEquals(1, duplicate.attempts());
        assertEquals(1, embeddings.calls);
        assertEquals(List.of(IndexStatus.EMBEDDING, IndexStatus.READY), repository.savedStatuses);
    }

    private static RulebookIndexingApplicationService service(
            RulebookIndexRepository repository, EmbeddingPort embeddings, int maximumChunkCharacters) {
        return new RulebookIndexingApplicationService(
                repository, embeddings, text -> StructureDetectionPort.DetectedStructure.none(), maximumChunkCharacters);
    }

    private static RulebookIndexingApplicationService service(
            RulebookIndexRepository repository,
            EmbeddingPort embeddings,
            int maximumChunkCharacters,
            int embeddingBatchSize) {
        return new RulebookIndexingApplicationService(
                repository,
                embeddings,
                text -> StructureDetectionPort.DetectedStructure.none(),
                maximumChunkCharacters,
                embeddingBatchSize);
    }

    private static IndexingCommand command(Rulebook rulebook) {
        IndexKey key = new IndexKey(rulebook.id(), "content-hash", "mock-embedding", "v1");
        return new IndexingCommand(rulebook, key, DIMENSION);
    }

    private static Rulebook oversizedRulebook(String content) {
        Rulebook rulebook = Rulebook.acceptUpload(
                RulebookId.generate(),
                new OwnerPlayerId(UUID.randomUUID()),
                RulebookFormat.PDF,
                new FileSize(RulebookIndexingPolicy.AUTOMATIC_SPLIT_THRESHOLD_BYTES + 1));
        rulebook.recordExtraction(ExtractionResult.success(content));
        return rulebook;
    }

    private static final class RecordingEmbeddingPort implements EmbeddingPort {
        private int failuresRemaining;
        private int calls;
        private final List<Integer> batchSizes = new ArrayList<>();

        private RecordingEmbeddingPort(int failuresRemaining) {
            this.failuresRemaining = failuresRemaining;
        }

        @Override
        public List<ChunkEmbedding> embed(List<RulebookChunk> chunks, String embeddingModel, int expectedDimension) {
            calls++;
            batchSizes.add(chunks.size());
            assertEquals("mock-embedding", embeddingModel);
            assertEquals(DIMENSION, expectedDimension);
            if (failuresRemaining-- > 0) {
                throw new IllegalStateException("mock embedding failure");
            }
            return chunks.stream()
                    .map(c -> new ChunkEmbedding(c.chunkId(), new float[]{1f, 0f, 0f}))
                    .toList();
        }
    }

    private static final class InMemoryIndexRepository implements RulebookIndexRepository {
        private final Map<IndexKey, RulebookIndex> indexes = new HashMap<>();
        private final List<IndexStatus> savedStatuses = new ArrayList<>();
        private final List<Integer> savedBatchSizes = new ArrayList<>();

        @Override
        public RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> newIndex) {
            return indexes.computeIfAbsent(key, ignored -> newIndex.get());
        }

        @Override
        public void save(RulebookIndex index) {
            indexes.put(index.key(), index);
            savedStatuses.add(index.status());
        }

        @Override
        public void saveComplete(RulebookIndex index, List<com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk> chunks) {
            indexes.put(index.key(), index);
            savedStatuses.add(index.status());
        }

        @Override
        public void saveBatch(
                RulebookIndex index,
                List<com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk> chunks) {
            indexes.put(index.key(), index);
            savedBatchSizes.add(chunks.size());
        }

        private RulebookIndex get(IndexKey key) {
            return indexes.get(key);
        }
    }
}
