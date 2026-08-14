package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.infrastructure.persistence.PostgresScenarioBundleRepository;
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

class PostgresScenarioBundleRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure")
            .withUsername("adventure")
            .withPassword("adventure");
    private static DataSource dataSource;
    private ScenarioBundleId bundleId;
    private OwnerPlayerId owner;
    private PostgresScenarioBundleRepository repository;

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
            statement.execute("TRUNCATE scenario_package, scenario_source_bundle CASCADE");
        }
        bundleId = ScenarioBundleId.generate();
        owner = new OwnerPlayerId(UUID.randomUUID());
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scenario_source_bundle(bundle_id, owner_player_id, current_revision, name, rulebook_edition) VALUES (?, ?, 1, 'Test adventure', 'DND_5E_2014')")) {
            statement.setObject(1, bundleId.value());
            statement.setObject(2, owner.value());
            statement.executeUpdate();
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO scenario_source_bundle_revision(bundle_id, revision_number) VALUES (?, 1)")) {
            statement.setObject(1, bundleId.value());
            statement.executeUpdate();
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO scenario_source_bundle_revision_document(bundle_id, revision_number, selection_order, knowledge_document_id, document_type, original_filename, document_role, knowledge_document_status, extraction_version) VALUES (?, 1, 0, ?, 'STORYBOOK', 'old.pdf', 'MAIN_SCENARIO', 'INDEXED', 1)")) {
            statement.setObject(1, bundleId.value());
            statement.setObject(2, UUID.randomUUID());
            statement.executeUpdate();
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO scenario_package(package_id, bundle_id, bundle_revision, input_fingerprint, report_status) VALUES (?, ?, 1, ?, 'COMPLETE')")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, bundleId.value());
            statement.setString(3, UUID.randomUUID().toString());
            statement.executeUpdate();
        }
        repository = new PostgresScenarioBundleRepository(dataSource);
    }

    @Test
    void savesNewRevisionWithoutDeletingPackagesReferencingBundle() {
        ScenarioSourceBundle existing = repository.findById(bundleId).orElseThrow();
        ScenarioSourceBundle revised = existing.revise(new ScenarioSourceBundleRevision(2, java.util.List.of(
                new ScenarioBundleDocumentSelection(
                        new KnowledgeDocumentId(UUID.randomUUID()), ScenarioBundleDocumentRole.MAIN_SCENARIO,
                        KnowledgeDocumentStatus.INDEXED, "scenario.pdf", "STORYBOOK", 1))));

        repository.save(revised);

        ScenarioSourceBundle loaded = repository.findById(bundleId).orElseThrow();
        assertEquals(2, loaded.currentRevision().revision());
        assertEquals(2, loaded.revisions().size());
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
