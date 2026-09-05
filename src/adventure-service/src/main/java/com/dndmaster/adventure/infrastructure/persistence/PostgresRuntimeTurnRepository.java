package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresRuntimeTurnRepository implements RuntimeTurnRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PostgresRuntimeTurnRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(java.util.Objects.requireNonNull(dataSource, "data source must not be null"));
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public Optional<RuntimeTurn> findByTurnId(UUID turnId) {
        String sql = "SELECT runtime_turn_json, turn_id, command_id, session_id, scenario_package_id FROM adventure_runtime_turn WHERE turn_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, turnId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not load runtime turn", exception);
        }
    }

    @Override
    public Optional<RuntimeTurn> findByCommandId(UUID commandId) {
        String sql = "SELECT runtime_turn_json, turn_id, command_id, session_id, scenario_package_id FROM adventure_runtime_turn WHERE command_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, commandId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not load runtime turn by command id", exception);
        }
    }

    @Override
    public List<RuntimeTurn> findAllByAdventureId(AdventureId adventureId) {
        String sql = "SELECT runtime_turn_json, turn_id, command_id, session_id, scenario_package_id FROM adventure_runtime_turn WHERE adventure_id = ? ORDER BY created_at, turn_id";
        List<RuntimeTurn> turns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, adventureId.value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) turns.add(read(rows));
            }
            return turns;
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not list runtime turns", exception);
        }
    }

    @Override
    public List<RuntimeTurn> findAllBySessionId(UUID sessionId) {
        String sql = "SELECT runtime_turn_json, turn_id, command_id, session_id, scenario_package_id FROM adventure_runtime_turn WHERE session_id = ? ORDER BY created_at, turn_id";
        List<RuntimeTurn> turns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) turns.add(read(rows)); }
            return turns;
        } catch (SQLException exception) { throw new RuntimeTurnPersistenceException("could not list runtime turns by session", exception); }
    }

    @Override
    public List<RuntimeTurn> findAllByLifecycle(com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle lifecycle) {
        String sql = "SELECT runtime_turn_json, turn_id, command_id, session_id, scenario_package_id FROM adventure_runtime_turn WHERE lifecycle = ? ORDER BY created_at, turn_id";
        List<RuntimeTurn> turns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lifecycle.name());
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) turns.add(read(rows)); }
            return turns;
        } catch (SQLException exception) { throw new RuntimeTurnPersistenceException("could not list runtime turns by lifecycle", exception); }
    }

    @Override
    public void save(RuntimeTurn turn) {
        String sql = """
                INSERT INTO adventure_runtime_turn (
                    turn_id, command_id, adventure_id, session_id, binding_version, scenario_package_id, action, runtime_turn_json,
                    requested_endpoint_id, requested_provider, requested_model, requested_reasoning,
                    effective_endpoint_id, effective_endpoint_version, effective_provider, effective_model, effective_reasoning, attempt_count,
                    lifecycle, fixed_resolution_json, pending_state_json, completion_proposal_json, narration
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                ON CONFLICT (command_id) DO UPDATE SET
                    turn_id = EXCLUDED.turn_id,
                    adventure_id = EXCLUDED.adventure_id,
                    session_id = EXCLUDED.session_id,
                    binding_version = EXCLUDED.binding_version,
                    scenario_package_id = EXCLUDED.scenario_package_id,
                    action = EXCLUDED.action,
                    runtime_turn_json = EXCLUDED.runtime_turn_json,
                    requested_endpoint_id = EXCLUDED.requested_endpoint_id,
                    requested_provider = EXCLUDED.requested_provider,
                    requested_model = EXCLUDED.requested_model,
                    requested_reasoning = EXCLUDED.requested_reasoning,
                    effective_endpoint_id = EXCLUDED.effective_endpoint_id,
                    effective_endpoint_version = EXCLUDED.effective_endpoint_version,
                    effective_provider = EXCLUDED.effective_provider,
                    effective_model = EXCLUDED.effective_model,
                    effective_reasoning = EXCLUDED.effective_reasoning,
                    attempt_count = EXCLUDED.attempt_count,
                    lifecycle = EXCLUDED.lifecycle,
                    fixed_resolution_json = EXCLUDED.fixed_resolution_json,
                    pending_state_json = EXCLUDED.pending_state_json,
                    completion_proposal_json = EXCLUDED.completion_proposal_json,
                    narration = EXCLUDED.narration
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, turn.turnId());
            statement.setObject(2, turn.commandId());
            statement.setObject(3, turn.adventureId().value());
            statement.setObject(4, turn.sessionId());
            statement.setLong(5, turn.bindingVersion());
            statement.setObject(6, turn.scenarioPackageId());
            statement.setString(7, turn.action());
            statement.setString(8, write(turn));
            statement.setObject(9, turn.plan().requestedSelection().endpointId());
            statement.setString(10, turn.plan().requestedSelection().provider());
            statement.setString(11, turn.plan().requestedSelection().model());
            statement.setString(12, turn.plan().requestedSelection().reasoning());
            statement.setObject(13, turn.plan().effectiveSelection().endpointId());
            if (turn.plan().effectiveSelection().endpointVersion() == null) statement.setTimestamp(14, null);
            else statement.setTimestamp(14, java.sql.Timestamp.from(turn.plan().effectiveSelection().endpointVersion()));
            statement.setString(15, turn.plan().effectiveSelection().provider());
            statement.setString(16, turn.plan().effectiveSelection().model());
            statement.setString(17, turn.plan().effectiveSelection().reasoning());
            statement.setInt(18, 1);
            statement.setString(19, turn.lifecycle().name());
            statement.setString(20, writeJson(turn.fixedResolution()));
            statement.setString(21, writeJson(turn.pendingState()));
            statement.setString(22, writeJson(turn.completionProposal()));
            statement.setString(23, turn.narration());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not save runtime turn", exception);
        }
    }

    private RuntimeTurn read(ResultSet row) throws SQLException {
        return RuntimeTurnJsonCompatibilityAdapter.read(objectMapper, row.getString("runtime_turn_json"),
                (UUID) row.getObject("turn_id"), (UUID) row.getObject("command_id"),
                (UUID) row.getObject("session_id"), (UUID) row.getObject("scenario_package_id"));
    }

    private String write(RuntimeTurn turn) throws SQLException {
        try {
            return objectMapper.writeValueAsString(turn);
        } catch (Exception exception) {
            throw new SQLException("could not write runtime turn", exception);
        }
    }

    private String writeJson(Object value) throws SQLException {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new SQLException("could not write runtime turn state", exception);
        }
    }
}
