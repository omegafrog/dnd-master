package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.infrastructure.persistence.PostgresGmTurnRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresGmTurnConcurrencyIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure").withUsername("adventure").withPassword("adventure");
    private static DataSource dataSource;

    @BeforeAll static void start() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll static void stop() { POSTGRES.stop(); }

    @Test
    void advisory_lock_serializes_different_commands_for_same_adventure() throws Exception {
        UUID adventureId = UUID.randomUUID();
        PostgresGmTurnRepository repository = new PostgresGmTurnRepository(dataSource, new ObjectMapper());
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        var first = executor.submit(() -> transactions.execute(status -> {
            repository.lockAdventure(adventureId);
            firstLocked.countDown();
            await(releaseFirst);
            return null;
        }));
        assertTrue(firstLocked.await(5, TimeUnit.SECONDS));
        var second = executor.submit(() -> transactions.execute(status -> {
            repository.lockAdventure(adventureId);
            secondLocked.countDown();
            return null;
        }));

        assertFalse(secondLocked.await(300, TimeUnit.MILLISECONDS));
        releaseFirst.countDown();
        assertTrue(secondLocked.await(5, TimeUnit.SECONDS));
        first.get(); second.get(); executor.shutdownNow();
    }

    @Test
    void adventure_write_rolls_back_with_runtime_transaction() {
        PostgresAdventureRepository adventures = new PostgresAdventureRepository(dataSource);
        Adventure adventure = Adventure.create(AdventureId.generate(), SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("scene", null, null, null));
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        try {
            transactions.executeWithoutResult(status -> {
                adventures.save(adventure);
                throw new IllegalStateException("injected failure after local write");
            });
        } catch (IllegalStateException expected) { }

        assertTrue(adventures.findById(adventure.id()).isEmpty());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
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
