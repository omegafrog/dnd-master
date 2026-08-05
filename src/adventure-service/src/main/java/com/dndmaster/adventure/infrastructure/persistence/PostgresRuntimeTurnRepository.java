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
        String sql = "SELECT runtime_turn_json FROM adventure_runtime_turn WHERE turn_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, turnId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row.getString("runtime_turn_json"))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not load runtime turn", exception);
        }
    }

    @Override
    public Optional<RuntimeTurn> findByCommandId(UUID commandId) {
        String sql = "SELECT runtime_turn_json FROM adventure_runtime_turn WHERE command_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, commandId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row.getString("runtime_turn_json"))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not load runtime turn by command id", exception);
        }
    }

    @Override
    public List<RuntimeTurn> findAllByAdventureId(AdventureId adventureId) {
        String sql = "SELECT runtime_turn_json FROM adventure_runtime_turn WHERE adventure_id = ? ORDER BY created_at, turn_id";
        List<RuntimeTurn> turns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, adventureId.value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) turns.add(read(rows.getString("runtime_turn_json")));
            }
            return turns;
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not list runtime turns", exception);
        }
    }

    @Override
    public void save(RuntimeTurn turn) {
        String sql = """
                INSERT INTO adventure_runtime_turn (
                    turn_id, command_id, adventure_id, session_id, binding_version, scenario_package_id, action, runtime_turn_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (command_id) DO UPDATE SET
                    turn_id = EXCLUDED.turn_id,
                    adventure_id = EXCLUDED.adventure_id,
                    session_id = EXCLUDED.session_id,
                    binding_version = EXCLUDED.binding_version,
                    scenario_package_id = EXCLUDED.scenario_package_id,
                    action = EXCLUDED.action,
                    runtime_turn_json = EXCLUDED.runtime_turn_json
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
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeTurnPersistenceException("could not save runtime turn", exception);
        }
    }

    private RuntimeTurn read(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, RuntimeTurn.class);
        } catch (Exception exception) {
            throw new SQLException("could not read runtime turn", exception);
        }
    }

    private String write(RuntimeTurn turn) throws SQLException {
        try {
            return objectMapper.writeValueAsString(turn);
        } catch (Exception exception) {
            throw new SQLException("could not write runtime turn", exception);
        }
    }
}
