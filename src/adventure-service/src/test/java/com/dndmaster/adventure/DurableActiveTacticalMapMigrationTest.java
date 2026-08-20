package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class DurableActiveTacticalMapMigrationTest {
    @Test
    void freshMigrationCreatesActiveBindingColumnBeforeRuntimeQueries() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("39").load().migrate();
            assertHasActiveColumn(dataSource, false);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            assertHasActiveColumn(dataSource, true);
        }
    }

    private static void assertHasActiveColumn(DataSource dataSource, boolean expected) throws SQLException {
            try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                    "SELECT 1 FROM information_schema.columns WHERE table_name = 'adventure_active_tactical_map' AND column_name = 'active'")) {
                try (var rows = statement.executeQuery()) { assertTrue(rows.next() == expected); }
            }
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
