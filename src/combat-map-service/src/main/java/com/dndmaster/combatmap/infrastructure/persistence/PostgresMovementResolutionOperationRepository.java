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
                ? "INSERT INTO combat_map_movement_operation(operation_id,map_id,command_id,player_id,token_id,requested_path,path_distance,fingerprint,expected_version,status,cursor,current_x,current_y,traversed_path,result_traversed_path,result_final_x,result_final_y,result_map_version,result_public_events,result_interruption_reason,retry_count,operation_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                : "UPDATE combat_map_movement_operation SET status=?,cursor=?,current_x=?,current_y=?,traversed_path=?,result_traversed_path=?,result_final_x=?,result_final_y=?,result_map_version=?,result_public_events=?,result_interruption_reason=?,retry_count=?,operation_version=operation_version+1,updated_at=CURRENT_TIMESTAMP WHERE operation_id=? AND operation_version=?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            if (insert) { statement.setObject(1, value.operationId()); statement.setObject(2, value.mapId().value()); statement.setObject(3, value.commandId()); statement.setObject(4, value.playerId().value()); statement.setObject(5, value.tokenId().value()); statement.setString(6, path); statement.setInt(7, value.requestedPath().distance()); statement.setString(8, value.fingerprint()); statement.setLong(9, value.expectedVersion()); bindProgress(statement, 10, value); statement.setLong(22, value.persistenceVersion()); }
            else { bindProgress(statement, 1, value); statement.setObject(13, value.operationId()); statement.setLong(14, value.persistenceVersion()); }
            if (statement.executeUpdate() != 1) throw new CombatMapPersistenceException("movement operation save lost", null);
            if (!insert) value.markPersisted(value.persistenceVersion() + 1);
        } catch (SQLException exception) {
            if (insert && "23505".equals(exception.getSQLState())
                    && exception.getMessage() != null && exception.getMessage().contains("combat_map_movement_operation_active_map_uq")) {
                throw new com.dndmaster.combatmap.application.movement.MovementReservationConflictException();
            }
            throw new CombatMapPersistenceException("movement operation save failed", exception);
        }
    }
    private static void bindProgress(java.sql.PreparedStatement statement, int start, MovementResolutionOperation value) throws SQLException { statement.setString(start, value.status().name()); statement.setInt(start + 1, value.cursor()); statement.setInt(start + 2, value.currentCell().x()); statement.setInt(start + 3, value.currentCell().y()); statement.setString(start + 4, encode(value.traversedPath())); MovementResolutionResult result = value.result(); statement.setString(start + 5, result == null ? null : encode(result.traversedPath())); if (result == null) { statement.setNull(start + 6, java.sql.Types.INTEGER); statement.setNull(start + 7, java.sql.Types.INTEGER); statement.setNull(start + 8, java.sql.Types.BIGINT); statement.setNull(start + 9, java.sql.Types.VARCHAR); statement.setNull(start + 10, java.sql.Types.VARCHAR); } else { statement.setInt(start + 6, result.finalPosition().x()); statement.setInt(start + 7, result.finalPosition().y()); statement.setLong(start + 8, result.mapVersion()); statement.setString(start + 9, String.join("\u001f", result.publicEvents())); statement.setString(start + 10, result.interruptionReason()); } statement.setInt(start + 11, value.retryCount()); }
    private static MovementResolutionOperation read(ResultSet row) throws SQLException {
        List<GridPosition> requested = decode(row.getString("requested_path")); List<GridPosition> traversed = decode(row.getString("traversed_path"));
        MovementOperationStatus status = MovementOperationStatus.valueOf(row.getString("status"));
        long expectedVersion = row.getLong("expected_version");
        MovementResolutionResult result = status == MovementOperationStatus.COMMITTED
                ? new MovementResolutionResult(new MovementPath(requested, row.getInt("path_distance")), decode(row.getString("result_traversed_path")),
                        new GridPosition(row.getInt("result_final_x"), row.getInt("result_final_y")), row.getLong("result_map_version"),
                        row.getString("result_public_events") == null || row.getString("result_public_events").isEmpty() ? List.of() : List.of(row.getString("result_public_events").split("\\u001f")), row.getString("result_interruption_reason"))
                : null;
        return MovementResolutionOperation.restore((UUID) row.getObject("operation_id"), new MapId((UUID) row.getObject("map_id")), (UUID) row.getObject("command_id"), new PlayerId((UUID) row.getObject("player_id")), new TokenId((UUID) row.getObject("token_id")), new MovementPath(requested, row.getInt("path_distance")), row.getString("fingerprint"), expectedVersion, status, row.getInt("cursor"), new GridPosition(row.getInt("current_x"), row.getInt("current_y")), traversed, result, row.getInt("retry_count"), row.getLong("operation_version"));
    }
    private static String encode(List<GridPosition> positions) { return positions.stream().map(p -> p.x() + "," + p.y()).collect(java.util.stream.Collectors.joining(";")); }
    private static List<GridPosition> decode(String encoded) { return encoded == null || encoded.isEmpty() ? List.of() : java.util.Arrays.stream(encoded.split(";")).map(pair -> pair.split(",")).map(pair -> new GridPosition(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]))).toList(); }
}
