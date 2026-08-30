package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.RuntimeTurnFailureArtifact;
import com.dndmaster.adventure.application.runtime.RuntimeTurnFailureCode;
import com.dndmaster.adventure.application.runtime.RuntimeTurnFailureRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnFailureStage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresRuntimeTurnFailureRepository implements RuntimeTurnFailureRepository {
    private final DataSource dataSource;
    public PostgresRuntimeTurnFailureRepository(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }

    @Override public void append(RuntimeTurnFailureArtifact failure) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO runtime_turn_failure_artifact
                (artifact_id, turn_id, failure_code, stage, retryable, root_cause_class, correlation_id, attempt, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, failure.artifactId()); statement.setObject(2, failure.turnId());
            statement.setString(3, failure.failureCode().name()); statement.setString(4, failure.stage().name());
            statement.setBoolean(5, failure.retryable()); statement.setString(6, failure.rootCauseClass());
            statement.setObject(7, failure.correlationId()); statement.setInt(8, failure.attempt());
            statement.setTimestamp(9, Timestamp.from(failure.occurredAt())); statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not append runtime turn failure", exception);
        }
    }

    @Override public List<RuntimeTurnFailureArtifact> findByTurnId(UUID turnId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT artifact_id, turn_id, failure_code, stage, retryable, root_cause_class, correlation_id, attempt, occurred_at
                FROM runtime_turn_failure_artifact WHERE turn_id = ? ORDER BY occurred_at, artifact_id
                """)) {
            statement.setObject(1, turnId);
            List<RuntimeTurnFailureArtifact> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new RuntimeTurnFailureArtifact(
                        rows.getObject("artifact_id", UUID.class), rows.getObject("turn_id", UUID.class),
                        RuntimeTurnFailureCode.valueOf(rows.getString("failure_code")),
                        RuntimeTurnFailureStage.valueOf(rows.getString("stage")), rows.getBoolean("retryable"),
                        rows.getString("root_cause_class"), rows.getObject("correlation_id", UUID.class),
                        rows.getInt("attempt"), rows.getTimestamp("occurred_at").toInstant()));
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not read runtime turn failures", exception);
        }
    }
}
