package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeBindingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class RuntimeCompatibilityPostgresIntegrationTest {
    @Test
    void backfills_legacy_binding_scope_and_keeps_legacy_binding_readable() throws Exception {
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID characterSheetId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        List<UUID> expectedScope = List.of(firstDocumentId, secondDocumentId);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            String location = "classpath:db/migration";
            Flyway.configure().dataSource(dataSource).locations(location).target("20").load().migrate();
            insertLegacyAdventureAndBinding(
                    dataSource, adventureId, sessionId, ownerId, packageId, characterSheetId,
                    firstDocumentId, secondDocumentId);

            Flyway current = Flyway.configure().dataSource(dataSource).locations(location).load();
            assertEquals(2, current.migrate().migrationsExecuted);

            assertEquals(
                    expectedScope,
                    readScope(dataSource, sessionId));
            assertEquals(
                    expectedScope,
                    new PostgresRuntimeBindingRepository(dataSource, new ObjectMapper())
                            .findCurrentByAdventureId(new AdventureId(adventureId))
                            .orElseThrow()
                            .rulebookIds());
        }
    }

    private static void insertLegacyAdventureAndBinding(
            DataSource dataSource, UUID adventureId, UUID sessionId, UUID ownerId, UUID packageId,
            UUID characterSheetId, UUID firstDocumentId, UUID secondDocumentId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement adventure = connection.prepareStatement("""
                    INSERT INTO adventure(adventure_id, session_id, owner_player_id, scenario_id, rule_set_id,
                        current_scene, status, version)
                    VALUES (?, ?, ?, ?, ?, 'legacy scene', 'SAVED', 0)
                    """)) {
                adventure.setObject(1, adventureId);
                adventure.setObject(2, sessionId);
                adventure.setObject(3, ownerId);
                adventure.setObject(4, UUID.randomUUID());
                adventure.setObject(5, UUID.randomUUID());
                adventure.executeUpdate();
            }
            try (PreparedStatement binding = connection.prepareStatement("""
                    INSERT INTO adventure_runtime_binding(adventure_id, binding_version, owner_player_id,
                        scenario_package_id, scenario_package_revision, rulebook_ids_json,
                        party_json, engine_id, tool_ids_json, playability_status, playability_warnings_json,
                        playability_blockers_json, playability_limits_json, active_source_context_json,
                        source_context_candidates_json)
                    VALUES (?, 1, ?, ?, 1, ?, ?, 'engine', '[]', 'PLAYABLE', '[]', '[]', '[]', NULL, '[]')
                    """)) {
                binding.setObject(1, adventureId);
                binding.setObject(2, ownerId);
                binding.setObject(3, packageId);
                binding.setString(4, "[\"%s\",\"%s\"]".formatted(firstDocumentId, secondDocumentId));
                binding.setString(5, "[{\"characterSheetId\":\"%s\",\"controlMode\":\"DIRECT\",\"nameMutableAfterStart\":false,\"raceMutableAfterStart\":false,\"characterClassMutableAfterStart\":false,\"backgroundMutableAfterStart\":false,\"startingAbilitiesMutableAfterStart\":false,\"levelMutableAfterStart\":false}]".formatted(characterSheetId));
                binding.executeUpdate();
            }
        }
    }

    private static List<UUID> readScope(DataSource dataSource, UUID sessionId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT knowledge_document_id FROM adventure_session_knowledge_document
                        WHERE session_id = ? ORDER BY selection_order
                        """)) {
            statement.setObject(1, sessionId);
            try (var rows = statement.executeQuery()) {
                List<UUID> ids = new java.util.ArrayList<>();
                while (rows.next()) ids.add(rows.getObject(1, UUID.class));
                return ids;
            }
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
