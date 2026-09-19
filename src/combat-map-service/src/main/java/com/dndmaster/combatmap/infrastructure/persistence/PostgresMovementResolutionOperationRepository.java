package com.dndmaster.combatmap.infrastructure.persistence;

import com.dndmaster.combatmap.application.movement.MovementOperationStatus;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperation;
import com.dndmaster.combatmap.application.movement.MovementResolutionResult;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperationRepository;
import com.dndmaster.combatmap.application.movement.MovementResolutionOutcomeStatus;
import com.dndmaster.combatmap.application.movement.MovementCommandConflictException;
import com.dndmaster.combatmap.application.movement.MovementOperationConcurrentUpdateException;
import com.dndmaster.combatmap.application.movement.MovementCheckRequest;
import com.dndmaster.combatmap.application.movement.MovementCheckOutcome;
import com.dndmaster.combatmap.application.movement.MovementCheckActor;
import com.dndmaster.combatmap.application.movement.MovementCheckOwner;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MovementPath;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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
        return find("map_id", id.value(), " AND status IN ('PREPARING','CHECK_PENDING','RETRY_WAIT','READY_TO_COMMIT')");
    }
    @Override public Optional<MovementResolutionOperation> findLatestByMapId(MapId id) {
        String sql = "SELECT * FROM combat_map_movement_operation WHERE map_id=? ORDER BY updated_at DESC, created_at DESC, operation_id DESC LIMIT 1";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id.value());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new CombatMapPersistenceException("latest movement operation load failed", exception);
        }
    }
    @Override public MovementResolutionOperation reserve(MovementResolutionOperation value) { return write(value, true); }
    @Override public void save(MovementResolutionOperation value) { write(value, false); }
    @Override public List<MovementResolutionOperation> findRecoverable() {
        return findRecoverable("status IN ('PREPARING','CHECK_PENDING','RETRY_WAIT','READY_TO_COMMIT')", null);
    }
    @Override public List<MovementResolutionOperation> findStalledBefore(Instant cutoff) {
        return findRecoverable("status IN ('PREPARING','READY_TO_COMMIT') AND updated_at<=?", cutoff);
    }
    private List<MovementResolutionOperation> findRecoverable(String predicate, Instant cutoff) {
        String sql = "SELECT * FROM combat_map_movement_operation WHERE " + predicate + " ORDER BY created_at,operation_id";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            if (cutoff != null) statement.setTimestamp(1, java.sql.Timestamp.from(cutoff));
            try (var rows = statement.executeQuery()) {
            List<MovementResolutionOperation> result = new java.util.ArrayList<>();
            while (rows.next()) result.add(read(rows));
            return List.copyOf(result);
            }
        } catch (SQLException exception) { throw new CombatMapPersistenceException("movement operation recovery load failed", exception); }
    }
    private Optional<MovementResolutionOperation> find(String field, UUID value) { return find(field, value, ""); }
    private Optional<MovementResolutionOperation> find(String field, UUID value, String suffix) {
        String sql = "SELECT * FROM combat_map_movement_operation WHERE " + field + "=?" + suffix + " ORDER BY created_at LIMIT 1";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value); try (var rows = statement.executeQuery()) { return rows.next() ? Optional.of(read(rows)) : Optional.empty(); }
        } catch (SQLException exception) { throw new CombatMapPersistenceException("movement operation load failed", exception); }
    }
    private MovementResolutionOperation write(MovementResolutionOperation value, boolean insert) {
        String path = encode(value.requestedPath().orderedPositions());
        String sql = insert
                ? "INSERT INTO combat_map_movement_operation(operation_id,map_id,command_id,player_id,token_id,requested_path,path_distance,fingerprint,expected_version,status,cursor,current_x,current_y,traversed_path,result_traversed_path,result_status,result_final_x,result_final_y,result_map_version,result_public_events,result_interruption_reason,retry_count,operation_version,retry_resume_status,pending_check_id,pending_feature_id,pending_feature_type,pending_trigger,pending_rule_reference,pending_difficulty,pending_mode,pending_dice_expression,pending_modifier,pending_ownership,pending_owner_actor,pending_owner_player_id,check_outcomes) VALUES (" + "?,".repeat(36) + "?)"
                : "UPDATE combat_map_movement_operation SET status=?,cursor=?,current_x=?,current_y=?,traversed_path=?,result_traversed_path=?,result_status=?,result_final_x=?,result_final_y=?,result_map_version=?,result_public_events=?,result_interruption_reason=?,retry_count=?,retry_resume_status=?,pending_check_id=?,pending_feature_id=?,pending_feature_type=?,pending_trigger=?,pending_rule_reference=?,pending_difficulty=?,pending_mode=?,pending_dice_expression=?,pending_modifier=?,pending_ownership=?,pending_owner_actor=?,pending_owner_player_id=?,check_outcomes=?,operation_version=operation_version+1,updated_at=CURRENT_TIMESTAMP WHERE operation_id=? AND operation_version=?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            if (insert) { statement.setObject(1, value.operationId()); statement.setObject(2, value.mapId().value()); statement.setObject(3, value.commandId()); statement.setObject(4, value.playerId().value()); statement.setObject(5, value.tokenId().value()); statement.setString(6, path); statement.setInt(7, value.requestedPath().distance()); statement.setString(8, value.fingerprint()); statement.setLong(9, value.expectedVersion()); bindProgress(statement, 10, value); statement.setLong(23, value.persistenceVersion()); statement.setString(24, value.retryResumeStatus().name()); bindPending(statement, 25, value); }
            else { bindProgress(statement, 1, value); statement.setString(14, value.retryResumeStatus().name()); bindPending(statement, 15, value); statement.setObject(28, value.operationId()); statement.setLong(29, value.persistenceVersion()); }
            if (statement.executeUpdate() != 1) throw new MovementOperationConcurrentUpdateException();
            if (!insert) value.markPersisted(value.persistenceVersion() + 1);
            return value;
        } catch (SQLException exception) {
            if (insert && "23505".equals(exception.getSQLState())) {
                MovementResolutionOperation winner = findOperationByCommandId(value.commandId()).orElse(null);
                if (winner != null) {
                    if (!winner.fingerprint().equals(value.fingerprint())) throw new MovementCommandConflictException();
                    return winner;
                }
                throw new com.dndmaster.combatmap.application.movement.MovementReservationConflictException();
            }
            throw new CombatMapPersistenceException("movement operation save failed", exception);
        }
    }
    private static void bindPending(java.sql.PreparedStatement statement, int start, MovementResolutionOperation value) throws SQLException {
        MovementCheckRequest pending = value.pendingCheck();
        if (pending == null) {
            for (int index = 0; index < 12; index++) statement.setObject(start + index, null);
            statement.setInt(start + 8, 0);
        } else {
            statement.setObject(start, pending.checkId()); statement.setObject(start + 1, pending.featureId());
            statement.setString(start + 2, pending.featureType().name()); statement.setString(start + 3, pending.trigger().name());
            statement.setString(start + 4, pending.ruleReference());
            if (pending.difficulty() == null) statement.setObject(start + 5, null); else statement.setInt(start + 5, pending.difficulty());
            statement.setString(start + 6, pending.mode()); statement.setString(start + 7, pending.diceExpression());
            statement.setInt(start + 8, pending.modifier()); statement.setString(start + 9, pending.owner().actor().name());
            statement.setString(start + 10, pending.owner().actor().name()); statement.setObject(start + 11, pending.owner().playerId().value());
        }
        statement.setString(start + 12, value.checkOutcomes().stream().map(outcome -> outcome.commandId() == null
                ? outcome.featureId() + "=" + outcome.success()
                : String.join(",", outcome.featureId().toString(), outcome.checkId().toString(), outcome.commandId().toString(),
                        outcome.owner().actor().name(), outcome.owner().playerId().value().toString(), Boolean.toString(outcome.success())))
                .collect(java.util.stream.Collectors.joining(";")));
    }
    private static void bindProgress(java.sql.PreparedStatement statement, int start, MovementResolutionOperation value) throws SQLException { statement.setString(start, value.status().name()); statement.setInt(start + 1, value.cursor()); statement.setInt(start + 2, value.currentCell().x()); statement.setInt(start + 3, value.currentCell().y()); statement.setString(start + 4, encode(value.traversedPath())); MovementResolutionResult result = value.result(); statement.setString(start + 5, result == null ? null : encode(result.traversedPath())); statement.setString(start + 6, result == null ? null : result.status().name()); if (result == null) { statement.setNull(start + 7, java.sql.Types.INTEGER); statement.setNull(start + 8, java.sql.Types.INTEGER); statement.setNull(start + 9, java.sql.Types.BIGINT); statement.setNull(start + 10, java.sql.Types.VARCHAR); statement.setNull(start + 11, java.sql.Types.VARCHAR); } else { statement.setInt(start + 7, result.finalPosition().x()); statement.setInt(start + 8, result.finalPosition().y()); statement.setLong(start + 9, result.mapVersion()); statement.setString(start + 10, String.join("\u001f", result.publicEvents())); statement.setString(start + 11, result.interruptionReason()); } statement.setInt(start + 12, value.retryCount()); }
    private static MovementResolutionOperation read(ResultSet row) throws SQLException {
        List<GridPosition> requested = decode(row.getString("requested_path")); List<GridPosition> traversed = decode(row.getString("traversed_path"));
        MovementOperationStatus status = MovementOperationStatus.valueOf(row.getString("status"));
        long expectedVersion = row.getLong("expected_version");
        MovementResolutionResult result = row.getObject("result_final_x") != null
                ? new MovementResolutionResult(new MovementPath(requested, row.getInt("path_distance")), decode(row.getString("result_traversed_path")),
                        new GridPosition(row.getInt("result_final_x"), row.getInt("result_final_y")), row.getLong("result_map_version"),
                        row.getString("result_public_events") == null || row.getString("result_public_events").isEmpty() ? List.of() : List.of(row.getString("result_public_events").split("\\u001f")), row.getString("result_interruption_reason"), resultStatus(row))
                : null;
        UUID operationId = (UUID) row.getObject("operation_id");
        UUID ownerPlayerId = row.getObject("pending_owner_player_id", UUID.class);
        if (ownerPlayerId == null) ownerPlayerId = row.getObject("player_id", UUID.class);
        MovementCheckRequest pending = row.getObject("pending_check_id") == null ? null : new MovementCheckRequest(
                (UUID) row.getObject("pending_check_id"), operationId, (UUID) row.getObject("pending_feature_id"),
                com.dndmaster.combatmap.domain.SpatialFeatureType.valueOf(row.getString("pending_feature_type")),
                com.dndmaster.combatmap.domain.SpatialTrigger.valueOf(row.getString("pending_trigger")),
                row.getString("pending_rule_reference"), row.getString("pending_dice_expression"),
                row.getObject("pending_modifier", Integer.class) == null ? 0 : row.getInt("pending_modifier"),
                row.getObject("pending_difficulty", Integer.class),
                row.getString("pending_mode"), new MovementCheckOwner(
                        MovementCheckActor.valueOf(row.getString("pending_owner_actor") == null
                                ? row.getString("pending_ownership") : row.getString("pending_owner_actor")), new PlayerId(ownerPlayerId)));
        List<MovementCheckOutcome> outcomes = decodeOutcomes(row.getString("check_outcomes"));
        return MovementResolutionOperation.restore(operationId, new MapId((UUID) row.getObject("map_id")), (UUID) row.getObject("command_id"), new PlayerId((UUID) row.getObject("player_id")), new TokenId((UUID) row.getObject("token_id")), new MovementPath(requested, row.getInt("path_distance")), row.getString("fingerprint"), expectedVersion, status, row.getInt("cursor"), new GridPosition(row.getInt("current_x"), row.getInt("current_y")), traversed, result, row.getInt("retry_count"), row.getLong("operation_version"), MovementOperationStatus.valueOf(row.getString("retry_resume_status")), pending, outcomes);
    }
    private static MovementResolutionOutcomeStatus resultStatus(ResultSet row) throws SQLException {
        String status = row.getString("result_status");
        if (status != null && !status.isBlank()) return MovementResolutionOutcomeStatus.valueOf(status);
        return row.getString("result_interruption_reason") == null
                ? MovementResolutionOutcomeStatus.COMMITTED : MovementResolutionOutcomeStatus.INTERRUPTED;
    }
    private static String encode(List<GridPosition> positions) { return positions.stream().map(p -> p.x() + "," + p.y()).collect(java.util.stream.Collectors.joining(";")); }
    private static List<GridPosition> decode(String encoded) { return encoded == null || encoded.isEmpty() ? List.of() : java.util.Arrays.stream(encoded.split(";")).map(pair -> pair.split(",")).map(pair -> new GridPosition(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]))).toList(); }
    private static List<MovementCheckOutcome> decodeOutcomes(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        return java.util.Arrays.stream(encoded.split(";"))
                .map(value -> value.split(",", -1))
                .map(value -> value.length == 6
                        ? new MovementCheckOutcome(UUID.fromString(value[0]), UUID.fromString(value[1]), UUID.fromString(value[2]),
                                new MovementCheckOwner(MovementCheckActor.valueOf(value[3]), new PlayerId(UUID.fromString(value[4]))),
                                Boolean.parseBoolean(value[5]))
                        : new MovementCheckOutcome(UUID.fromString(value[0].split("=", 2)[0]),
                                Boolean.parseBoolean(value[0].split("=", 2)[1])))
                .toList();
    }
}
