package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresRuntimeTurnCommandRepository implements RuntimeTurnCommandRepository {
    private final JdbcTemplate jdbc;

    public PostgresRuntimeTurnCommandRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override public Optional<RuntimeTurnCommand> findByCommandId(UUID commandId) {
        return jdbc.query("SELECT * FROM adventure_runtime_turn_command WHERE command_id = ?", this::map, commandId)
                .stream().findFirst();
    }

    @Override public List<RuntimeTurnCommand> findByTurnId(UUID turnId) {
        return jdbc.query("SELECT * FROM adventure_runtime_turn_command WHERE turn_id = ? ORDER BY execution_order, command_id",
                this::map, turnId);
    }

    @Override public Optional<RuntimeTurnCommand> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query("SELECT * FROM adventure_runtime_turn_command WHERE idempotency_key = ?", this::map, idempotencyKey)
                .stream().findFirst();
    }

    @Override public void save(RuntimeTurnCommand command) {
        jdbc.update("""
                INSERT INTO adventure_runtime_turn_command
                    (command_id, turn_id, adventure_id, session_id, owner_player_id, target_context, command_type,
                     payload_json, execution_status, execution_order, idempotency_key, last_error, attempt_count, outcome_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (command_id) DO UPDATE SET
                    execution_status = EXCLUDED.execution_status,
                    last_error = EXCLUDED.last_error,
                    attempt_count = EXCLUDED.attempt_count,
                    outcome_json = EXCLUDED.outcome_json,
                    updated_at = CURRENT_TIMESTAMP
                """, command.commandId(), command.turnId(), command.adventureId(), command.sessionId(),
                command.ownerPlayerId(), command.targetContext(), command.commandType(), command.payloadJson(),
                command.executionStatus().name(), command.executionOrder(), command.idempotencyKey(), command.lastError(),
                command.attemptCount(), command.outcomeJson());
    }

    private RuntimeTurnCommand map(ResultSet row, int ignored) throws SQLException {
        return new RuntimeTurnCommand(row.getObject("command_id", UUID.class), row.getObject("turn_id", UUID.class),
                row.getObject("adventure_id", UUID.class), row.getObject("session_id", UUID.class),
                row.getObject("owner_player_id", UUID.class), row.getString("target_context"), row.getString("command_type"),
                row.getString("payload_json"), RuntimeTurnCommand.ExecutionStatus.valueOf(row.getString("execution_status")),
                row.getInt("execution_order"), row.getString("idempotency_key"), row.getString("last_error"),
                row.getInt("attempt_count"), row.getString("outcome_json"));
    }
}
