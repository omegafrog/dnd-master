package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class SessionEventVersionMigrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure").withUsername("adventure").withPassword("adventure");
    private static DataSource dataSource;
    private static String schema;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        schema = "migration_" + UUID.randomUUID().toString().replace('-', '_');
        flyway("71").migrate();
    }

    @AfterAll
    static void stopDatabase() {
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            } catch (SQLException ignored) {
                // The container is disposable; cleanup failure must not hide the migration result.
            }
        }
        POSTGRES.stop();
    }

    @Test
    void V74_preserves_existing_versions_and_appends_legacy_duplicates_deterministically() throws SQLException {
        UUID session = UUID.randomUUID();
        UUID firstEvent = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID duplicateEvent = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID laterEvent = UUID.fromString("00000000-0000-0000-0000-000000000003");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + schema + ".adventure_session_event_outbox"
                    + "(event_id, session_id, version, event_type, payload) VALUES "
                    + "('" + firstEvent + "', '" + session + "', 4, 'FIRST', '{}'),"
                    + "('" + duplicateEvent + "', '" + session + "', 4, 'SECOND', '{}'),"
                    + "('" + laterEvent + "', '" + session + "', 7, 'THIRD', '{}')");
        }

        flyway("74").migrate();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            var rows = statement.executeQuery("SELECT event_id, version FROM " + schema
                    + ".adventure_session_event_outbox WHERE session_id = '" + session + "' ORDER BY version");
            assertTrue(rows.next());
            assertEquals(firstEvent, rows.getObject("event_id"));
            assertEquals(4, rows.getLong("version"));
            assertTrue(rows.next());
            assertEquals(laterEvent, rows.getObject("event_id"));
            assertEquals(7, rows.getLong("version"));
            assertTrue(rows.next());
            assertEquals(duplicateEvent, rows.getObject("event_id"));
            assertEquals(8, rows.getLong("version"));
            assertFalse(rows.next());

            rows = statement.executeQuery("SELECT next_version FROM " + schema
                    + ".adventure_session_event_version_counter WHERE session_id = '" + session + "'");
            assertTrue(rows.next());
            assertEquals(9, rows.getLong(1));

            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO " + schema
                    + ".adventure_session_event_outbox(event_id, session_id, version, event_type, payload) VALUES ('"
                    + UUID.randomUUID() + "', '" + session + "', 8, 'DUPLICATE', '{}')"));
        }
    }

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(target).load();
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
