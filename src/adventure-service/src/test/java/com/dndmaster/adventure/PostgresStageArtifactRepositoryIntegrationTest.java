package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresStageArtifactRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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

class PostgresStageArtifactRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure").withUsername("adventure").withPassword("adventure");
    private static DataSource dataSource;
    private UUID packageId;
    private StageArtifactRepository repository;
    private PostgresAdventureRepository adventures;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @BeforeEach
    void setup() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE adventure CASCADE");
            statement.execute("TRUNCATE scenario_package, scenario_source_bundle CASCADE");
        }
        UUID bundleId = UUID.randomUUID();
        packageId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scenario_source_bundle(bundle_id, owner_player_id, current_revision, name, rulebook_edition) VALUES (?, ?, 1, 'Test adventure', 'DND_5E_2014')")) {
            statement.setObject(1, bundleId); statement.setObject(2, UUID.randomUUID()); statement.executeUpdate();
        }
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scenario_source_bundle_revision(bundle_id, revision_number) VALUES (?, 1)")) {
            statement.setObject(1, bundleId); statement.executeUpdate();
        }
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scenario_package(package_id, bundle_id, bundle_revision, input_fingerprint, report_status) VALUES (?, ?, 1, ?, 'COMPLETE')")) {
            statement.setObject(1, packageId); statement.setObject(2, bundleId); statement.setString(3, UUID.randomUUID().toString()); statement.executeUpdate();
        }
        repository = new PostgresStageArtifactRepository(dataSource);
        adventures = new PostgresAdventureRepository(dataSource);
    }

    @Test
    void keeps_same_source_preparations_independent_and_rolls_back_a_partial_initial_set() {
        AdventureId first = reserveAdventure();
        AdventureId second = reserveAdventure();
        var source = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        StageBackbone firstBackbone = new StageBackbone(packageId, 1,
                backbone("first adventure").stages(), List.of(),
                List.of(new RequiredSituation("stage-1-situation", "Find the beginning", List.of(), List.of(source))));
        StageBackbone secondBackbone = backbone("second adventure");

        repository.saveInitialArtifacts(first, firstBackbone, stages("first adventure"));
        repository.saveInitialArtifacts(second, secondBackbone, stages("second adventure"));

        assertEquals("first adventure", repository.findBackbone(first, packageId, 1).orElseThrow()
                .stages().getFirst().coreProblem());
        assertEquals("second adventure", repository.findBackbone(second, packageId, 1).orElseThrow()
                .stages().getFirst().coreProblem());
        assertEquals(firstBackbone.requiredSituations(), repository.findBackbone(first, packageId, 1).orElseThrow().requiredSituations());
        assertEquals(List.of(), repository.findBackbone(second, packageId, 1).orElseThrow().requiredSituations());
        assertEquals(1, repository.findDetailedStage(first, packageId, "stage-2", 1, 1).stream().count());

        AdventureId failed = reserveAdventure();
        assertThrows(RuntimeException.class, () -> repository.saveInitialArtifacts(failed, backbone("failed"),
                List.of(stage("stage-1", "failed"), stage("stage-1", "failed"))));
        assertTrue(repository.findBackbone(failed, packageId, 1).isEmpty());
        assertTrue(repository.findDetailedStage(failed, packageId, "stage-1", 1, 1).isEmpty());
    }

    private AdventureId reserveAdventure() {
        AdventureId id = AdventureId.generate();
        adventures.save(Adventure.create(id, SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("start", null, null, null)));
        return id;
    }

    private StageBackbone backbone(String text) {
        return new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", text, "goal", List.of()),
                new StageBackboneEntry("stage-2", 2, "later", text, "goal", List.of())), List.of());
    }

    private List<DetailedStage> stages(String text) { return List.of(stage("stage-1", text), stage("stage-2", text)); }

    private DetailedStage stage(String id, String text) {
        return new DetailedStage(packageId, 1, id, 1, text,
                List.of(new RevelationDefinition(id + "-revelation", true, List.of())),
                new ThreatDefinition("threat", List.of()), new PressureDefinition("pressure", text, List.of()),
                new FunnelDefinition("funnel", text, List.of(id + "-revelation"), List.of(), List.of()),
                List.of(new SituationDefinition(id + "-situation", List.of(StageIntent.REVELATION),
                        List.of(id + "-revelation"), List.of(), List.of(), List.of(), List.of(), id.equals("stage-1"), List.of())),
                List.of(), List.of());
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
