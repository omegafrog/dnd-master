package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PersistenceCompatibilityTest {
    private static final List<ModuleSchema> MODULES = List.of(
            new ModuleSchema("identity-access-service", "identity_access"),
            new ModuleSchema("adventure-service", "adventure_contract"),
            new ModuleSchema("rule-knowledge-service", "rule_knowledge_contract"),
            new ModuleSchema("character-management-service", "character_management"),
            new ModuleSchema("dice-roll-service", "dice_roll_contract"),
            new ModuleSchema("combat-map-service", "combat_map_contract"),
            new ModuleSchema("ai-game-master-service", "ai_game_master_contract"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Test
    void everyServiceMigrationAppliesAndMutableRootsExposeVersions() throws Exception {
        for (ModuleSchema module : MODULES) {
            flyway(module, null).migrate();
            assertTrue(migrationCount(module.schema()) >= 1, module.module() + " must have a migration history");
        }

        assertColumn("identity_access", "players", "version");
        assertColumn("adventure_contract", "adventure", "version");
        assertColumn("rule_knowledge_contract", "rulebook_vector_index", "version");
        assertColumn("character_management", "character_sheet", "version");
        assertColumn("dice_roll_contract", "dice_roll", "version");
        assertColumn("combat_map_contract", "combat_map", "version");
        assertColumn("ai_game_master_contract", "ai_operation", "version");
    }

    @Test
    void deployedMigrationVersionNamesRemainResolvableWhenNewMigrationsArrive() {
        List<LegacyMigration> modules = List.of(
                new LegacyMigration("identity-access-service", "identity_access_legacy", "2.5"),
                new LegacyMigration("dice-roll-service", "dice_roll_legacy", "2.4"),
                new LegacyMigration("combat-map-service", "combat_map_legacy", "2.4"),
                new LegacyMigration("rule-knowledge-service", "rule_knowledge_legacy", "2.6"));

        for (LegacyMigration module : modules) {
            flyway(new ModuleSchema(module.module(), module.schema()), module.lastLegacyVersion()).migrate();
            Flyway current = flyway(new ModuleSchema(module.module(), module.schema()), null);
            current.migrate();
            current.validate();
        }
    }

    @Test
    void expandMigrationPreservesOldAdventureState() throws Exception {
        ModuleSchema module = new ModuleSchema("adventure-service", "adventure_expand_contract");
        flyway(module, "1.1").migrate();
        UUID adventureId = UUID.randomUUID();
        try (Connection connection = connection(); var statement = connection.prepareStatement("""
                INSERT INTO adventure_expand_contract.adventure (
                    adventure_id, session_id, owner_player_id, scenario_id, rule_set_id, character_sheet_id,
                    current_scene, status, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'tavern', 'SAVED', 3)
                """)) {
            statement.setObject(1, adventureId);
            for (int index = 2; index <= 6; index++) statement.setObject(index, UUID.randomUUID());
            statement.executeUpdate();
        }

        flyway(module, null).migrate();

        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "SELECT current_scene, version, operation_key, updated_at "
                        + "FROM adventure_expand_contract.adventure WHERE adventure_id=?")) {
            statement.setObject(1, adventureId);
            try (var row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals("tavern", row.getString("current_scene"));
                assertEquals(3, row.getLong("version"));
                assertEquals(null, row.getString("operation_key"));
                assertNotNull(row.getObject("updated_at"));
            }
        }
    }

    @Test
    void pgvectorOldAndNextEmbeddingsCoexistDuringParallelIndexTransition() throws Exception {
        ModuleSchema module = new ModuleSchema("rule-knowledge-service", "vector_expand_contract");
        flyway(module, "1.6").migrate();
        UUID indexId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO vector_expand_contract.rulebook_vector_index VALUES ('" + indexId
                    + "', '" + rulebookId + "', '" + ownerId + "', 'legacy', 3, 'v1', 'READY')");
            statement.execute("INSERT INTO vector_expand_contract.rulebook_vector_chunk "
                    + "(chunk_id,index_id,rulebook_id,owner_player_id,sequence,locator,content,embedding) VALUES ('"
                    + chunkId + "','" + indexId + "','" + rulebookId + "','" + ownerId
                    + "',0,'p.1','legacy rule','[1,2,3]')");
        }

        flyway(module, null).migrate();

        try (Connection connection = connection(); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("SELECT embedding_current::text, embedding_next "
                    + "FROM vector_expand_contract.rulebook_vector_chunk_transition")) {
                assertTrue(row.next());
                assertEquals("[1,2,3]", row.getString(1));
                assertEquals(null, row.getObject(2));
            }
            try (var indexes = statement.executeQuery("SELECT count(*) FROM pg_indexes "
                    + "WHERE schemaname='vector_expand_contract' "
                    + "AND indexname='rulebook_vector_chunk_embedding_next_hnsw_idx'")) {
                assertTrue(indexes.next());
                assertEquals(1, indexes.getInt(1));
            }
        }
    }

    private static Flyway flyway(ModuleSchema module, String target) {
        Path root = Path.of(System.getProperty("dnd.reactor.root"));
        String location = "filesystem:" + root.resolve(module.module())
                .resolve("src/main/resources/db/migration").toAbsolutePath().toString().replace('\\', '/');
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(module.schema())
                .defaultSchema(module.schema())
                .locations(location);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static int migrationCount(String schema) throws Exception {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "SELECT count(*) FROM " + schema + ".flyway_schema_history WHERE success")) {
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static void assertColumn(String schema, String table, String column) throws Exception {
        try (Connection connection = connection(); var statement = connection.prepareStatement("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema=? AND table_name=? AND column_name=?
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next(), schema + "." + table + " must expose " + column);
                assertFalse(rows.getString(1).isBlank());
            }
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record ModuleSchema(String module, String schema) {}

    private record LegacyMigration(String module, String schema, String lastLegacyVersion) {}
}
