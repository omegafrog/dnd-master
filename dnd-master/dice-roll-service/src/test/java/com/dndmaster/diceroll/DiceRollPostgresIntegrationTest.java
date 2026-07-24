package com.dndmaster.diceroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.diceroll.domain.*;
import com.dndmaster.diceroll.infrastructure.http.DeliveryStatus;
import com.dndmaster.diceroll.infrastructure.persistence.IdempotencyConflictException;
import com.dndmaster.diceroll.infrastructure.persistence.PostgresAdjudicationDeliveryStateStore;
import com.dndmaster.diceroll.infrastructure.persistence.PostgresDiceRollRepository;
import com.dndmaster.diceroll.infrastructure.random.SecureDiceRandomAdapter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class DiceRollPostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dice_roll").withUsername("dice_roll").withPassword("dice_roll");
    private static DataSource dataSource;

    @BeforeAll static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }
    @AfterAll static void stopDatabase() { POSTGRES.stop(); }

    @BeforeEach void clearDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE dice_roll CASCADE");
        }
    }

    @Test
    void secureRandomStaysWithinExclusiveBound() {
        SecureDiceRandomAdapter random = new SecureDiceRandomAdapter();
        for (int index = 0; index < 1_000; index++) {
            int value = random.nextInt(20);
            assertTrue(value >= 0 && value < 20);
        }
    }

    @Test
    void persistsAndRestoresCompletedRollInDedicatedDatabase() {
        PostgresDiceRollRepository repository = new PostgresDiceRollRepository(dataSource);
        DiceRoll roll = completedRoll(RollScope.SECRET_CHECK, List.of(4, 17), 2);
        repository.save(roll);

        DiceRoll restored = repository.findById(roll.id()).orElseThrow();

        assertEquals(roll.id(), restored.id());
        assertEquals(roll.scope(), restored.scope());
        assertEquals(roll.result(), restored.result());
    }

    @Test
    void findsRollByCommandIdAndReplaysSavedResult() {
        PostgresDiceRollRepository repository = new PostgresDiceRollRepository(dataSource);
        DiceRoll roll = completedRoll(RollScope.PLAYER_ACTION, List.of(10), 0);
        repository.save(roll);

        DiceRoll replayed = repository.findByCommandId(roll.commandId()).orElseThrow();

        assertEquals(roll, replayed);
    }

    @Test
    void deliveryStateRetriesFailedAttemptAndRejectsChangedPayloadForSameKey() {
        PostgresDiceRollRepository rolls = new PostgresDiceRollRepository(dataSource);
        DiceRoll roll = completedRoll(RollScope.ENEMY, List.of(9), 0);
        rolls.save(roll);
        PostgresAdjudicationDeliveryStateStore deliveries = new PostgresAdjudicationDeliveryStateStore(dataSource);

        var first = deliveries.begin("delivery-1", roll.id(), "hash-a");
        deliveries.markFailed("delivery-1", first.version(), "timeout");
        var retry = deliveries.begin("delivery-1", roll.id(), "hash-a");
        deliveries.markCompleted("delivery-1", retry.version());
        var duplicate = deliveries.begin("delivery-1", roll.id(), "hash-a");

        assertTrue(first.shouldDeliver());
        assertTrue(retry.shouldDeliver());
        assertNotEquals(first.version(), retry.version());
        assertEquals(DeliveryStatus.COMPLETED, duplicate.status());
        assertEquals(false, duplicate.shouldDeliver());
        assertThrows(
                IdempotencyConflictException.class,
                () -> deliveries.begin("delivery-1", roll.id(), "different-hash"));
    }

    private static DiceRoll completedRoll(RollScope scope, List<Integer> faces, int modifier) {
        DiceExpression expression = new DiceExpression(faces.size(), 20, modifier);
        UUID sessionId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        DiceRoll roll = new DiceRoll(
                RollId.generate(), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), scope, expression,
                sessionId, turnId, commandId, 0);
        roll.recordBuiltInResult(DiceResult.forExpression(expression, faces));
        return roll;
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, username, password); }
        @Override public Connection getConnection(String user, String pass) throws SQLException { return DriverManager.getConnection(url, user, pass); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("unwrap unsupported"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
