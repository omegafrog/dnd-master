package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.*;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeCommandJournal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCommandJournalPostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure").withUsername("adventure").withPassword("adventure");
    private static DataSource dataSource;
    private final UUID session = UUID.randomUUID();
    private final UUID turn = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @BeforeAll static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }
    @AfterAll static void stopDatabase() { POSTGRES.stop(); }
    @BeforeEach void clear() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE adventure_runtime_command_journal");
        }
    }

    @Test
    void atomicClaimAndReloadSurviveServiceRestart() {
        ObjectMapper mapper = new ObjectMapper();
        RuntimeCommandRequest request = new RuntimeCommandRequest(UUID.randomUUID(), session, turn, owner, "dice.roll", "d20");
        var firstJournal = new PostgresRuntimeCommandJournal(dataSource, mapper);
        assertTrue(firstJournal.claim(new RuntimeCommandJournalEntry(request.commandId(), session, turn, owner, request.toolName(), request.fingerprint(), RuntimeCommandStatus.PENDING, null, 0)));
        assertFalse(new PostgresRuntimeCommandJournal(dataSource, mapper).claim(new RuntimeCommandJournalEntry(request.commandId(), session, turn, owner, request.toolName(), request.fingerprint(), RuntimeCommandStatus.PENDING, null, 0)));
        firstJournal.record(new RuntimeCommandJournalEntry(request.commandId(), session, turn, owner, request.toolName(), request.fingerprint(), RuntimeCommandStatus.APPLIED, RuntimeCommandOutcome.applied("20", 1), 1));

        RuntimeCommandSagaApplicationService restarted = new RuntimeCommandSagaApplicationService(new PostgresRuntimeCommandJournal(dataSource, mapper));
        AtomicInteger dispatches = new AtomicInteger();
        assertEquals("20", restarted.execute(request, ignored -> { dispatches.incrementAndGet(); return RuntimeCommandOutcome.applied("rerolled", 2); }).value());
        assertEquals(0, dispatches.get());
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, username, password); }
        public Connection getConnection(String user, String pass) throws SQLException { return DriverManager.getConnection(url, user, pass); }
        public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("unwrap unsupported"); }
        public boolean isWrapperFor(Class<?> iface) { return false; }
        public java.io.PrintWriter getLogWriter() { return null; }
        public void setLogWriter(java.io.PrintWriter out) {}
        public void setLoginTimeout(int seconds) {}
        public int getLoginTimeout() { return 0; }
        public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
