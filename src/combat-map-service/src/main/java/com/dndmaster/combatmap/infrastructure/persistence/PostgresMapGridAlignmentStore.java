package com.dndmaster.combatmap.infrastructure.persistence;

import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.MapId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** 정렬 및 그 재시도 결과만 한 트랜잭션으로 기록한다. */
public final class PostgresMapGridAlignmentStore implements MapGridAlignmentStore {
    private final DataSource dataSource;
    public PostgresMapGridAlignmentStore(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override
    public Optional<MapGridAlignment> find(MapId mapId) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT image_revision,origin_x,origin_y,cell_size,version FROM combat_map_alignment WHERE map_id=?")) {
            statement.setObject(1, mapId.value()); try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(mapId, rows)) : Optional.empty();
            }
        } catch (SQLException exception) { throw new CombatMapPersistenceException("map grid alignment load failed", exception); }
    }

    @Override
    public MapGridAlignment apply(MapOwnerId owner, MapId mapId, MapGridAlignmentRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MapGridAlignmentCommand replay = command(connection, owner, mapId, request.commandId());
                if (replay != null) {
                    if (!replay.request().equals(request)) throw new MapGridAlignmentConflictException();
                    connection.commit(); return replay.result();
                }
                if (!lockedImageRevision(connection, owner, mapId).equals(request.imageRevision())) throw new MapGridAlignmentConflictException();
                MapGridAlignment existing = current(connection, mapId);
                long version = existing == null ? 0 : existing.version();
                if (version != request.expectedVersion()) throw new MapGridAlignmentConflictException();
                MapGridAlignment saved = new MapGridAlignment(mapId, request.imageRevision(), request.originX(), request.originY(), request.cellSize(), version + 1);
                if (existing == null) insert(connection, saved); else update(connection, saved, version);
                recordCommand(connection, owner, request, saved);
                connection.commit(); return saved;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            }
        } catch (SQLException exception) { throw new CombatMapPersistenceException("map grid alignment save failed", exception); }
    }

    private static MapGridAlignmentCommand command(Connection connection, MapOwnerId owner, MapId mapId, java.util.UUID commandId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT expected_version,image_revision,origin_x,origin_y,cell_size,result_version FROM combat_map_alignment_command WHERE map_id=? AND owner_player_id=? AND command_id=?")) {
            statement.setObject(1, mapId.value()); statement.setObject(2, owner.value()); statement.setObject(3, commandId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                MapGridAlignmentRequest request = new MapGridAlignmentRequest(commandId, rows.getLong(1), rows.getString(2), rows.getDouble(3), rows.getDouble(4), rows.getDouble(5));
                return new MapGridAlignmentCommand(request, new MapGridAlignment(mapId, request.imageRevision(), request.originX(), request.originY(), request.cellSize(), rows.getLong(6)));
            }
        }
    }

    private static String lockedImageRevision(Connection connection, MapOwnerId owner, MapId mapId) throws SQLException {
        try (PreparedStatement map = connection.prepareStatement("SELECT map_id FROM combat_map WHERE map_id=? AND owner_player_id=? FOR UPDATE")) {
            map.setObject(1, mapId.value()); map.setObject(2, owner.value()); try (ResultSet rows = map.executeQuery()) {
                if (!rows.next()) throw new CombatMapAccessDeniedException();
            }
        }
        try (PreparedStatement layer = connection.prepareStatement("SELECT layer_value FROM combat_map_layer WHERE map_id=? AND layer_type='MAP_IMAGE' ORDER BY sequence LIMIT 1")) {
            layer.setObject(1, mapId.value()); try (ResultSet rows = layer.executeQuery()) {
                return hash(rows.next() ? rows.getString(1) : "");
            }
        }
    }

    private static MapGridAlignment current(Connection connection, MapId mapId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT image_revision,origin_x,origin_y,cell_size,version FROM combat_map_alignment WHERE map_id=? FOR UPDATE")) {
            statement.setObject(1, mapId.value()); try (ResultSet rows = statement.executeQuery()) { return rows.next() ? read(mapId, rows) : null; }
        }
    }
    private static MapGridAlignment read(MapId mapId, ResultSet rows) throws SQLException { return new MapGridAlignment(mapId, rows.getString(1), rows.getDouble(2), rows.getDouble(3), rows.getDouble(4), rows.getLong(5)); }
    private static void insert(Connection c, MapGridAlignment a) throws SQLException { try (PreparedStatement s=c.prepareStatement("INSERT INTO combat_map_alignment(map_id,image_revision,origin_x,origin_y,cell_size,version) VALUES (?,?,?,?,?,?)")){s.setObject(1,a.mapId().value());s.setString(2,a.imageRevision());s.setDouble(3,a.originX());s.setDouble(4,a.originY());s.setDouble(5,a.cellSize());s.setLong(6,a.version());s.executeUpdate();} }
    private static void update(Connection c, MapGridAlignment a, long expected) throws SQLException { try (PreparedStatement s=c.prepareStatement("UPDATE combat_map_alignment SET image_revision=?,origin_x=?,origin_y=?,cell_size=?,version=?,updated_at=CURRENT_TIMESTAMP WHERE map_id=? AND version=?")){s.setString(1,a.imageRevision());s.setDouble(2,a.originX());s.setDouble(3,a.originY());s.setDouble(4,a.cellSize());s.setLong(5,a.version());s.setObject(6,a.mapId().value());s.setLong(7,expected);if(s.executeUpdate()!=1)throw new MapGridAlignmentConflictException();} }
    private static void recordCommand(Connection c, MapOwnerId owner, MapGridAlignmentRequest r, MapGridAlignment a) throws SQLException { try (PreparedStatement s=c.prepareStatement("INSERT INTO combat_map_alignment_command(map_id,owner_player_id,command_id,expected_version,image_revision,origin_x,origin_y,cell_size,result_version) VALUES (?,?,?,?,?,?,?,?,?)")){s.setObject(1,a.mapId().value());s.setObject(2,owner.value());s.setObject(3,r.commandId());s.setLong(4,r.expectedVersion());s.setString(5,r.imageRevision());s.setDouble(6,r.originX());s.setDouble(7,r.originY());s.setDouble(8,r.cellSize());s.setLong(9,a.version());s.executeUpdate();} }
    private static String hash(String image) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); } }
}
