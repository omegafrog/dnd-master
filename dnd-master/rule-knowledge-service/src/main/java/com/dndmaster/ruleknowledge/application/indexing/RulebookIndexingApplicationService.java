package com.dndmaster.ruleknowledge.application.indexing;

import com.dndmaster.ruleknowledge.domain.index.IndexStatus;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndexingPolicy;
import java.util.Objects;

public final class RulebookIndexingApplicationService {
    private final RulebookIndexRepository repository;
    private final EmbeddingPort embeddingPort;
    private final RulebookIndexingPolicy indexingPolicy;

    public RulebookIndexingApplicationService(
            RulebookIndexRepository repository,
            EmbeddingPort embeddingPort,
            RulebookIndexingPolicy indexingPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort must not be null");
        this.indexingPolicy = Objects.requireNonNull(indexingPolicy, "indexingPolicy must not be null");
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
                () -> indexingPolicy.createIndex(command.rulebook(), command.key(), command.dimension()));
    }

    private RulebookIndex executeAttempt(IndexingCommand command, RulebookIndex index) {
        index.beginAttempt();
        repository.save(index);
        var chunks = indexingPolicy.createChunks(command.rulebook());
        try {
            embeddingPort.embed(chunks, command.key().embeddingModel(), command.dimension());
        } catch (RuntimeException exception) {
            index.fail("embedding call failed");
            repository.save(index);
            throw new IndexingFailedException(exception);
        }
        index.complete(chunks);
        repository.save(index);
        return index;
    }
}
