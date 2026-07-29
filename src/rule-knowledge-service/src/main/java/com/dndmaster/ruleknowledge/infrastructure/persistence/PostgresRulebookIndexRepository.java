package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexRepository;
import com.dndmaster.ruleknowledge.domain.index.*;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;

import java.sql.*;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.time.Instant;
import java.util.Optional;
import com.dndmaster.ruleknowledge.application.indexing.IndexProgress;
import com.dndmaster.ruleknowledge.application.indexing.IndexLease;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class PostgresRulebookIndexRepository implements RulebookIndexRepository {
    private static final String INSERT_INDEX = """
            INSERT INTO rulebook_vector_index
                (index_id, rulebook_id, owner_player_id, embedding_model, dimension, index_version, status, version, attempts, failure_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (rulebook_id, owner_player_id, embedding_model, index_version)
            DO UPDATE SET status = EXCLUDED.status, version = EXCLUDED.version,
                          attempts = EXCLUDED.attempts, failure_reason = EXCLUDED.failure_reason
            """;
    private static final String SELECT_INDEX = """
            SELECT index_id, owner_player_id, dimension, status, version, attempts, failure_reason
              FROM rulebook_vector_index
             WHERE rulebook_id = ? AND owner_player_id = ? AND embedding_model = ? AND index_version = ?
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
    private static final String UPDATE_INDEX_LEASED = """
            UPDATE rulebook_vector_index
               SET status = ?, version = ?, attempts = ?, failure_reason = ?
             WHERE index_id = ? AND version = ? AND lease_owner = ? AND lease_token = ?
               AND lease_until > now()
            """;
    private static final String PUBLISH_INDEX_LEASED = """
            UPDATE rulebook_vector_index SET status = 'READY', version = version + 1
             WHERE index_id = ? AND lease_owner = ? AND lease_token = ?
               AND lease_until > now()
            """;
    private static final String UPDATE_PROGRESS = """
            UPDATE rulebook_vector_index
               SET total_chunks = ?, completed_chunks = ?, next_chunk_sequence = ?, last_progress_at = now()
             WHERE index_id = ?
            """;
    private static final String UPDATE_PROGRESS_LEASED = """
            UPDATE rulebook_vector_index
               SET total_chunks = ?, completed_chunks = ?, next_chunk_sequence = ?, last_progress_at = now()
            WHERE index_id = ? AND lease_owner = ? AND lease_token = ? AND lease_until > now()
            """;
    private static final String SELECT_COMPLETED_SEQUENCES = """
            SELECT sequence
              FROM rulebook_vector_chunk
             WHERE index_id = ?
             ORDER BY sequence
            """;
    private static final String SELECT_PROGRESS = """
            SELECT status, total_chunks, completed_chunks, failure_reason, lease_owner, lease_until
              FROM rulebook_vector_index
             WHERE rulebook_id = ?
             ORDER BY version DESC
             LIMIT 1
            """;
    private static final String SELECT_PROGRESS_FOR_VERSION = """
            SELECT status, total_chunks, completed_chunks, failure_reason, lease_owner, lease_until
              FROM rulebook_vector_index
             WHERE rulebook_id = ? AND index_version = ?
             ORDER BY version DESC LIMIT 1
            """;
    private static final String CLAIM_LEASE = """
            UPDATE rulebook_vector_index
               SET lease_owner = ?, lease_token = ?, lease_until = ?
             WHERE rulebook_id = ? AND embedding_model = ? AND index_version = ?
               AND (lease_until IS NULL OR lease_until < ? OR (lease_owner = ? AND lease_token = ?))
            RETURNING index_id
            """;
    private static final String CLAIM_LEASE_BY_ID = """
            UPDATE rulebook_vector_index
               SET lease_owner = ?, lease_token = ?, lease_until = ?
             WHERE index_id = ?
               AND (lease_until IS NULL OR lease_until < ? OR (lease_owner = ? AND lease_token = ?))
            RETURNING index_id
            """;
    private static final String RELEASE_LEASE = """
            UPDATE rulebook_vector_index SET lease_owner = NULL, lease_token = NULL, lease_until = NULL
             WHERE index_id = ? AND lease_owner = ? AND lease_token = ?
            """;
    private static final String RENEW_LEASE = """
            UPDATE rulebook_vector_index SET lease_until = ?
             WHERE index_id = ? AND lease_owner = ? AND lease_token = ?
               AND lease_until >= ?
            """;

    private final DataSource dataSource;

    public PostgresRulebookIndexRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public RulebookIndex loadOrCreate(IndexKey key, Supplier<RulebookIndex> newIndex) {
        RulebookIndex created = newIndex.get();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SELECT_INDEX)) {
            ps.setObject(1, key.rulebookId().value(), Types.OTHER);
            ps.setObject(2, created.ownerPlayerId().value(), Types.OTHER);
            ps.setString(3, key.embeddingModel());
            ps.setString(4, key.indexVersion());
            try (ResultSet row = ps.executeQuery()) {
                if (row.next()) {
                    return RulebookIndex.rehydrate(
                            new IndexId(row.getObject("index_id", java.util.UUID.class)),
                            key,
                            new OwnerPlayerId(row.getObject("owner_player_id", java.util.UUID.class)),
                            row.getInt("dimension"),
                            IndexStatus.valueOf(row.getString("status")),
                            row.getInt("attempts"),
                            row.getLong("version"),
                            row.getString("failure_reason"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to load index", e);
        }
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
            ps.setInt(9, index.attempts());
            ps.setString(10, index.failureReason().orElse(null));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("failed to save index", e);
        }
    }

    @Override
    public void save(RulebookIndex index, IndexLease lease) {
        Objects.requireNonNull(index); Objects.requireNonNull(lease);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(UPDATE_INDEX_LEASED)) {
            ps.setString(1, index.status().name()); ps.setLong(2, index.version());
            ps.setInt(3, index.attempts()); ps.setString(4, index.failureReason().orElse(null));
            ps.setObject(5, index.id().value(), Types.OTHER); ps.setLong(6, index.version() - 1);
            ps.setString(7, lease.owner()); ps.setString(8, lease.token());
            if (ps.executeUpdate() != 1) throw new IllegalStateException("stale index worker");
        } catch (SQLException e) {
            throw new RuntimeException("failed to save leased index", e);
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

    @Override
    public void saveBatch(
            RulebookIndex index, List<EmbeddedRulebookChunk> chunks, int totalChunks, int completedChunks, IndexLease lease) {
        Objects.requireNonNull(lease);
        List<EmbeddedRulebookChunk> immutableChunks = List.copyOf(chunks);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                insertChunks(connection, index, immutableChunks);
                try (PreparedStatement ps = connection.prepareStatement(UPDATE_PROGRESS_LEASED)) {
                    ps.setInt(1, totalChunks); ps.setInt(2, completedChunks); ps.setInt(3, completedChunks);
                    ps.setObject(4, index.id().value(), Types.OTHER);
                    ps.setString(5, lease.owner()); ps.setString(6, lease.token());
                    if (ps.executeUpdate() != 1) throw new IllegalStateException("stale index worker");
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(autoCommit); }
        } catch (SQLException e) { throw new RuntimeException("failed to save leased embedding batch", e); }
    }

    @Override
    public void saveComplete(RulebookIndex index, List<EmbeddedRulebookChunk> chunks, IndexLease lease) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                insertChunks(connection, index, List.copyOf(chunks));
                try (PreparedStatement ps = connection.prepareStatement(PUBLISH_INDEX_LEASED)) {
                    ps.setObject(1, index.id().value(), Types.OTHER);
                    ps.setString(2, lease.owner()); ps.setString(3, lease.token());
                    if (ps.executeUpdate() != 1) throw new IllegalStateException("stale index worker");
                }
                connection.commit();
            } catch (Exception e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new RuntimeException(e);
            } finally { connection.setAutoCommit(autoCommit); }
        } catch (SQLException e) { throw new RuntimeException("failed to publish leased index", e); }
    }

    @Override
    public Set<Integer> completedSequences(RulebookIndex index) {
        Objects.requireNonNull(index, "index must not be null");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SELECT_COMPLETED_SEQUENCES)) {
            ps.setObject(1, index.id().value(), Types.OTHER);
            try (ResultSet rows = ps.executeQuery()) {
                Set<Integer> sequences = new HashSet<>();
                while (rows.next()) sequences.add(rows.getInt("sequence"));
                return Set.copyOf(sequences);
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to load completed embedding sequences", e);
        }
    }

    @Override
    public Optional<IndexProgress> progressFor(RulebookId rulebookId) {
        return progressForQuery(rulebookId, null);
    }

    @Override
    public Optional<IndexProgress> progressFor(RulebookId rulebookId, String indexVersion) {
        return progressForQuery(rulebookId, indexVersion);
    }

    private Optional<IndexProgress> progressForQuery(RulebookId rulebookId, String indexVersion) {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        String sql = indexVersion == null ? SELECT_PROGRESS : SELECT_PROGRESS_FOR_VERSION;
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, rulebookId.value(), Types.OTHER);
            if (indexVersion != null) ps.setString(2, indexVersion);
            try (ResultSet row = ps.executeQuery()) {
                if (!row.next()) return Optional.empty();
                Timestamp leaseUntil = row.getTimestamp("lease_until");
                return Optional.of(new IndexProgress(
                        row.getString("status"),
                        row.getInt("total_chunks"),
                        row.getInt("completed_chunks"),
                        row.getString("failure_reason"),
                        row.getString("lease_owner"),
                        leaseUntil == null ? null : leaseUntil.toInstant()));
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to load index progress", e);
        }
    }

    @Override
    public Optional<IndexLease> claimLease(IndexKey key, String owner, String token, java.time.Instant now, java.time.Duration duration) {
        java.time.Instant until = now.plus(duration);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(CLAIM_LEASE)) {
            ps.setString(1, owner); ps.setString(2, token); ps.setTimestamp(3, Timestamp.from(until));
            ps.setObject(4, key.rulebookId().value(), Types.OTHER); ps.setString(5, key.embeddingModel());
            ps.setString(6, key.indexVersion()); ps.setTimestamp(7, Timestamp.from(now));
            ps.setString(8, owner); ps.setString(9, token);
            try (ResultSet row = ps.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(new IndexLease(
                        new IndexId(row.getObject("index_id", java.util.UUID.class)), owner, token, until));
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to claim index lease", e);
        }
    }

    @Override
    public Optional<IndexLease> claimLease(
            RulebookIndex index, String owner, String token, java.time.Instant now, java.time.Duration duration) {
        java.time.Instant until = now.plus(duration);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(CLAIM_LEASE_BY_ID)) {
            ps.setString(1, owner); ps.setString(2, token); ps.setTimestamp(3, Timestamp.from(until));
            ps.setObject(4, index.id().value(), Types.OTHER); ps.setTimestamp(5, Timestamp.from(now));
            ps.setString(6, owner); ps.setString(7, token);
            try (ResultSet row = ps.executeQuery()) {
                return row.next() ? Optional.of(new IndexLease(index.id(), owner, token, until)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("failed to claim index lease", e);
        }
    }

    @Override
    public boolean releaseLease(IndexLease lease) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(RELEASE_LEASE)) {
            ps.setObject(1, lease.indexId().value(), Types.OTHER);
            ps.setString(2, lease.owner()); ps.setString(3, lease.token());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("failed to release index lease", e);
        }
    }

    @Override
    public boolean renewLease(IndexLease lease, java.time.Instant now, java.time.Duration duration) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(RENEW_LEASE)) {
            ps.setTimestamp(1, Timestamp.from(now.plus(duration)));
            ps.setObject(2, lease.indexId().value(), Types.OTHER);
            ps.setString(3, lease.owner()); ps.setString(4, lease.token());
            ps.setTimestamp(5, Timestamp.from(now));
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("failed to renew index lease", e);
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
