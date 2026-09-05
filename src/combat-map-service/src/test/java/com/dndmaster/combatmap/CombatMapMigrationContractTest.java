package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CombatMapMigrationContractTest {
    @Test
    void restoresTheActiveMapReadModelAfterScenarioRuntimeCutover() throws IOException {
        Path current = Path.of(".").toAbsolutePath().normalize();
        Path relativePath = null;
        while (current != null) {
            Path candidate = current.resolve(
                    "src/combat-map-service/src/main/resources/db/migration/V2_10__restore_active_tactical_map_read_model.sql");
            if (Files.exists(candidate)) {
                relativePath = candidate;
                break;
            }
            current = current.getParent();
        }
        if (relativePath == null) throw new IOException("repository migration not found");
        String migration = Files.readString(relativePath);

        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS adventure_active_tactical_map"));
        assertTrue(migration.contains("active BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(migration.contains("adventure_active_tactical_map_one_active_owner"));
    }
}
