package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.RuleSearchHit;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class PgvectorRuleSearchRepository {
    private static final String INSERT_INDEX = """
            INSERT INTO rulebook_vector_index
                (index_id, rulebook_id, owner_player_id, embedding_model, dimension, index_version, status)
            VALUES (?, ?, ?, ?, ?, ?, 'READY')
            """;
    private static final String INSERT_CHUNK = """
            INSERT INTO rulebook_vector_chunk
                (chunk_id, index_id, rulebook_id, owner_player_id, sequence, locator, content, embedding, chapter, section)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?)
            """;
    private static final String SEARCH = """
            SELECT rulebook_id, chunk_id, locator, content,
                   embedding <=> CAST(? AS vector) AS distance,
                   chapter, section
              FROM rulebook_vector_chunk
             WHERE owner_player_id = ?
               AND rulebook_id = ANY (?)
             ORDER BY embedding <=> CAST(? AS vector), sequence
             LIMIT ?
            """;

    private final DataSource dataSource;

    public PgvectorRuleSearchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public void storeReadyIndex(IndexMetadata metadata, List<EmbeddedRulebookChunk> chunks) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        List<EmbeddedRulebookChunk> immutableChunks = List.copyOf(
                Objects.requireNonNull(chunks, "chunks must not be null"));
        if (immutableChunks.isEmpty()) {
            throw new IllegalArgumentException("ready index requires chunks");
        }
        validateChunks(metadata, immutableChunks);

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertIndex(connection, metadata);
                insertChunks(connection, metadata, immutableChunks);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new RuleVectorPersistenceException("could not store ready rulebook index", exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not access rulebook vector storage", exception);
        }
    }

    public List<RuleSearchHit> search(
            OwnerPlayerId ownerPlayerId,
            Collection<RulebookId> selectedRulebookIds,
            float[] queryEmbedding,
            int limit) {
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        List<RulebookId> selected = List.copyOf(
                Objects.requireNonNull(selectedRulebookIds, "selectedRulebookIds must not be null"));
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("at least one selected rulebook is required");
        }
        if (selected.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("selected rulebooks must not contain null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String vector = vectorLiteral(queryEmbedding);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, vector);
            statement.setObject(2, ownerPlayerId.value(), Types.OTHER);
            UUID[] ids = selected.stream().map(RulebookId::value).toArray(UUID[]::new);
            statement.setArray(3, connection.createArrayOf("uuid", ids));
            statement.setString(4, vector);
            statement.setInt(5, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<RuleSearchHit> hits = new ArrayList<>();
                while (rows.next()) {
                    hits.add(new RuleSearchHit(
                            new KnowledgeDocumentId(rows.getObject("rulebook_id", UUID.class)),
                            DocumentType.RULEBOOK,
                            new ChunkId(rows.getObject("chunk_id", UUID.class)),
                            rows.getString("locator"),
                            rows.getString("content"),
                            rows.getDouble("distance"),
                            rows.getString("chapter"),
                            rows.getString("section")));
                }
                return List.copyOf(hits);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not search rulebook vectors", exception);
        }
    }

    private static void validateChunks(IndexMetadata metadata, List<EmbeddedRulebookChunk> chunks) {
        for (EmbeddedRulebookChunk embedded : chunks) {
            if (!embedded.chunk().rulebookId().equals(metadata.rulebookId())) {
                throw new IllegalArgumentException("all chunks must belong to indexed rulebook");
            }
            if (embedded.embedding().length != metadata.dimension()) {
                throw new IllegalArgumentException("embedding dimension must match index metadata");
            }
        }
    }

    private static void insertIndex(Connection connection, IndexMetadata metadata) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_INDEX)) {
            statement.setObject(1, metadata.indexId().value());
            statement.setObject(2, metadata.rulebookId().value());
            statement.setObject(3, metadata.ownerPlayerId().value());
            statement.setString(4, metadata.embeddingModel());
            statement.setInt(5, metadata.dimension());
            statement.setString(6, metadata.indexVersion());
            statement.executeUpdate();
        }
    }

    private static void insertChunks(
            Connection connection, IndexMetadata metadata, List<EmbeddedRulebookChunk> chunks) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CHUNK)) {
            for (EmbeddedRulebookChunk embedded : chunks) {
                statement.setObject(1, embedded.chunk().chunkId().value());
                statement.setObject(2, metadata.indexId().value());
                statement.setObject(3, metadata.rulebookId().value());
                statement.setObject(4, metadata.ownerPlayerId().value());
                statement.setInt(5, embedded.chunk().sequence());
                statement.setString(6, embedded.locator());
                statement.setString(7, embedded.chunk().content());
                statement.setString(8, vectorLiteral(embedded.embedding()));
                statement.setString(9, embedded.chunk().chapter());
                statement.setString(10, embedded.chunk().section());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static String vectorLiteral(float[] values) {
        Objects.requireNonNull(values, "embedding must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            float value = values[index];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
            if (index > 0) {
                result.append(',');
            }
            result.append(Float.toString(value));
        }
        return result.append(']').toString();
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
