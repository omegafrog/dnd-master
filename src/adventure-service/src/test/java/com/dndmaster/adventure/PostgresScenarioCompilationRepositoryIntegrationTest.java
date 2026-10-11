package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationInputSnapshot;
import com.dndmaster.adventure.domain.scenario.ScenarioCreativity;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.BasicRulebookSpellInventory;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationDiagnostic;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
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
    void persists_structured_agent_failure_diagnostics_with_terminal_status() {
        ScenarioCompilation requested = ScenarioCompilation.request(bundleId, 1, "diagnostic-fingerprint");
        repository.save(requested);
        UUID lease = UUID.randomUUID();
        ScenarioCompilation claimed = requested.claim(lease);
        assertTrue(repository.saveIfLeaseMatches(claimed, null));

        ScenarioCompilation failed = claimed.fail(lease, "scenario compilation agent failed",
                List.of(ScenarioCompilationDiagnostic.blocking("AI_EXECUTION_CONNECTION_UNAVAILABLE",
                        "safe error code and correlation", "AiExecutionUnavailableException")));
        assertTrue(repository.saveIfLeaseMatches(failed, lease));

        ScenarioCompilation reloaded = repository.findById(requested.id()).orElseThrow();
        assertEquals(ScenarioCompilationStatus.FAILED, reloaded.status());
        assertEquals("AI_EXECUTION_CONNECTION_UNAVAILABLE", reloaded.diagnostics().getFirst().code());
        assertEquals("AiExecutionUnavailableException", reloaded.diagnostics().getFirst().rootCauseClass());
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

    @Test
    void persists_and_reloads_the_complete_spell_inventory_with_its_sources_and_owners() {
        var packageVersion = ScenarioPackage.publish(bundleId, 1, "spell-inventory-fingerprint",
                List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()));
        var packages = new PostgresScenarioPackageRepository(dataSource);
        var spells = BasicRulebookSpellInventory.load(
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(
                        UUID.fromString("55555555-5555-5555-5555-555555555555")), 7);
        var first = spells.getFirst();
        var edited = new StructuredSpellDefinition(first.id(), first.name(), first.sourceDocumentId(), first.sourceLocator(),
                first.sourceVersion(), first.extractionVersion(), first.level(), first.castingTime(), first.rangeArea(),
                first.components(), first.duration(), first.school(), "사용자 수정 판정", "사용자 수정 효과",
                first.ownerPlanNumbers(), first.ownerEvidence(), first.executable(), first.reviewStatus());
        packages.save(packageVersion);
        var repaired = new java.util.ArrayList<>(spells);
        repaired.set(0, edited);
        var preparation = packages.createSpellDefinitionPreparation(packageVersion.packageId(), UUID.randomUUID(),
                UUID.randomUUID().toString(), repaired);
        var reloaded = packages.findSpellDefinitionPreparation(preparation.preparationId()).orElseThrow().definitions();
        assertEquals(126, reloaded.size());
        assertEquals(repaired, reloaded);
        assertEquals(List.of(), packages.findById(packageVersion.packageId()).orElseThrow().spellDefinitions(),
                "review edits belong to the preparation and must not mutate the shared package");
    }

    @Test
    void updates_spell_review_with_expected_revision_and_keeps_edit_history() {
        var packageVersion = ScenarioPackage.publish(bundleId, 1, "spell-review-revision",
                List.of(), List.of(), new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()));
        var packages = new PostgresScenarioPackageRepository(dataSource);
        var original = BasicRulebookSpellInventory.load(
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()), 7).getFirst();
        packages.save(packageVersion);
        var preparation = packages.createSpellDefinitionPreparation(packageVersion.packageId(), UUID.randomUUID(),
                UUID.randomUUID().toString(), List.of(original));
        var edited = original.edit(original.level(), original.castingTime(), original.rangeArea(), original.components(),
                original.duration(), original.school(), "사용자 수정 내성", original.damageEffect());
        var history = new com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory(1, original.id(),
                UUID.randomUUID(), java.time.Instant.now(), original, edited);

        assertEquals(1, packages.updateSpellDefinitionPreparation(preparation.preparationId(), 0, List.of(edited), history));
        assertEquals(edited, packages.findSpellDefinitionPreparation(preparation.preparationId()).orElseThrow().definitions().getFirst());
        assertEquals(history.previous(), packages.spellDefinitionPreparationHistory(preparation.preparationId()).getFirst().previous());
        assertThrows(com.dndmaster.adventure.domain.scenario.SpellDefinitionRevisionConflictException.class,
                () -> packages.updateSpellDefinitionPreparation(preparation.preparationId(), 0, List.of(original), history));
        assertEquals(edited, packages.findSpellDefinitionPreparation(preparation.preparationId()).orElseThrow().definitions().getFirst());
        var approved = edited.approve();
        var approval = new com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory(2, original.id(),
                UUID.randomUUID(), java.time.Instant.now(), edited, approved);
        assertEquals(2, packages.updateSpellDefinitionPreparation(preparation.preparationId(), 1, List.of(approved), approval));
        UUID sessionId = UUID.randomUUID();
        packages.bindSpellDefinitionPreparation(preparation.preparationId(), packageVersion.packageId(),
                preparation.ownerId(), sessionId, 2);
        var bound = packages.findSpellDefinitionPreparationBySession(sessionId).orElseThrow();
        assertFalse(bound.locked(), "binding a preparation to a session must leave it editable until adventure start");
        var revised = approved.edit(approved.level(), approved.castingTime(), approved.rangeArea(), approved.components(),
                approved.duration(), approved.school(), "재검토 내성", approved.damageEffect());
        var revisionThreeHistory = new com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory(3, original.id(),
                UUID.randomUUID(), java.time.Instant.now(), approved, revised);
        assertEquals(3, packages.updateSpellDefinitionPreparation(preparation.preparationId(), 2, List.of(revised), revisionThreeHistory));
        var revisedApproved = revised.approve();
        var revisionFourHistory = new com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory(4, original.id(),
                UUID.randomUUID(), java.time.Instant.now(), revised, revisedApproved);
        assertEquals(4, packages.updateSpellDefinitionPreparation(preparation.preparationId(), 3, List.of(revisedApproved), revisionFourHistory));
        packages.lockSpellDefinitionPreparationForStart(preparation.preparationId(), packageVersion.packageId(),
                preparation.ownerId(), sessionId, 4);
        assertThrows(IllegalStateException.class,
                () -> packages.updateSpellDefinitionPreparation(preparation.preparationId(), 4, List.of(edited), approval));
        assertTrue(packages.findSpellDefinitionPreparationBySession(sessionId).orElseThrow().locked());
        assertEquals(revisedApproved, packages.findSpellDefinitionPreparation(preparation.preparationId()).orElseThrow().definitions().getFirst());
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
