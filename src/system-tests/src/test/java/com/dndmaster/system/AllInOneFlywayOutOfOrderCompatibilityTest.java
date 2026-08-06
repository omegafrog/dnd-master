package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class AllInOneFlywayOutOfOrderCompatibilityTest {
    private static final DockerImageName PGVECTOR = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");
    @Test
    void appAllCanRecoverWhenAdventure31ArrivesAfterHigherVersions() throws Exception {
        try (var postgres = new PostgreSQLContainer<>(PGVECTOR)
                .withDatabaseName("app_all_flyway")
                .withUsername("app_all")
                .withPassword("app_all")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

            List<MigrationInfo> resolvedMigrations = Arrays.stream(appAllFlyway(dataSource, false).info().all())
                    .filter(info -> info.getVersion() != null)
                    .toList();

            migrateModule(dataSource, "adventure-service", "2.1");
            seedHistoryRows(dataSource, resolvedMigrations, Set.of("1.1", "2.1", "3.1"));

            assertThrows(FlywayValidateException.class, () -> appAllFlyway(dataSource, false).migrate());

            assertEquals(1, appAllFlyway(dataSource, true).migrate().migrationsExecuted);
            assertTrue(tableExists(dataSource, "adventure_session_knowledge_document"));
        }
    }

    private static void migrateModule(DataSource dataSource, String module, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations(moduleLocation(module));
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void seedHistoryRows(
            DataSource dataSource, List<MigrationInfo> resolvedMigrations, Set<String> excludedVersions)
            throws SQLException {
        Set<String> existingVersions = Set.of("1.1", "2.1");
        try (Connection connection = dataSource.getConnection()) {
            int installedRank = nextInstalledRank(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO flyway_schema_history (
                        installed_rank, version, description, type, script, checksum, installed_by, execution_time, success
                    ) VALUES (?, ?, ?, ?, ?, ?, 'test', 0, true)
                    """)) {
                for (MigrationInfo migration : resolvedMigrations) {
                    String version = migration.getVersion().getVersion();
                    if (excludedVersions.contains(version) || existingVersions.contains(version)) {
                        continue;
                    }

                    statement.setInt(1, installedRank++);
                    statement.setString(2, version);
                    statement.setString(3, migration.getDescription());
                    statement.setString(4, migration.getType().name());
                    statement.setString(5, migration.getScript());
                    statement.setObject(6, migration.getChecksum());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    private static int nextInstalledRank(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history");
                ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static Flyway appAllFlyway(DataSource dataSource, boolean outOfOrder) {
        // app-all configures one Flyway history table per module. Combining all module
        // locations here creates false duplicate versions (for example V2_6 in
        // character-management and combat-map) that production never resolves together.
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(moduleLocation("adventure-service"))
                .outOfOrder(outOfOrder);
        return configuration.load();
    }

    private static String moduleLocation(String module) {
        return "filesystem:" + Path.of(System.getProperty("dnd.reactor.root"))
                .resolve(module)
                .resolve("src/main/resources/db/migration")
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');
    }

    private static boolean tableExists(DataSource dataSource, String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1) == 1;
            }
        }
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String suppliedUsername, String suppliedPassword) throws SQLException {
            return DriverManager.getConnection(url, suppliedUsername, suppliedPassword);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unwrap unsupported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
