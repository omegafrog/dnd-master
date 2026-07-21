package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.HeadingPattern;
import com.dndmaster.ruleknowledge.domain.index.IndexStatus;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndexingPolicy;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class RulebookIndexingApplicationService {
    private static final Logger log = Logger.getLogger(RulebookIndexingApplicationService.class.getName());

    private final RulebookIndexRepository repository;
    private final EmbeddingPort embeddingPort;
    private final StructureDetectionPort structureDetectionPort;
    private final int defaultChunkSize;

    public RulebookIndexingApplicationService(
            RulebookIndexRepository repository,
            EmbeddingPort embeddingPort,
            StructureDetectionPort structureDetectionPort,
            int defaultChunkSize) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort must not be null");
        this.structureDetectionPort = Objects.requireNonNull(structureDetectionPort, "structureDetectionPort must not be null");
        if (defaultChunkSize <= 0) throw new IllegalArgumentException("defaultChunkSize must be positive");
        this.defaultChunkSize = defaultChunkSize;
    }

    public RulebookIndex indexContent(IndexingCommand command) {
        RulebookIndex index = load(command);
        if (index.status() == IndexStatus.READY) return index;
        if (index.status() == IndexStatus.FAILED) {
            throw new IllegalStateException("failed index requires explicit retry");
        }
        if (index.status() == IndexStatus.EMBEDDING) {
            throw new IllegalStateException("indexing operation is already in progress");
        }
        return executeAttempt(command, index);
    }

    public RulebookIndex retryIndexing(IndexingCommand command) {
        RulebookIndex index = load(command);
        if (index.status() == IndexStatus.READY) return index;
        if (index.status() != IndexStatus.FAILED) {
            throw new IllegalStateException("only failed index can be retried");
        }
        return executeAttempt(command, index);
    }

    private RulebookIndex load(IndexingCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return repository.loadOrCreate(
                command.key(),
                () -> new RulebookIndexingPolicy(defaultChunkSize)
                        .createIndex(command.rulebook(), command.key(), command.dimension()));
    }

    private RulebookIndex executeAttempt(IndexingCommand command, RulebookIndex index) {
        index.beginAttempt();
        repository.save(index);

        RulebookIndexingPolicy policy = buildPolicy(command.rulebook());
        var chunks = policy.createChunks(command.rulebook());
        try {
            List<ChunkEmbedding> embeddings = embeddingPort.embed(chunks, command.key().embeddingModel(), command.dimension());
            List<EmbeddedRulebookChunk> embedded = new ArrayList<>(embeddings.size());
            for (ChunkEmbedding ce : embeddings) {
                RulebookChunk chunk = chunks.stream()
                        .filter(c -> c.chunkId().equals(ce.chunkId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("embedding result references unknown chunk"));
                embedded.add(new EmbeddedRulebookChunk(chunk, formatLocator(chunk), ce.vector()));
            }
            index.complete(chunks);
            repository.saveComplete(index, embedded);
        } catch (RuntimeException exception) {
            index.fail("embedding call failed");
            repository.save(index);
            throw new IndexingFailedException(exception);
        }
        return index;
    }

    private RulebookIndexingPolicy buildPolicy(Rulebook rulebook) {
        String content = rulebook.extractionResult().orElseThrow().content().orElseThrow();
        try {
            StructureDetectionPort.DetectedStructure detected = structureDetectionPort.detect(content);
            if (detected.hasPatterns()) {
                log.info("detected structure: " + detected.description());
                List<HeadingPattern> headingPatterns = detected.patterns().stream()
                        .map(p -> new HeadingPattern(
                                Pattern.compile(p.regex(), Pattern.MULTILINE),
                                p.groupName(),
                                p.description()))
                        .toList();
                return new RulebookIndexingPolicy(defaultChunkSize, headingPatterns);
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "structure detection failed, using defaults", e);
        }
        return new RulebookIndexingPolicy(defaultChunkSize);
    }

    private static String formatLocator(RulebookChunk chunk) {
        return "offset " + chunk.range().startInclusive() + "-" + chunk.range().endExclusive();
    }
}
