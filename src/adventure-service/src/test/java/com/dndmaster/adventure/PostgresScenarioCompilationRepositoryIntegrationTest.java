package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationInputSnapshot;
import com.dndmaster.adventure.domain.scenario.ScenarioCreativity;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioPackageRepository;
import java.util.List;
import java.util.Map;
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
                "INSERT INTO scenario_source_bundle(bundle_id, owner_player_id, current_revision, name, rulebook_edition) VALUES (?, ?, 1, 'Test adventure', 'DND_5E_2014')")) {
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

    @Test
    void publishesPackageModelJobAndOutboxAtomically() throws SQLException {
        UUID primary = UUID.randomUUID();
        ScenarioCompilationInputSnapshot input = new ScenarioCompilationInputSnapshot(bundleId, 1,
                List.of(new ScenarioCompilationInputSnapshot.StorybookInput(primary, 1,
                        ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK")), primary, "", ScenarioCreativity.NONE);
        ScenarioCompilation requested = ScenarioCompilation.request(input, "atomic-fingerprint", "atomic-key");
        repository.save(requested);
        UUID lease = UUID.randomUUID();
        ScenarioCompilation claimed = requested.claim(lease);
        assertTrue(repository.saveIfLeaseMatches(claimed, null));

        ScenarioModel model = new ScenarioModel(1, List.of(), List.of(),
                List.of(element("objective", "goal")), List.of(), List.of(), List.of(),
                List.of(element("resolution", "resolved")), "The adventure starts.");
        ScenarioPackage scenarioPackage = ScenarioPackage.publishWithScenarioModel(bundleId, 1, "atomic-package",
                List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                com.dndmaster.adventure.domain.scenario.CharacterLimit.defaultLimit(), null, List.of(), List.of(), model);
        PostgresScenarioPackageRepository packages = new PostgresScenarioPackageRepository(dataSource);

        assertTrue(packages.publishAtomically(scenarioPackage, claimed, List.of()));
        assertTrue(packages.findById(scenarioPackage.packageId()).orElseThrow().scenarioModel() != null);
        assertEquals(ScenarioCompilationStatus.COMPLETED, repository.findById(requested.id()).orElseThrow().status());
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM scenario_compilation_outbox WHERE compilation_id = ?")) {
            statement.setObject(1, requested.id());
            try (var rows = statement.executeQuery()) { rows.next(); assertEquals(1, rows.getInt(1)); }
        }
    }

    private static ScenarioModelElement element(String type, String value) {
        return new ScenarioModelElement(type, type, Map.of("value", value), List.of());
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
