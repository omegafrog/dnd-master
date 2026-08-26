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
                statement.execute("CREATE TABLE combat_map(map_id UUID PRIMARY KEY)");
                statement.execute("CREATE TABLE adventure_active_tactical_map(adventure_id UUID NOT NULL, stage_position INTEGER NOT NULL, owner_player_id UUID NOT NULL, combat_map_id UUID NOT NULL REFERENCES combat_map(map_id), active BOOLEAN NOT NULL)");
            }
            UUID adventure = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            UUID inactive = UUID.randomUUID();
            UUID active = UUID.randomUUID();
            try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement("INSERT INTO adventure_active_tactical_map VALUES (?, ?, ?, ?, ?)");) {
                try (var map = connection.prepareStatement("INSERT INTO combat_map VALUES (?)")) {
                    map.setObject(1, inactive); map.addBatch(); map.setObject(1, active); map.addBatch(); map.executeBatch();
                }
                statement.setObject(1, adventure); statement.setInt(2, 1); statement.setObject(3, owner); statement.setObject(4, inactive); statement.setBoolean(5, false); statement.addBatch();
                statement.setObject(4, active); statement.setBoolean(5, true); statement.addBatch(); statement.executeBatch();
            }
            var adapter = new PostgresActiveTacticalMapAdapter(dataSource);
            assertEquals(active, adapter.findActiveMap(adventure, 1, owner).orElseThrow());
            assertTrue(adapter.findActiveMap(adventure, 2, owner).isEmpty());
        }
    }

    @Test
    void rebindingRollsBackDeactivationWhenNewBindingCannotBePersisted() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            UUID adventure = UUID.randomUUID(); UUID owner = UUID.randomUUID(); UUID oldMap = UUID.randomUUID();
            try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE combat_map(map_id UUID PRIMARY KEY)");
                statement.execute("CREATE TABLE adventure_active_tactical_map(adventure_id UUID NOT NULL, stage_position INTEGER NOT NULL, owner_player_id UUID NOT NULL, combat_map_id UUID NOT NULL REFERENCES combat_map(map_id), active BOOLEAN NOT NULL)");
                try (var insertMap = connection.prepareStatement("INSERT INTO combat_map VALUES (?)")) { insertMap.setObject(1, oldMap); insertMap.executeUpdate(); }
                try (var insertBinding = connection.prepareStatement("INSERT INTO adventure_active_tactical_map VALUES (?, ?, ?, ?, true)")) { insertBinding.setObject(1, adventure); insertBinding.setInt(2, 1); insertBinding.setObject(3, owner); insertBinding.setObject(4, oldMap); insertBinding.executeUpdate(); }
            }
            var adapter = new PostgresActiveTacticalMapAdapter(dataSource);
            UUID missingMap = UUID.randomUUID();
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> adapter.bindActiveMap(adventure, 2, owner, missingMap));
            assertEquals(oldMap, adapter.findActiveMap(adventure, 1, owner).orElseThrow());
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
