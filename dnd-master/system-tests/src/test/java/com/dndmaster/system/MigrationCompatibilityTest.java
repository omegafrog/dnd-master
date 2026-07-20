package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class MigrationCompatibilityTest {
    private static final DockerImageName PGVECTOR = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Test
    void preservesStateAllowsPreviousAppRollbackAndRebuildsVectorIndex() throws Exception {
        try (var postgres = new PostgreSQLContainer<>(PGVECTOR)
                .withDatabaseName("migration_compatibility")
                .withUsername("migration_owner")
                .withPassword("migration-password")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            String location = "filesystem:" + Path.of(System.getProperty("dnd.migration.location"))
                    .toAbsolutePath().normalize().toString().replace('\\', '/');

            Flyway v1 = Flyway.configure().dataSource(dataSource).locations(location).target("1").load();
            assertEquals(1, v1.migrate().migrationsExecuted);
            var previousApp = new PreviousVersionApp(dataSource);
            UUID adventureId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID rulebookId = UUID.randomUUID();
            UUID chunkId = UUID.randomUUID();
            previousApp.createAdventure(adventureId, ownerId, "sealed crypt");
            previousApp.storeRuleChunk(chunkId, ownerId, rulebookId, "PHB p.173", "advantage rule");

            Flyway current = Flyway.configure().dataSource(dataSource).locations(location).load();
            assertEquals(1, current.migrate().migrationsExecuted);
            current.validate();
            var currentApp = new CurrentVersionApp(dataSource);

            assertEquals("sealed crypt", currentApp.scene(adventureId));
            assertNull(currentApp.contextJson(adventureId));
            assertEquals("advantage rule", currentApp.ruleContent(chunkId));
            assertEquals(4, currentApp.rebuiltVectorDimension(chunkId));
            assertEquals(1, currentApp.currentSearchRows());
            assertTrue(currentApp.hasVectorIndex("compat_rulebook_vector_v2_cosine_idx"));

            // Deploying the previous application binary against the expanded schema is the rollback path.
            previousApp.updateScene(adventureId, "crypt exit");
            assertEquals("crypt exit", previousApp.scene(adventureId));
            assertEquals("crypt exit", currentApp.scene(adventureId));
            currentApp.updateContext(adventureId, "{\"light\":\"torch\"}");
            assertEquals("crypt exit", previousApp.scene(adventureId));
            assertEquals("torch", currentApp.contextLight(adventureId));

            assertEquals(0, current.migrate().migrationsExecuted, "repeat migration must be idempotent");
            assertEquals(2, currentApp.appliedMigrationCount());
        }
    }

    private record PreviousVersionApp(DataSource dataSource) {
        void createAdventure(UUID adventureId, UUID ownerId, String scene) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO compat_adventure(adventure_id,owner_player_id,current_scene,status) VALUES (?,?,?,'SAVED')")) {
                statement.setObject(1, adventureId);
                statement.setObject(2, ownerId);
                statement.setString(3, scene);
                statement.executeUpdate();
            }
        }

        void storeRuleChunk(UUID chunkId, UUID ownerId, UUID rulebookId, String locator, String content)
                throws SQLException {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement source = connection.prepareStatement(
                                "INSERT INTO compat_rulebook_source(chunk_id,owner_player_id,rulebook_id,locator,content) VALUES (?,?,?,?,?)");
                        PreparedStatement vector = connection.prepareStatement(
                                "INSERT INTO compat_rulebook_vector_v1(chunk_id,embedding) VALUES (?,CAST('[1,0,0]' AS vector))")) {
                    source.setObject(1, chunkId);
                    source.setObject(2, ownerId);
                    source.setObject(3, rulebookId);
                    source.setString(4, locator);
                    source.setString(5, content);
                    source.executeUpdate();
                    vector.setObject(1, chunkId);
                    vector.executeUpdate();
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        }

        void updateScene(UUID adventureId, String scene) throws SQLException {
            executeUpdate("UPDATE compat_adventure SET current_scene=? WHERE adventure_id=?", scene, adventureId);
        }

        String scene(UUID adventureId) throws SQLException {
            return queryString("SELECT current_scene FROM compat_adventure WHERE adventure_id=?", adventureId);
        }

        private void executeUpdate(String sql, String value, UUID id) throws SQLException {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                statement.setObject(2, id);
                statement.executeUpdate();
            }
        }

        private String queryString(String sql, UUID id) throws SQLException {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    return result.getString(1);
                }
            }
        }
    }

    private record CurrentVersionApp(DataSource dataSource) {
        String scene(UUID id) throws SQLException { return string("SELECT current_scene FROM compat_adventure WHERE adventure_id=?", id); }
        String contextJson(UUID id) throws SQLException { return string("SELECT context_json::text FROM compat_adventure WHERE adventure_id=?", id); }
        String ruleContent(UUID id) throws SQLException { return string("SELECT content FROM compat_rulebook_source WHERE chunk_id=?", id); }
        int rebuiltVectorDimension(UUID id) throws SQLException { return integer("SELECT vector_dims(embedding) FROM compat_rulebook_vector_v2 WHERE chunk_id=?", id); }
        int currentSearchRows() throws SQLException { return integer("SELECT count(*) FROM compat_rulebook_search_current", null); }
        int appliedMigrationCount() throws SQLException { return integer("SELECT count(*) FROM flyway_schema_history WHERE success", null); }
        boolean hasVectorIndex(String name) throws SQLException {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM pg_indexes WHERE indexname=?")) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1) == 1; }
            }
        }
        void updateContext(UUID id, String json) throws SQLException {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                    "UPDATE compat_adventure SET context_json=CAST(? AS jsonb) WHERE adventure_id=?")) {
                statement.setString(1, json);
                statement.setObject(2, id);
                statement.executeUpdate();
            }
        }
        String contextLight(UUID id) throws SQLException { return string("SELECT context_json->>'light' FROM compat_adventure WHERE adventure_id=?", id); }
        private String string(String sql, UUID id) throws SQLException {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getString(1); }
            }
        }
        private int integer(String sql, UUID id) throws SQLException {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                if (id != null) statement.setObject(1, id);
                try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getInt(1); }
            }
        }
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, username, password); }
        @Override public Connection getConnection(String user, String pass) throws SQLException { return DriverManager.getConnection(url, user, pass); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("unwrap unsupported"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
