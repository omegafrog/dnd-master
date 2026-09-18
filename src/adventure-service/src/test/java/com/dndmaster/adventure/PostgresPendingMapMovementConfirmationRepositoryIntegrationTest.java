package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation;
import com.dndmaster.adventure.infrastructure.persistence.PostgresPendingMapMovementConfirmationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class PostgresPendingMapMovementConfirmationRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure").withUsername("adventure").withPassword("adventure");
    private static DataSource dataSource;
    private PostgresPendingMapMovementConfirmationRepository repository;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE adventure_pending_map_movement_confirmation");
        }
        repository = new PostgresPendingMapMovementConfirmationRepository(dataSource, new ObjectMapper());
    }

    @Test
    void survives_repository_reload_and_is_scoped_to_the_adventure_owner() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PendingMapMovementConfirmation confirmation = new PendingMapMovementConfirmation(adventureId, ownerId,
                UUID.randomUUID(), UUID.randomUUID(), 7,
                List.of(new PendingMapMovementConfirmation.Position(1, 1), new PendingMapMovementConfirmation.Position(2, 1)),
                5, "preview-fingerprint", List.of(new PendingMapMovementConfirmation.Position(1, 1)));

        repository.save(confirmation);
        var reloaded = new PostgresPendingMapMovementConfirmationRepository(dataSource, new ObjectMapper())
                .findByAdventureId(adventureId, ownerId).orElseThrow();

        assertEquals(confirmation, reloaded);
        assertTrue(repository.findByAdventureId(adventureId, UUID.randomUUID()).isEmpty());
        repository.deleteByAdventureId(adventureId, ownerId);
        assertTrue(repository.findByAdventureId(adventureId, ownerId).isEmpty());
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
