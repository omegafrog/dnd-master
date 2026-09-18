package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.application.movement.MovementCommandConflictException;
import com.dndmaster.combatmap.application.movement.MovementOperationConcurrentUpdateException;
import com.dndmaster.combatmap.application.movement.MovementOperationStatus;
import com.dndmaster.combatmap.application.movement.MovementResolutionOperation;
import com.dndmaster.combatmap.application.movement.MovementResolutionResult;
import com.dndmaster.combatmap.application.movement.MovementReservationConflictException;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MovementPath;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.infrastructure.persistence.PostgresMovementResolutionOperationRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresMovementResolutionOperationRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("combat_map").withUsername("combat_map").withPassword("combat_map");
    private static DataSource dataSource;

    private MapId mapId;
    private PlayerId playerId;
    private TokenId tokenId;
    private UUID commandId;
    private MovementPath path;
    private PostgresMovementResolutionOperationRepository repository;

    @BeforeAll
    static void startPostgres() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE combat_map CASCADE");
        }
        mapId = new MapId(UUID.randomUUID());
        playerId = new PlayerId(UUID.randomUUID());
        tokenId = new TokenId(UUID.randomUUID());
        commandId = UUID.randomUUID();
        path = new MovementPath(List.of(
                new GridPosition(1, 1), new GridPosition(2, 1), new GridPosition(3, 1)), 10);
        repository = new PostgresMovementResolutionOperationRepository(dataSource);
        insertMap();
    }

    @Test
    void command_unique_race_replays_the_same_fingerprint_and_rejects_a_different_one() {
        MovementResolutionOperation winner = repository.reserve(operation(commandId, "same-fingerprint"));

        MovementResolutionOperation replay = repository.reserve(operation(commandId, "same-fingerprint"));

        assertEquals(winner.operationId(), replay.operationId());
        assertThrows(MovementCommandConflictException.class,
                () -> repository.reserve(operation(commandId, "different-fingerprint")));
    }

    @Test
    void active_map_unique_race_is_a_typed_reservation_conflict() {
        repository.reserve(operation(commandId, "first"));

        assertThrows(MovementReservationConflictException.class,
                () -> repository.reserve(operation(UUID.randomUUID(), "second")));
    }

    @Test
    void compare_and_set_zero_row_is_a_typed_concurrent_update() {
        MovementResolutionOperation inserted = repository.reserve(operation(commandId, "fingerprint"));
        MovementResolutionOperation first = repository.findById(inserted.operationId()).orElseThrow();
        MovementResolutionOperation stale = repository.findById(inserted.operationId()).orElseThrow();
        first.advanceTo(1, new GridPosition(2, 1));
        repository.save(first);
        stale.advanceTo(1, new GridPosition(2, 1));

        assertThrows(MovementOperationConcurrentUpdateException.class, () -> repository.save(stale));
    }

    @Test
    void retry_wait_round_trip_preserves_the_prepared_result_and_resume_state() {
        MovementResolutionOperation operation = operation(commandId, "fingerprint");
        operation.advanceTo(1, new GridPosition(2, 1));
        operation.advanceTo(2, new GridPosition(3, 1));
        MovementResolutionResult result = new MovementResolutionResult(
                path, operation.traversedPath(), operation.currentCell(), 1, List.of("movement-complete"), null);
        operation.readyToCommit(result);
        operation.retryWait(3);
        repository.reserve(operation);

        MovementResolutionOperation restored = repository.findById(operation.operationId()).orElseThrow();

        assertEquals(MovementOperationStatus.RETRY_WAIT, restored.status());
        assertEquals(MovementOperationStatus.READY_TO_COMMIT, restored.retryResumeStatus());
        assertEquals(result, restored.result());
    }

    @Test
    void stalled_recovery_excludes_recent_active_work_and_retry_wait() throws SQLException {
        MovementResolutionOperation preparing = repository.reserve(operation(commandId, "preparing"));
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "UPDATE combat_map_movement_operation SET updated_at=? WHERE operation_id=?")) {
            statement.setTimestamp(1, java.sql.Timestamp.from(Instant.parse("2026-09-18T00:00:00Z")));
            statement.setObject(2, preparing.operationId());
            statement.executeUpdate();
        }

        assertEquals(List.of(), repository.findStalledBefore(Instant.parse("2026-09-17T23:59:59Z")));
        assertEquals(List.of(preparing.operationId()), repository.findStalledBefore(
                Instant.parse("2026-09-18T00:00:01Z")).stream().map(MovementResolutionOperation::operationId).toList());

        preparing.retryWait(3);
        repository.save(preparing);
        assertEquals(List.of(), repository.findStalledBefore(Instant.parse("2030-01-01T00:00:00Z")));
    }

    private MovementResolutionOperation operation(UUID requestedCommandId, String fingerprint) {
        return MovementResolutionOperation.start(UUID.randomUUID(), mapId, requestedCommandId,
                playerId, tokenId, path, fingerprint, 0);
    }

    private void insertMap() throws SQLException {
        String sql = "INSERT INTO combat_map(map_id,owner_player_id,adventure_id,rule_set_id,grid_width,grid_height,cell_size,distance_unit,version) VALUES (?,?,?,?,?,?,?,?,0)";
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, mapId.value());
            statement.setObject(2, playerId.value());
            statement.setObject(3, UUID.randomUUID());
            statement.setObject(4, UUID.randomUUID());
            statement.setInt(5, 10);
            statement.setInt(6, 10);
            statement.setInt(7, 50);
            statement.setInt(8, 5);
            statement.executeUpdate();
        }
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
        @Override public Connection getConnection(String user, String pass) throws SQLException {
            return DriverManager.getConnection(url, user, pass);
        }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("unwrap unsupported"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
