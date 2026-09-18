package com.dndmaster.combatmap.infrastructure.persistence;

import com.dndmaster.combatmap.application.movement.MovementOperationStatus;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperation;
import com.dndmaster.combatmap.application.movement.MovementResolutionResult;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperationRepository;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MovementPath;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** PostgreSQL-backed coordinator record. It deliberately stores no public map mutation. */
public final class PostgresMovementResolutionOperationRepository implements MovementResolutionOperationRepository {
    private final DataSource dataSource;
    public PostgresMovementResolutionOperationRepository(DataSource dataSource) { this.dataSource = dataSource; }
    @Override public Optional<MovementResolutionOperation> findById(UUID id) { return find("operation_id", id); }
    @Override public Optional<MovementResolutionOperation> findOperationByCommandId(UUID id) { return find("command_id", id); }
    @Override public Optional<MovementResolutionOperation> findActiveByMapId(MapId id) {
        return find("map_id", id.value(), " AND status IN ('PREPARING','RETRY_WAIT','READY_TO_COMMIT')");
    }
    @Override public void reserve(MovementResolutionOperation value) { write(value, true); }
    @Override public void save(MovementResolutionOperation value) { write(value, false); }
    private Optional<MovementResolutionOperation> find(String field, UUID value) { return find(field, value, ""); }
    private Optional<MovementResolutionOperation> find(String field, UUID value, String suffix) {
        String sql = "SELECT * FROM combat_map_movement_operation WHERE " + field + "=?" + suffix + " ORDER BY created_at LIMIT 1";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value); try (var rows = statement.executeQuery()) { return rows.next() ? Optional.of(read(rows)) : Optional.empty(); }
        } catch (SQLException exception) { throw new CombatMapPersistenceException("movement operation load failed", exception); }
    }
    private void write(MovementResolutionOperation value, boolean insert) {
        String path = encode(value.requestedPath().orderedPositions());
        String sql = insert
                ? "INSERT INTO combat_map_movement_operation(operation_id,map_id,command_id,player_id,token_id,requested_path,path_distance,fingerprint,expected_version,status,cursor,current_x,current_y,traversed_path) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                : "UPDATE combat_map_movement_operation SET status=?,cursor=?,current_x=?,current_y=?,traversed_path=?,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            if (insert) { statement.setObject(1, value.operationId()); statement.setObject(2, value.mapId().value()); statement.setObject(3, value.commandId()); statement.setObject(4, value.playerId().value()); statement.setObject(5, value.tokenId().value()); statement.setString(6, path); statement.setInt(7, value.requestedPath().distance()); statement.setString(8, value.fingerprint()); statement.setLong(9, value.expectedVersion()); bindProgress(statement, 10, value); }
            else { bindProgress(statement, 1, value); statement.setObject(6, value.operationId()); }
            if (statement.executeUpdate() != 1) throw new CombatMapPersistenceException("movement operation save lost", null);
        } catch (SQLException exception) { throw new CombatMapPersistenceException("movement operation save failed", exception); }
    }
    private static void bindProgress(java.sql.PreparedStatement statement, int start, MovementResolutionOperation value) throws SQLException { statement.setString(start, value.status().name()); statement.setInt(start + 1, value.cursor()); statement.setInt(start + 2, value.currentCell().x()); statement.setInt(start + 3, value.currentCell().y()); statement.setString(start + 4, encode(value.traversedPath())); }
    private static MovementResolutionOperation read(ResultSet row) throws SQLException {
        List<GridPosition> requested = decode(row.getString("requested_path")); List<GridPosition> traversed = decode(row.getString("traversed_path"));
        MovementOperationStatus status = MovementOperationStatus.valueOf(row.getString("status"));
        long expectedVersion = row.getLong("expected_version");
        MovementResolutionResult result = status == MovementOperationStatus.COMMITTED
                ? new MovementResolutionResult(new MovementPath(requested, row.getInt("path_distance")), traversed,
                        new GridPosition(row.getInt("current_x"), row.getInt("current_y")), expectedVersion + 1, List.of(), null)
                : null;
        return MovementResolutionOperation.restore((UUID) row.getObject("operation_id"), new MapId((UUID) row.getObject("map_id")), (UUID) row.getObject("command_id"), new PlayerId((UUID) row.getObject("player_id")), new TokenId((UUID) row.getObject("token_id")), new MovementPath(requested, row.getInt("path_distance")), row.getString("fingerprint"), expectedVersion, status, row.getInt("cursor"), new GridPosition(row.getInt("current_x"), row.getInt("current_y")), traversed, result);
    }
    private static String encode(List<GridPosition> positions) { return positions.stream().map(p -> p.x() + "," + p.y()).collect(java.util.stream.Collectors.joining(";")); }
    private static List<GridPosition> decode(String encoded) { return java.util.Arrays.stream(encoded.split(";")).map(pair -> pair.split(",")).map(pair -> new GridPosition(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]))).toList(); }
}
