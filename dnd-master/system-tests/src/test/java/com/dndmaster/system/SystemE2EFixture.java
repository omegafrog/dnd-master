package com.dndmaster.system;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

final class SystemE2EFixture {
    private static final DockerImageName PGVECTOR = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PGVECTOR)
            .withDatabaseName("dnd_master_e2e")
            .withUsername("dnd_master")
            .withPassword("dnd-master-test");
    private static final DataSource DATA_SOURCE;

    static {
        POSTGRES.start();
        DATA_SOURCE = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "stop-pgvector-e2e"));
        createSchema();
    }

    private SystemE2EFixture() {}

    static DataSource dataSource() {
        return DATA_SOURCE;
    }

    static synchronized void reset() {
        execute("TRUNCATE adventure, rulebook_vector_index, combat_map CASCADE");
    }

    private static void createSchema() {
        execute("CREATE EXTENSION IF NOT EXISTS vector");
        execute("""
                CREATE TABLE adventure (
                    adventure_id UUID PRIMARY KEY, session_id UUID NOT NULL UNIQUE,
                    owner_player_id UUID NOT NULL, scenario_id UUID NOT NULL,
                    rule_set_id UUID NOT NULL, character_sheet_id UUID NOT NULL,
                    current_scene TEXT NOT NULL, npc_state TEXT, pending_action TEXT,
                    latest_judgment TEXT, status TEXT NOT NULL CHECK (status IN ('SAVED', 'DELETED')),
                    version BIGINT NOT NULL CHECK (version >= 0)
                )
                """);
        execute("CREATE INDEX adventure_saved_owner_idx ON adventure(owner_player_id) WHERE status = 'SAVED'");
        execute("""
                CREATE TABLE adventure_conversation (
                    adventure_id UUID NOT NULL REFERENCES adventure(adventure_id) ON DELETE CASCADE,
                    sequence BIGINT NOT NULL CHECK (sequence >= 0), speaker TEXT NOT NULL,
                    content TEXT NOT NULL, PRIMARY KEY (adventure_id, sequence)
                )
                """);
        execute("""
                CREATE TABLE rulebook_vector_index (
                    index_id UUID PRIMARY KEY, rulebook_id UUID NOT NULL, owner_player_id UUID NOT NULL,
                    embedding_model TEXT NOT NULL, dimension INTEGER NOT NULL CHECK (dimension > 0),
                    index_version TEXT NOT NULL, status TEXT NOT NULL,
                    UNIQUE (rulebook_id, owner_player_id, embedding_model, index_version)
                )
                """);
        execute("""
                CREATE TABLE rulebook_vector_chunk (
                    chunk_id UUID PRIMARY KEY,
                    index_id UUID NOT NULL REFERENCES rulebook_vector_index(index_id) ON DELETE CASCADE,
                    rulebook_id UUID NOT NULL, owner_player_id UUID NOT NULL,
                    sequence INTEGER NOT NULL CHECK (sequence >= 0), locator TEXT NOT NULL,
                    content TEXT NOT NULL, embedding vector NOT NULL, UNIQUE (index_id, sequence)
                )
                """);
        execute("CREATE INDEX rulebook_vector_chunk_owner_rulebook_idx ON rulebook_vector_chunk(owner_player_id, rulebook_id)");
        execute("""
                CREATE TABLE combat_map (
                    map_id UUID PRIMARY KEY, owner_player_id UUID NOT NULL, adventure_id UUID NOT NULL,
                    rule_set_id UUID NOT NULL, grid_width INT NOT NULL, grid_height INT NOT NULL,
                    cell_size INT NOT NULL, distance_unit INT NOT NULL, version BIGINT NOT NULL
                )
                """);
        execute("""
                CREATE TABLE combat_map_token (
                    map_id UUID REFERENCES combat_map ON DELETE CASCADE, token_id UUID,
                    token_type TEXT NOT NULL, x INT NOT NULL, y INT NOT NULL, controller TEXT NOT NULL,
                    owner_player_id UUID, PRIMARY KEY(map_id, token_id)
                )
                """);
        execute("CREATE TABLE combat_map_obstacle(map_id UUID REFERENCES combat_map ON DELETE CASCADE, x INT, y INT, PRIMARY KEY(map_id,x,y))");
        execute("""
                CREATE TABLE combat_map_layer (
                    map_id UUID REFERENCES combat_map ON DELETE CASCADE, sequence INT,
                    layer_type TEXT NOT NULL, layer_value TEXT NOT NULL,
                    visibility TEXT NOT NULL CHECK(visibility IN('PLAYER_VISIBLE','AI_ONLY')),
                    PRIMARY KEY(map_id,sequence)
                )
                """);
    }

    private static void execute(String sql) {
        try (Connection connection = DATA_SOURCE.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("E2E database setup failed", exception);
        }
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
        @Override public Connection getConnection(String user, String suppliedPassword) throws SQLException {
            return DriverManager.getConnection(url, user, suppliedPassword);
        }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("unwrap unsupported"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
