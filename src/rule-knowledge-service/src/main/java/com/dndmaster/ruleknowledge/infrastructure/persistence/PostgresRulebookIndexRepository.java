package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexRepository;
import com.dndmaster.ruleknowledge.domain.index.*;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;

import java.sql.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class PostgresRulebookIndexRepository implements RulebookIndexRepository {
    private static final String INSERT_INDEX = """
            INSERT INTO rulebook_vector_index
                (index_id, rulebook_id, owner_player_id, embedding_model, dimension, index_version, status, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (rulebook_id, owner_player_id, embedding_model, index_version)
            DO UPDATE SET status = EXCLUDED.status, version = EXCLUDED.version
            """;
    private static final String INSERT_CHUNK = """
            INSERT INTO rulebook_vector_chunk
                (chunk_id, index_id, rulebook_id, owner_player_id, sequence, locator, content, embedding, chapter, section)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?)
            ON CONFLICT (index_id, chunk_id) DO UPDATE SET
                sequence = EXCLUDED.sequence,
                locator = EXCLUDED.locator,
                content = EXCLUDED.content,
                embedding = EXCLUDED.embedding,
                chapter = EXCLUDED.chapter,
                section = EXCLUDED.section
            """;
    private static final String UPDATE_INDEX_READY = """
            UPDATE rulebook_vector_index SET status = 'READY', version = version + 1
            WHERE index_id = ?
            """;
    private static final String UPDATE_PROGRESS = """
            UPDATE rulebook_vector_index
               SET total_chunks = ?, completed_chunks = ?, next_chunk_sequence = ?, last_progress_at = now()
             WHERE index_id = ?
            """;

    private final DataSource dataSource;

    public PostgresRulebookIndexRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> newIndex) {
        RulebookIndex created = newIndex.get();
        save(created);
        return created;
    }

    @Override
    public void save(RulebookIndex index) {
        Objects.requireNonNull(index, "index must not be null");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(INSERT_INDEX)) {
            ps.setObject(1, index.id().value(), Types.OTHER);
            ps.setObject(2, index.key().rulebookId().value(), Types.OTHER);
            ps.setObject(3, index.ownerPlayerId().value(), Types.OTHER);
            ps.setString(4, index.key().embeddingModel());
            ps.setInt(5, index.dimension());
            ps.setString(6, index.key().indexVersion());
            ps.setString(7, index.status().name());
            ps.setLong(8, index.version());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to save index", e);
        }
    }

    @Override
    public void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks) {
        Objects.requireNonNull(index, "index must not be null");
        List<EmbeddedRulebookChunk> immutableChunks = List.copyOf(
                Objects.requireNonNull(chunks, "chunks must not be null"));
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertChunks(connection, index, immutableChunks);
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_INDEX_READY)) {
                    ps.setObject(1, index.id().value(), Types.OTHER);
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    exception.addSuppressed(rollbackEx);
                }
                throw new RuntimeException("failed to atomically save complete index", exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("could not access database for index completion", e);
        }
    }

    @Override
    public void saveBatch(
            RulebookIndex index, List<EmbeddedRulebookChunk> chunks, int totalChunks, int completedChunks) {
        Objects.requireNonNull(index, "index must not be null");
        List<EmbeddedRulebookChunk> immutableChunks = List.copyOf(
                Objects.requireNonNull(chunks, "chunks must not be null"));
        if (totalChunks <= 0 || completedChunks < 0 || completedChunks > totalChunks) {
            throw new IllegalArgumentException("invalid index progress");
        }
        if (immutableChunks.isEmpty()) return;
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertChunks(connection, index, immutableChunks);
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_PROGRESS)) {
                    ps.setInt(1, totalChunks);
                    ps.setInt(2, completedChunks);
                    ps.setInt(3, completedChunks);
                    ps.setObject(4, index.id().value(), Types.OTHER);
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    exception.addSuppressed(rollbackEx);
                }
                throw new RuntimeException("failed to save embedding batch", exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("could not access database for embedding batch", e);
        }
    }

    private static void insertChunks(
            Connection connection, RulebookIndex index, List<EmbeddedRulebookChunk> chunks) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_CHUNK)) {
            for (EmbeddedRulebookChunk embedded : chunks) {
                ps.setObject(1, embedded.chunk().chunkId().value());
                ps.setObject(2, index.id().value());
                ps.setObject(3, embedded.chunk().rulebookId().value());
                ps.setObject(4, index.ownerPlayerId().value());
                ps.setInt(5, embedded.chunk().sequence());
                ps.setString(6, embedded.locator());
                ps.setString(7, embedded.chunk().content());
                ps.setString(8, vectorLiteral(embedded.embedding()));
                ps.setString(9, embedded.chunk().chapter());
                ps.setString(10, embedded.chunk().section());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static String vectorLiteral(float[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            result.append(Float.toString(values[i]));
        }
        return result.append(']').toString();
    }
}
