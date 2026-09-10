package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.RuntimeBindingApplicationService;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.session.AdventureSessionStartCoordinator;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStatus;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureSessionRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class AdventureSessionDeletionPostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure")
            .withUsername("adventure")
            .withPassword("adventure");
    private static DataSource dataSource;

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
            statement.execute("TRUNCATE adventure_session_character_sheet_deletion_outbox, adventure_session, adventure CASCADE");
        }
    }

    @Test
    void deletesRuntimeAdventureAndQueuesCharacterSheetCleanupInTheSameTransaction() throws SQLException {
        Fixture fixture = seedFixture();
        new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .executeWithoutResult(status -> fixture.service().delete(fixture.sessionId(), fixture.owner(), 0));

        assertEquals(AdventureSession.Status.DELETED, fixture.sessions().findById(fixture.sessionId()).orElseThrow().status());
        assertEquals(AdventureStatus.DELETED, fixture.adventures().findById(fixture.adventureId()).orElseThrow().status());
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT character_sheet_ids_json::text FROM adventure_session_character_sheet_deletion_outbox WHERE session_id=? AND status='PENDING'")) {
            statement.setObject(1, fixture.sessionId().value());
            try (ResultSet rows = statement.executeQuery()) {
                assertEquals(true, rows.next());
                assertEquals("[\"" + fixture.sheetId().value() + "\"]", rows.getString(1));
                assertEquals(false, rows.next());
            }
        }
    }

    @Test
    void rollsBackRuntimeAdventureWhenSessionDeletionFails() throws SQLException {
        Fixture fixture = seedFixture();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE OR REPLACE FUNCTION test_fail_session_delete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test session delete failure'; END $$");
            statement.execute("CREATE TRIGGER test_fail_session_delete_trigger BEFORE UPDATE OF status ON adventure_session FOR EACH ROW EXECUTE FUNCTION test_fail_session_delete()");
        }
        try {
            assertThrows(RuntimeException.class, () -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> fixture.service().delete(fixture.sessionId(), fixture.owner(), 0)));
        } finally {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS test_fail_session_delete_trigger ON adventure_session");
                statement.execute("DROP FUNCTION IF EXISTS test_fail_session_delete()");
            }
        }

        assertEquals(AdventureSession.Status.STARTING, fixture.sessions().findById(fixture.sessionId()).orElseThrow().status());
        assertEquals(AdventureStatus.STARTING, fixture.adventures().findById(fixture.adventureId()).orElseThrow().status());
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM adventure_session_character_sheet_deletion_outbox WHERE session_id=?")) {
            statement.setObject(1, fixture.sessionId().value());
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertEquals(0, rows.getLong(1));
            }
        }
    }

    private Fixture seedFixture() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        SessionId sessionId = SessionId.generate();
        UUID packageId = UUID.randomUUID();
        var adventureId = com.dndmaster.adventure.domain.adventure.AdventureId.generate();
        CharacterSheetId sheetId = new CharacterSheetId(UUID.randomUUID());
        AdventurePartyMember member = new AdventurePartyMember(sheetId, ControlMode.DIRECT, true, true, true, true, true, true);
        AdventureSession session = AdventureSession.rehydrate(sessionId, owner, packageId, 1, packageId, 1,
                "DND_5E_2014", 1, List.of(member), null, AdventureSession.Status.STARTING, adventureId,
                UUID.randomUUID(), 0);
        Adventure adventure = Adventure.beginScenarioRuntime(adventureId, sessionId, owner, new ScenarioId(packageId),
                new RuleSetId(UUID.randomUUID()), packageId, 1, List.of(member), new AdventureContext("opening", null, null, null));
        AdventureSessionRepository sessions = new PostgresAdventureSessionRepository(dataSource);
        AdventureRepository adventures = new PostgresAdventureRepository(dataSource);
        sessions.save(session, 0);
        adventures.save(adventure);

        AdventureSessionApplicationService service = new AdventureSessionApplicationService(
                sessions, org.mockito.Mockito.mock(ScenarioPackageRepository.class), adventures,
                org.mockito.Mockito.mock(RuntimeBindingApplicationService.class),
                org.mockito.Mockito.mock(AdventureSessionStartCoordinator.class));
        return new Fixture(owner, sessionId, adventureId, sheetId, sessions, adventures, service);
    }

    private record Fixture(OwnerPlayerId owner, SessionId sessionId,
            com.dndmaster.adventure.domain.adventure.AdventureId adventureId, CharacterSheetId sheetId,
            AdventureSessionRepository sessions, AdventureRepository adventures,
            AdventureSessionApplicationService service) {}

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
