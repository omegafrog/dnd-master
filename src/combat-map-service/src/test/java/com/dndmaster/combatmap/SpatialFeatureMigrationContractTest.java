package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SpatialFeatureMigrationContractTest {
    @Test
    void migration_stores_features_and_moves_legacy_tokens_without_creating_triggers() throws IOException {
        Path current = Path.of(".").toAbsolutePath().normalize();
        Path migration = null;
        while (current != null) {
            Path candidate = current.resolve("src/combat-map-service/src/main/resources/db/migration/V2_16__combat_map_spatial_features.sql");
            if (Files.exists(candidate)) { migration = candidate; break; }
            current = current.getParent();
        }
        assertTrue(migration != null);
        String sql = Files.readString(migration);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS combat_map_spatial_feature"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS combat_map_spatial_feature_cell"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS combat_map_spatial_feature_trigger"));
        assertTrue(sql.contains("'SYSTEM', 'legacy-token'"));
        assertTrue(sql.contains("DELETE FROM combat_map_token WHERE token_type IN ('TRAP', 'OBJECT')"));
        assertTrue(sql.contains("ON CONFLICT (map_id, feature_id) DO NOTHING") || sql.contains("ON CONFLICT (map_id, feature_id) DO UPDATE"));
    }
}
