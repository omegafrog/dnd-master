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
            try (var insert = connection.prepareStatement("INSERT INTO gm_context_checkpoint(checkpoint_id,session_id,source_turn_id,checkpoint_version,summary,unresolved_threats_json,plan_revision_id,plan_version,exact_tail_json,snapshot_references_json,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (session_id,source_turn_id) DO NOTHING")) {
                insert.setObject(1, checkpoint.checkpointId()); insert.setObject(2, checkpoint.sessionId()); insert.setObject(3, checkpoint.sourceTurnId()); insert.setLong(4, checkpoint.version()); insert.setString(5, checkpoint.summary()); insert.setString(6, mapper.writeValueAsString(checkpoint.unresolvedThreats())); insert.setObject(7, checkpoint.planRevisionId()); insert.setLong(8, checkpoint.planVersion()); insert.setString(9, mapper.writeValueAsString(checkpoint.exactTail())); insert.setString(10, mapper.writeValueAsString(checkpoint.snapshotReferences())); insert.setObject(11, checkpoint.createdAt()); insert.executeUpdate();
            }
            try (var pointer = connection.prepareStatement("INSERT INTO gm_context_checkpoint_current(session_id,checkpoint_id,updated_at) VALUES (?,?,CURRENT_TIMESTAMP) ON CONFLICT(session_id) DO UPDATE SET checkpoint_id=EXCLUDED.checkpoint_id,updated_at=CURRENT_TIMESTAMP")) { pointer.setObject(1, checkpoint.sessionId()); pointer.setObject(2, checkpoint.checkpointId()); pointer.executeUpdate(); }
            if (!external) { connection.commit(); connection.setAutoCommit(previous); }
        } catch (Exception exception) { throw new AdventurePersistenceException("could not append GM context checkpoint", exception); }
    }
    private GmContextCheckpoint read(java.sql.ResultSet row) throws Exception {
        return new GmContextCheckpoint(row.getObject("checkpoint_id", UUID.class), row.getObject("session_id", UUID.class), row.getObject("source_turn_id", UUID.class), row.getLong("checkpoint_version"), row.getString("summary"), mapper.readValue(row.getString("unresolved_threats_json"), new TypeReference<List<String>>() {}), row.getObject("plan_revision_id", UUID.class), row.getLong("plan_version"), mapper.readValue(row.getString("exact_tail_json"), com.dndmaster.adventure.domain.runtime.checkpoint.ExactTail.class), mapper.readValue(row.getString("snapshot_references_json"), com.dndmaster.adventure.domain.runtime.checkpoint.SnapshotReferences.class), row.getTimestamp("created_at").toInstant());
    }
}
