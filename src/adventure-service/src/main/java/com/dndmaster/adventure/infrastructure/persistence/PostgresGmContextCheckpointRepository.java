package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.GmContextCheckpointRepository;
import com.dndmaster.adventure.domain.runtime.checkpoint.GmContextCheckpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresGmContextCheckpointRepository implements GmContextCheckpointRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    public PostgresGmContextCheckpointRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource);
        this.mapper = mapper;
    }
    public Optional<GmContextCheckpoint> current(UUID sessionId) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "SELECT c.* FROM gm_context_checkpoint_current p JOIN gm_context_checkpoint c ON c.checkpoint_id=p.checkpoint_id WHERE p.session_id=?")) {
            statement.setObject(1, sessionId);
            try (var rows = statement.executeQuery()) { return rows.next() ? Optional.of(read(rows)) : Optional.empty(); }
        } catch (Exception exception) { throw new AdventurePersistenceException("could not load current GM context checkpoint", exception); }
    }
    public List<GmContextCheckpoint> history(UUID sessionId) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "SELECT * FROM gm_context_checkpoint WHERE session_id=? ORDER BY checkpoint_version")) {
            statement.setObject(1, sessionId); List<GmContextCheckpoint> result = new ArrayList<>();
            try (var rows = statement.executeQuery()) { while (rows.next()) result.add(read(rows)); }
            return result;
        } catch (Exception exception) { throw new AdventurePersistenceException("could not load GM context checkpoint history", exception); }
    }
    public void append(GmContextCheckpoint checkpoint) {
        try (var connection = dataSource.getConnection()) {
            boolean external = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
            boolean previous = connection.getAutoCommit(); if (!external) connection.setAutoCommit(false);
            UUID persistedId;
            try (var insert = connection.prepareStatement("INSERT INTO gm_context_checkpoint(checkpoint_id,session_id,source_turn_id,checkpoint_version,summary,unresolved_threats_json,plan_revision_id,plan_version,exact_tail_json,snapshot_references_json,created_at,provider,model,reasoning) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (session_id,source_turn_id) DO NOTHING RETURNING checkpoint_id")) {
                insert.setObject(1, checkpoint.checkpointId()); insert.setObject(2, checkpoint.sessionId()); insert.setObject(3, checkpoint.sourceTurnId()); insert.setLong(4, checkpoint.version()); insert.setString(5, checkpoint.summary()); insert.setString(6, mapper.writeValueAsString(checkpoint.unresolvedThreats())); insert.setObject(7, checkpoint.planRevisionId()); insert.setLong(8, checkpoint.planVersion()); insert.setString(9, mapper.writeValueAsString(checkpoint.exactTail())); insert.setString(10, mapper.writeValueAsString(checkpoint.snapshotReferences())); insert.setObject(11, checkpoint.createdAt()); insert.setString(12, checkpoint.provider()); insert.setString(13, checkpoint.model()); insert.setString(14, checkpoint.reasoning());
                try (var rows = insert.executeQuery()) {
                    persistedId = rows.next() ? rows.getObject(1, UUID.class) : null;
                }
            }
            if (persistedId == null) {
                try (var existing = connection.prepareStatement("SELECT checkpoint_id FROM gm_context_checkpoint WHERE session_id=? AND source_turn_id=?")) { existing.setObject(1, checkpoint.sessionId()); existing.setObject(2, checkpoint.sourceTurnId()); try (var rows = existing.executeQuery()) { if (!rows.next()) throw new SQLException("checkpoint insert disappeared"); persistedId = rows.getObject(1, UUID.class); } }
            }
            try (var pointer = connection.prepareStatement("INSERT INTO gm_context_checkpoint_current(session_id,checkpoint_id,updated_at) VALUES (?,?,CURRENT_TIMESTAMP) ON CONFLICT(session_id) DO UPDATE SET checkpoint_id=EXCLUDED.checkpoint_id,updated_at=CURRENT_TIMESTAMP WHERE (SELECT checkpoint_version FROM gm_context_checkpoint WHERE checkpoint_id=gm_context_checkpoint_current.checkpoint_id) < (SELECT checkpoint_version FROM gm_context_checkpoint WHERE checkpoint_id=EXCLUDED.checkpoint_id)")) { pointer.setObject(1, checkpoint.sessionId()); pointer.setObject(2, persistedId); pointer.executeUpdate(); }
            if (!external) { connection.commit(); connection.setAutoCommit(previous); }
        } catch (Exception exception) { throw new AdventurePersistenceException("could not append GM context checkpoint", exception); }
    }
    private GmContextCheckpoint read(java.sql.ResultSet row) throws Exception {
        return new GmContextCheckpoint(row.getObject("checkpoint_id", UUID.class), row.getObject("session_id", UUID.class), row.getObject("source_turn_id", UUID.class), row.getLong("checkpoint_version"), row.getString("summary"), mapper.readValue(row.getString("unresolved_threats_json"), new TypeReference<List<String>>() {}), row.getObject("plan_revision_id", UUID.class), row.getLong("plan_version"), mapper.readValue(row.getString("exact_tail_json"), com.dndmaster.adventure.domain.runtime.checkpoint.ExactTail.class), mapper.readValue(row.getString("snapshot_references_json"), com.dndmaster.adventure.domain.runtime.checkpoint.SnapshotReferences.class), row.getTimestamp("created_at").toInstant(), row.getString("provider"), row.getString("model"), row.getString("reasoning"));
    }
}
