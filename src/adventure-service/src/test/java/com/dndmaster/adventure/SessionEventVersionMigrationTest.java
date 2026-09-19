package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void V72_resequences_legacy_duplicate_versions_before_restoring_atomic_uniqueness() throws SQLException {
        UUID session = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + schema + ".adventure_session_event_outbox"
                    + "(event_id, session_id, version, event_type, payload) VALUES "
                    + "('" + UUID.randomUUID() + "', '" + session + "', 4, 'FIRST', '{}'),"
                    + "('" + UUID.randomUUID() + "', '" + session + "', 4, 'SECOND', '{}'),"
                    + "('" + UUID.randomUUID() + "', '" + session + "', 7, 'THIRD', '{}')");
        }

        flyway("72").migrate();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            var rows = statement.executeQuery("SELECT version FROM " + schema
                    + ".adventure_session_event_outbox WHERE session_id = '" + session + "' ORDER BY version");
            long expected = 0;
            while (rows.next()) assertEquals(expected++, rows.getLong(1));
            assertEquals(3, expected);

            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO " + schema
                    + ".adventure_session_event_outbox(event_id, session_id, version, event_type, payload) VALUES ('"
                    + UUID.randomUUID() + "', '" + session + "', 1, 'DUPLICATE', '{}')"));
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
