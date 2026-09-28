package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.ConversationCompactionJob;
import com.dndmaster.adventure.application.runtime.ConversationCompactionJobRepository;
import com.dndmaster.adventure.application.runtime.ConversationSummary;
import com.dndmaster.adventure.application.runtime.LongTermAdventureFact;
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
                    WHERE adventure_id=? AND ((status IN ('READY', 'RETRY_WAIT') AND available_at <= ?) OR (status='LEASED' AND lease_until <= ?))
                      AND NOT EXISTS (SELECT 1 FROM adventure_conversation_compaction_job held WHERE held.adventure_id=? AND held.status='LEASED' AND held.lease_until > ?)
                    ORDER BY available_at, job_id FOR UPDATE SKIP LOCKED LIMIT 1""")) {
                select.setObject(1, adventureId.value()); select.setTimestamp(2, Timestamp.from(now)); select.setTimestamp(3, Timestamp.from(now)); select.setObject(4, adventureId.value()); select.setTimestamp(5, Timestamp.from(now));
                try (ResultSet rows = select.executeQuery()) { if (!rows.next()) { if (!managed) connection.commit(); return Optional.empty(); }
                    ConversationCompactionJob leased = read(rows).lease(until); update(connection, leased); if (!managed) connection.commit(); return Optional.of(leased); }
            } catch (SQLException | RuntimeException error) { if (!managed) connection.rollback(); throw error; } finally { if (!managed) connection.setAutoCommit(autoCommit); }
        } catch (SQLException error) { throw new AdventurePersistenceException("could not lease conversation compaction job", error); }
    }
    @Override public boolean save(ConversationCompactionJob leasedJob, ConversationCompactionJob updatedJob) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE adventure_conversation_compaction_job SET status=?, available_at=?, lease_until=?, lease_token=?, attempts=? WHERE job_id=? AND status='LEASED' AND lease_token=?")) {
            bindTransition(statement, updatedJob, leasedJob); return statement.executeUpdate() == 1;
        } catch (SQLException error) { throw new AdventurePersistenceException("could not save conversation compaction job", error); }
    }
    @Override public boolean manualReview(ConversationCompactionJob leasedJob, String reason) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE adventure_conversation_compaction_job SET status='MANUAL_REVIEW', lease_until=NULL, lease_token=NULL, last_error=? WHERE job_id=? AND status='LEASED' AND lease_token=?")) {
            statement.setString(1, reason == null ? "UNKNOWN" : reason.substring(0, Math.min(reason.length(), 1000))); statement.setObject(2, leasedJob.id()); statement.setObject(3, leasedJob.leaseToken()); return statement.executeUpdate() == 1;
        } catch (SQLException error) { throw new AdventurePersistenceException("could not record conversation compaction failure", error); }
    }
    @Override public boolean publish(ConversationCompactionJob job, ConversationSummary summary, List<LongTermAdventureFact> facts, long actualAdventureVersion) {
        // Later confirmed turns may advance the Adventure while this immutable source range remains valid.
        // The locked Adventure row, lease token, and exact source-range uniqueness fence publication.
        if (actualAdventureVersion < job.expectedAdventureVersion()) return false;
        try (Connection connection = dataSource.getConnection()) {
            boolean managed = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(); boolean autoCommit = connection.getAutoCommit(); if (!managed) connection.setAutoCommit(false);
            try (PreparedStatement adventure = connection.prepareStatement("SELECT version FROM adventure WHERE adventure_id=? FOR UPDATE")) {
                adventure.setObject(1, job.adventureId().value()); try (ResultSet rows = adventure.executeQuery()) { if (!rows.next() || rows.getLong(1) != actualAdventureVersion || actualAdventureVersion < job.expectedAdventureVersion()) { if (!managed) connection.commit(); return false; } }
                try (PreparedStatement existing = connection.prepareStatement("SELECT 1 FROM adventure_conversation_summary WHERE adventure_id=? AND source_start=? AND source_end=?")) {
                    existing.setObject(1, summary.adventureId().value()); existing.setLong(2, summary.sourceStart()); existing.setLong(3, summary.sourceEnd());
                    try (ResultSet rows = existing.executeQuery()) { if (rows.next()) { if (!managed) connection.commit(); return false; } }
                }
                try (PreparedStatement complete = connection.prepareStatement("UPDATE adventure_conversation_compaction_job SET status='DONE', lease_until=NULL, lease_token=NULL WHERE job_id=? AND status='LEASED' AND lease_token=?")) {
                    complete.setObject(1, job.id()); complete.setObject(2, job.leaseToken()); if (complete.executeUpdate() != 1) { if (!managed) connection.commit(); return false; }
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO adventure_conversation_summary(adventure_id, summary_version, source_start, source_end, source_adventure_version, summary_text) VALUES (?, ?, ?, ?, ?, ?)")) {
                    insert.setObject(1, summary.adventureId().value()); insert.setLong(2, summary.version()); insert.setLong(3, summary.sourceStart()); insert.setLong(4, summary.sourceEnd()); insert.setLong(5, summary.sourceAdventureVersion()); insert.setString(6, summary.text()); insert.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO adventure_long_term_fact(adventure_id, fact_id, established_turn_id, source_adventure_version, fact_kind, relevance, player_visible, fact_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (adventure_id, fact_id) DO NOTHING")) {
                    for (LongTermAdventureFact fact : facts) {
                        insert.setObject(1, fact.adventureId().value()); insert.setObject(2, fact.factId()); insert.setObject(3, fact.establishedTurnId());
                        insert.setLong(4, fact.sourceAdventureVersion()); insert.setString(5, fact.kind()); insert.setString(6, fact.relevance());
                        insert.setBoolean(7, fact.playerVisible()); insert.setLong(8, fact.version()); insert.addBatch();
                    }
                    if (!facts.isEmpty()) insert.executeBatch();
                }
                if (!managed) connection.commit(); return true;
            } catch (SQLException | RuntimeException error) { if (!managed) connection.rollback(); throw error; } finally { if (!managed) connection.setAutoCommit(autoCommit); }
        } catch (SQLException error) { throw new AdventurePersistenceException("could not publish conversation summary", error); }
    }
    @Override public List<ConversationSummary> summaries(AdventureId adventureId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT summary_version, source_start, source_end, source_adventure_version, summary_text FROM adventure_conversation_summary WHERE adventure_id=? ORDER BY summary_version")) {
            statement.setObject(1, adventureId.value()); List<ConversationSummary> result = new ArrayList<>(); try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(new ConversationSummary(adventureId, rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4), rows.getString(5))); } return List.copyOf(result);
        } catch (SQLException error) { throw new AdventurePersistenceException("could not load conversation summaries", error); }
    }
    @Override public List<LongTermAdventureFact> longTermFacts(AdventureId adventureId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT fact_id, established_turn_id, source_adventure_version, fact_kind, relevance, player_visible, fact_version FROM adventure_long_term_fact WHERE adventure_id=? ORDER BY fact_version, fact_id")) {
            statement.setObject(1, adventureId.value()); List<LongTermAdventureFact> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(new LongTermAdventureFact(adventureId,
                    rows.getObject(1, java.util.UUID.class), rows.getObject(2, java.util.UUID.class), rows.getLong(3), rows.getString(4),
                    rows.getString(5), rows.getBoolean(6), rows.getLong(7))); }
            return List.copyOf(result);
        } catch (SQLException error) { throw new AdventurePersistenceException("could not load long-term adventure facts", error); }
    }
    @Override public long coveredThrough(AdventureId adventureId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(source_end), -1) FROM adventure_conversation_compaction_job WHERE adventure_id=?")) {
            statement.setObject(1, adventureId.value()); try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        } catch (SQLException error) { throw new AdventurePersistenceException("could not load conversation compaction coverage", error); }
    }
    @Override public List<ConversationCompactionJob> ready(Instant now) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM adventure_conversation_compaction_job WHERE (status IN ('READY', 'RETRY_WAIT') AND available_at <= ?) OR (status='LEASED' AND lease_until <= ?) ORDER BY available_at, job_id LIMIT 20""")) {
            statement.setTimestamp(1, Timestamp.from(now)); statement.setTimestamp(2, Timestamp.from(now)); List<ConversationCompactionJob> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(read(rows)); } return List.copyOf(result);
        } catch (SQLException error) { throw new AdventurePersistenceException("could not list ready conversation compaction jobs", error); }
    }
    private Optional<ConversationCompactionJob> byKey(Connection connection, String key) throws SQLException { try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM adventure_conversation_compaction_job WHERE idempotency_key=?")) { statement.setString(1, key); try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(read(rows)) : Optional.empty(); } } }
    private static void bind(PreparedStatement statement, ConversationCompactionJob job) throws SQLException { statement.setObject(1, job.id()); statement.setObject(2, job.adventureId().value()); statement.setLong(3, job.sourceStart()); statement.setLong(4, job.sourceEnd()); statement.setLong(5, job.expectedAdventureVersion()); statement.setString(6, job.idempotencyKey()); statement.setString(7, job.status().name()); statement.setTimestamp(8, Timestamp.from(job.availableAt())); if (job.leaseUntil()==null) statement.setTimestamp(9,null); else statement.setTimestamp(9,Timestamp.from(job.leaseUntil())); statement.setInt(10, job.attempts()); }
    private static void update(Connection connection, ConversationCompactionJob job) throws SQLException { try (PreparedStatement statement = connection.prepareStatement("UPDATE adventure_conversation_compaction_job SET status=?, available_at=?, lease_until=?, lease_token=?, attempts=? WHERE job_id=?")) { statement.setString(1,job.status().name()); statement.setTimestamp(2,Timestamp.from(job.availableAt())); if(job.leaseUntil()==null) statement.setTimestamp(3,null); else statement.setTimestamp(3,Timestamp.from(job.leaseUntil())); statement.setObject(4, job.leaseToken()); statement.setInt(5,job.attempts()); statement.setObject(6,job.id()); if(statement.executeUpdate()!=1) throw new SQLException("compaction job was not found"); } }
    private static void bindTransition(PreparedStatement statement, ConversationCompactionJob updated, ConversationCompactionJob leased) throws SQLException { statement.setString(1, updated.status().name()); statement.setTimestamp(2, Timestamp.from(updated.availableAt())); if (updated.leaseUntil() == null) statement.setTimestamp(3, null); else statement.setTimestamp(3, Timestamp.from(updated.leaseUntil())); statement.setObject(4, updated.leaseToken()); statement.setInt(5, updated.attempts()); statement.setObject(6, leased.id()); statement.setObject(7, leased.leaseToken()); }
    private static ConversationCompactionJob read(ResultSet row) throws SQLException { Timestamp lease=row.getTimestamp("lease_until"); return new ConversationCompactionJob(row.getObject("job_id", java.util.UUID.class),new AdventureId(row.getObject("adventure_id",java.util.UUID.class)),row.getLong("source_start"),row.getLong("source_end"),row.getLong("expected_adventure_version"),row.getString("idempotency_key"),ConversationCompactionJob.Status.valueOf(row.getString("status")),row.getTimestamp("available_at").toInstant(),lease==null?null:lease.toInstant(),row.getInt("attempts"), row.getObject("lease_token", java.util.UUID.class)); }
}
