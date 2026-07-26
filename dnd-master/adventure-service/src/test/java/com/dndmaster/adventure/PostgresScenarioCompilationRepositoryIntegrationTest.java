package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationStatus;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioCompilationRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresScenarioCompilationRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure")
            .withUsername("adventure")
            .withPassword("adventure");

    private static DataSource dataSource;
    private PostgresScenarioCompilationRepository repository;
    private ScenarioBundleId bundleId;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE scenario_compilation, scenario_source_bundle CASCADE");
        }
        bundleId = new ScenarioBundleId(UUID.randomUUID());
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scenario_source_bundle(bundle_id, owner_player_id, current_revision) VALUES (?, ?, 1)")) {
            statement.setObject(1, bundleId.value());
            statement.setObject(2, UUID.randomUUID());
            statement.executeUpdate();
        }
        repository = new PostgresScenarioCompilationRepository(dataSource);
    }

    @Test
    void claimsCompilationWithNoExistingLease() {
        ScenarioCompilation requested = ScenarioCompilation.request(bundleId, 1, "initial-lease-fingerprint");
        repository.save(requested);
        ScenarioCompilation claimed = requested.claim(UUID.randomUUID());

        assertTrue(repository.saveIfLeaseMatches(claimed, null));
        assertEquals(ScenarioCompilationStatus.RUNNING, repository.findById(claimed.id()).orElseThrow().status());
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
