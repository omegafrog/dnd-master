package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.infrastructure.persistence.PostgresActiveTacticalMapAdapter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresActiveTacticalMapAdapterIntegrationTest {
    @Test
    void lookupRequiresActiveBindingAndReturnsDeterministicCurrentMap() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE adventure_active_tactical_map(adventure_id UUID NOT NULL, stage_position INTEGER NOT NULL, owner_player_id UUID NOT NULL, combat_map_id UUID NOT NULL, active BOOLEAN NOT NULL)");
            }
            UUID adventure = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            UUID inactive = UUID.randomUUID();
            UUID active = UUID.randomUUID();
            try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement("INSERT INTO adventure_active_tactical_map VALUES (?, ?, ?, ?, ?)");) {
                statement.setObject(1, adventure); statement.setInt(2, 1); statement.setObject(3, owner); statement.setObject(4, inactive); statement.setBoolean(5, false); statement.addBatch();
                statement.setObject(4, active); statement.setBoolean(5, true); statement.addBatch(); statement.executeBatch();
            }
            var adapter = new PostgresActiveTacticalMapAdapter(dataSource);
            assertEquals(active, adapter.findActiveMap(adventure, 1, owner).orElseThrow());
            assertTrue(adapter.findActiveMap(adventure, 2, owner).isEmpty());
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
