package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository;
import com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresPendingMapMovementConfirmationRepository implements PendingMapMovementConfirmationRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PostgresPendingMapMovementConfirmationRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "data source must not be null");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public Optional<PendingMapMovementConfirmation> findByAdventureId(UUID adventureId, UUID ownerPlayerId) {
        String sql = "SELECT owner_player_id, map_id, token_id, map_version, path_json, distance, fingerprint, waypoints_json, source_text, destination_x, destination_y, pending_turn_id, confirmation_command_id, terminal, movement_result_json "
                + "FROM adventure_pending_map_movement_confirmation WHERE adventure_id = ? AND owner_player_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, adventureId);
            statement.setObject(2, ownerPlayerId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(read(adventureId, row));
            }
        } catch (SQLException exception) {
            throw failure("could not load pending map movement confirmation", exception);
        }
    }

    @Override
    public void save(PendingMapMovementConfirmation confirmation) {
        String sql = """
                INSERT INTO adventure_pending_map_movement_confirmation
                    (adventure_id, owner_player_id, map_id, token_id, map_version, path_json, distance, fingerprint, waypoints_json, source_text, destination_x, destination_y, pending_turn_id, confirmation_command_id, terminal, movement_result_json, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
                ON CONFLICT (adventure_id) DO UPDATE SET
                    owner_player_id = EXCLUDED.owner_player_id,
                    map_id = EXCLUDED.map_id,
                    token_id = EXCLUDED.token_id,
                    map_version = EXCLUDED.map_version,
                    path_json = EXCLUDED.path_json,
                    distance = EXCLUDED.distance,
                    fingerprint = EXCLUDED.fingerprint,
                    waypoints_json = EXCLUDED.waypoints_json,
                    source_text = EXCLUDED.source_text,
                    destination_x = EXCLUDED.destination_x,
                    destination_y = EXCLUDED.destination_y,
                    pending_turn_id = EXCLUDED.pending_turn_id,
                    confirmation_command_id = EXCLUDED.confirmation_command_id,
                    terminal = EXCLUDED.terminal,
                    movement_result_json = EXCLUDED.movement_result_json,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, confirmation.adventureId());
            statement.setObject(2, confirmation.ownerPlayerId());
            statement.setObject(3, confirmation.mapId());
            statement.setObject(4, confirmation.tokenId());
            statement.setLong(5, confirmation.mapVersion());
            statement.setString(6, write(confirmation.path()));
            statement.setInt(7, confirmation.distance());
            statement.setString(8, confirmation.fingerprint());
            statement.setString(9, write(confirmation.waypoints()));
            statement.setString(10, confirmation.sourceText());
            if (confirmation.destination() == null) { statement.setObject(11, null); statement.setObject(12, null); }
            else { statement.setInt(11, confirmation.destination().x()); statement.setInt(12, confirmation.destination().y()); }
            statement.setObject(13, confirmation.pendingTurnId());
            statement.setObject(14, confirmation.confirmationCommandId());
            statement.setBoolean(15, confirmation.terminal());
            statement.setString(16, confirmation.movementResultJson().isBlank() ? null : confirmation.movementResultJson());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure("could not save pending map movement confirmation", exception);
        }
    }

    @Override
    public void deleteByAdventureId(UUID adventureId, UUID ownerPlayerId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM adventure_pending_map_movement_confirmation WHERE adventure_id = ? AND owner_player_id = ?")) {
            statement.setObject(1, adventureId);
            statement.setObject(2, ownerPlayerId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure("could not delete pending map movement confirmation", exception);
        }
    }

    private PendingMapMovementConfirmation read(UUID adventureId, ResultSet row) throws SQLException {
        try {
            UUID ownerPlayerId = row.getObject("owner_player_id", UUID.class);
            List<PendingMapMovementConfirmation.Position> path = objectMapper.readValue(row.getString("path_json"),
                    new TypeReference<>() {});
            List<PendingMapMovementConfirmation.Position> waypoints = objectMapper.readValue(row.getString("waypoints_json"),
                    new TypeReference<>() {});
            Integer destinationX = (Integer) row.getObject("destination_x");
            Integer destinationY = (Integer) row.getObject("destination_y");
            PendingMapMovementConfirmation.Position destination = destinationX == null || destinationY == null ? null
                    : new PendingMapMovementConfirmation.Position(destinationX, destinationY);
            return new PendingMapMovementConfirmation(adventureId, ownerPlayerId,
                    row.getObject("map_id", UUID.class), row.getObject("token_id", UUID.class),
                    row.getLong("map_version"), path, row.getInt("distance"), row.getString("fingerprint"), waypoints,
                    row.getString("source_text"), destination, row.getObject("pending_turn_id", UUID.class),
                    row.getObject("confirmation_command_id", UUID.class), row.getBoolean("terminal"), row.getString("movement_result_json"));
        } catch (Exception exception) {
            throw new SQLException("could not decode pending map movement confirmation", exception);
        }
    }

    private String write(Object value) throws SQLException {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new SQLException("could not encode pending map movement confirmation", exception);
        }
    }

    private static RuntimeException failure(String message, SQLException cause) {
        return new RuntimeException(message, cause);
    }
}
