package com.dndmaster.appall.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ModuleFlywayConfigurationTest {
    @Test
    void modulesUseModuleScopedFlywayLocationsAndHistoryTables() {
        List<ModuleFlywayConfiguration.ModuleMigration> modules = ModuleFlywayConfiguration.modules();

        assertEquals(7, modules.size());
        assertEquals(
                List.of(
                        "identity-access-service",
                        "adventure-service",
                        "rule-knowledge-service",
                        "character-management-service",
                        "dice-roll-service",
                        "combat-map-service",
                        "ai-game-master-service"),
                modules.stream().map(ModuleFlywayConfiguration.ModuleMigration::module).toList());
        assertEquals(
                Set.of(
                        "flyway_identity_access_schema_history",
                        "flyway_adventure_schema_history",
                        "flyway_rule_knowledge_schema_history",
                        "flyway_character_management_schema_history",
                        "flyway_dice_roll_schema_history",
                        "flyway_combat_map_schema_history",
                        "flyway_ai_game_master_schema_history"),
                modules.stream().map(ModuleFlywayConfiguration.ModuleMigration::historyTable).collect(Collectors.toSet()));
        assertTrue(
                modules.stream().allMatch(module -> module.location().startsWith("classpath:db/migration/")),
                "Each migration location must be module-scoped");
    }
}
