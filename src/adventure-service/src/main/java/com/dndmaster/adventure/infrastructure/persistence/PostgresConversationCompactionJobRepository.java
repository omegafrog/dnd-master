package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.ConversationCompactionJob;
import com.dndmaster.adventure.application.runtime.ConversationCompactionJobRepository;
import com.dndmaster.adventure.application.runtime.ConversationSummary;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** JDBC implementation keeps job lease and conditional publish inside database transactions. */
public final class PostgresConversationCompactionJobRepository implements ConversationCompactionJobRepository {
    private final DataSource dataSource;
    public PostgresConversationCompactionJobRepository(DataSource dataSource) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource);
    }
    @Override public ConversationCompactionJob register(ConversationCompactionJob job) {
        try (Connection connection = dataSource.getConnection()) {
            register(connection, job);
            return byKey(connection, job.idempotencyKey()).orElseThrow();
        } catch (SQLException error) { throw new AdventurePersistenceException("could not register conversation compaction job", error); }
    }
    /** Uses the caller's local Adventure transaction. */
    public static void register(Connection connection, ConversationCompactionJob job) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO adventure_conversation_compaction_job(job_id, adventure_id, source_start, source_end, expected_adventure_version, idempotency_key, status, available_at, lease_until, attempts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING""")) {
            bind(statement, job); statement.executeUpdate();
        }
    }
    @Override public Optional<ConversationCompactionJob> lease(AdventureId adventureId, Instant now, Instant until) {
        try (Connection connection = dataSource.getConnection()) {
            boolean managed = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
            boolean autoCommit = connection.getAutoCommit(); if (!managed) connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT * FROM adventure_conversation_compaction_job
                    WHERE adventure_id=? AND status IN ('READY', 'RETRY_WAIT') AND available_at <= ?
                      AND NOT EXISTS (SELECT 1 FROM adventure_conversation_compaction_job held WHERE held.adventure_id=? AND held.status='LEASED' AND held.lease_until > ?)
                    ORDER BY available_at, job_id FOR UPDATE SKIP LOCKED LIMIT 1""")) {
                select.setObject(1, adventureId.value()); select.setTimestamp(2, Timestamp.from(now)); select.setObject(3, adventureId.value()); select.setTimestamp(4, Timestamp.from(now));
                try (ResultSet rows = select.executeQuery()) { if (!rows.next()) { if (!managed) connection.commit(); return Optional.empty(); }
                    ConversationCompactionJob leased = read(rows).lease(until); update(connection, leased); if (!managed) connection.commit(); return Optional.of(leased); }
            } catch (SQLException | RuntimeException error) { if (!managed) connection.rollback(); throw error; } finally { if (!managed) connection.setAutoCommit(autoCommit); }
        } catch (SQLException error) { throw new AdventurePersistenceException("could not lease conversation compaction job", error); }
    }
    @Override public void save(ConversationCompactionJob job) { try (Connection connection = dataSource.getConnection()) { update(connection, job); } catch (SQLException error) { throw new AdventurePersistenceException("could not save conversation compaction job", error); } }
    @Override public boolean publish(ConversationCompactionJob job, ConversationSummary summary, long actualAdventureVersion) {
        if (job.expectedAdventureVersion() != actualAdventureVersion) return false;
        try (Connection connection = dataSource.getConnection()) {
            boolean managed = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(); boolean autoCommit = connection.getAutoCommit(); if (!managed) connection.setAutoCommit(false);
            try (PreparedStatement adventure = connection.prepareStatement("SELECT version FROM adventure WHERE adventure_id=? FOR UPDATE")) {
                adventure.setObject(1, job.adventureId().value()); try (ResultSet rows = adventure.executeQuery()) { if (!rows.next() || rows.getLong(1) != actualAdventureVersion) { if (!managed) connection.commit(); return false; } }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO adventure_conversation_summary(adventure_id, summary_version, source_start, source_end, source_adventure_version, summary_text) VALUES (?, ?, ?, ?, ?, ?)")) {
                    insert.setObject(1, summary.adventureId().value()); insert.setLong(2, summary.version()); insert.setLong(3, summary.sourceStart()); insert.setLong(4, summary.sourceEnd()); insert.setLong(5, summary.sourceAdventureVersion()); insert.setString(6, summary.text()); insert.executeUpdate();
                }
                update(connection, job.done()); if (!managed) connection.commit(); return true;
            } catch (SQLException | RuntimeException error) { if (!managed) connection.rollback(); throw error; } finally { if (!managed) connection.setAutoCommit(autoCommit); }
        } catch (SQLException error) { throw new AdventurePersistenceException("could not publish conversation summary", error); }
    }
    @Override public List<ConversationSummary> summaries(AdventureId adventureId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT summary_version, source_start, source_end, source_adventure_version, summary_text FROM adventure_conversation_summary WHERE adventure_id=? ORDER BY summary_version")) {
            statement.setObject(1, adventureId.value()); List<ConversationSummary> result = new ArrayList<>(); try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(new ConversationSummary(adventureId, rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4), rows.getString(5))); } return List.copyOf(result);
        } catch (SQLException error) { throw new AdventurePersistenceException("could not load conversation summaries", error); }
    }
    @Override public List<ConversationCompactionJob> ready(Instant now) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM adventure_conversation_compaction_job WHERE status IN ('READY', 'RETRY_WAIT') AND available_at <= ? ORDER BY available_at, job_id LIMIT 20""")) {
            statement.setTimestamp(1, Timestamp.from(now)); List<ConversationCompactionJob> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(read(rows)); } return List.copyOf(result);
        } catch (SQLException error) { throw new AdventurePersistenceException("could not list ready conversation compaction jobs", error); }
    }
    private Optional<ConversationCompactionJob> byKey(Connection connection, String key) throws SQLException { try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM adventure_conversation_compaction_job WHERE idempotency_key=?")) { statement.setString(1, key); try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(read(rows)) : Optional.empty(); } } }
    private static void bind(PreparedStatement statement, ConversationCompactionJob job) throws SQLException { statement.setObject(1, job.id()); statement.setObject(2, job.adventureId().value()); statement.setLong(3, job.sourceStart()); statement.setLong(4, job.sourceEnd()); statement.setLong(5, job.expectedAdventureVersion()); statement.setString(6, job.idempotencyKey()); statement.setString(7, job.status().name()); statement.setTimestamp(8, Timestamp.from(job.availableAt())); if (job.leaseUntil()==null) statement.setTimestamp(9,null); else statement.setTimestamp(9,Timestamp.from(job.leaseUntil())); statement.setInt(10, job.attempts()); }
    private static void update(Connection connection, ConversationCompactionJob job) throws SQLException { try (PreparedStatement statement = connection.prepareStatement("UPDATE adventure_conversation_compaction_job SET status=?, available_at=?, lease_until=?, attempts=? WHERE job_id=?")) { statement.setString(1,job.status().name()); statement.setTimestamp(2,Timestamp.from(job.availableAt())); if(job.leaseUntil()==null) statement.setTimestamp(3,null); else statement.setTimestamp(3,Timestamp.from(job.leaseUntil())); statement.setInt(4,job.attempts()); statement.setObject(5,job.id()); if(statement.executeUpdate()!=1) throw new SQLException("compaction job was not found"); } }
    private static ConversationCompactionJob read(ResultSet row) throws SQLException { Timestamp lease=row.getTimestamp("lease_until"); return new ConversationCompactionJob(row.getObject("job_id", java.util.UUID.class),new AdventureId(row.getObject("adventure_id",java.util.UUID.class)),row.getLong("source_start"),row.getLong("source_end"),row.getLong("expected_adventure_version"),row.getString("idempotency_key"),ConversationCompactionJob.Status.valueOf(row.getString("status")),row.getTimestamp("available_at").toInstant(),lease==null?null:lease.toInstant(),row.getInt("attempts")); }
}
