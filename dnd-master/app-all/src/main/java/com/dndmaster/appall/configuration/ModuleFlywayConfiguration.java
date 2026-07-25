package com.dndmaster.appall.configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration(proxyBeanMethods = false)
public class ModuleFlywayConfiguration {
    private static final PathMatchingResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();
    private static final List<ModuleMigration> MODULES = List.of(
            new ModuleMigration("identity-access-service", "flyway_identity_access_schema_history"),
            new ModuleMigration("adventure-service", "flyway_adventure_schema_history"),
            new ModuleMigration("rule-knowledge-service", "flyway_rule_knowledge_schema_history"),
            new ModuleMigration("character-management-service", "flyway_character_management_schema_history"),
            new ModuleMigration("dice-roll-service", "flyway_dice_roll_schema_history"),
            new ModuleMigration("combat-map-service", "flyway_combat_map_schema_history"),
            new ModuleMigration("ai-game-master-service", "flyway_ai_game_master_schema_history"));

    @Bean
    FlywayMigrationInitializer identityAccessFlywayInitializer(DataSource dataSource) {
        return initializer(dataSource, MODULES.get(0));
    }

    @Bean
    FlywayMigrationInitializer adventureFlywayInitializer(DataSource dataSource) {
        return initializer(dataSource, MODULES.get(1));
    }

    @Bean
    FlywayMigrationInitializer ruleKnowledgeFlywayInitializer(DataSource dataSource) {
        return initializer(dataSource, MODULES.get(2));
    }

    @Bean
    FlywayMigrationInitializer characterManagementFlywayInitializer(DataSource dataSource) {
        return initializer(dataSource, MODULES.get(3));
    }

    @Bean
    FlywayMigrationInitializer diceRollFlywayInitializer(DataSource dataSource) {
        return initializer(dataSource, MODULES.get(4));
    }

    @Bean
    FlywayMigrationInitializer combatMapFlywayInitializer(DataSource dataSource) {
        return initializer(dataSource, MODULES.get(5));
    }

    @Bean
    FlywayMigrationInitializer aiGameMasterFlywayInitializer(DataSource dataSource) {
        return initializer(dataSource, MODULES.get(6));
    }

    static List<ModuleMigration> modules() {
        return MODULES;
    }

    private static FlywayMigrationInitializer initializer(DataSource dataSource, ModuleMigration module) {
        return new FlywayMigrationInitializer(flyway(dataSource, module));
    }

    private static Flyway flyway(DataSource dataSource, ModuleMigration module) {
        syncHistoryFromLegacy(dataSource, module);
        return Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema("public")
                .table(module.historyTable())
                .locations(module.location())
                .outOfOrder(true)
                .load();
    }

    private static void syncHistoryFromLegacy(DataSource dataSource, ModuleMigration module) {
        if (!tableExists(dataSource, "public", "flyway_schema_history")) {
            return;
        }

        if (!tableExists(dataSource, "public", module.historyTable())) {
            createHistoryTableLikeLegacy(dataSource, module.historyTable());
        }

        Set<String> moduleScripts = migrationScripts(module);
        Map<String, Integer> currentChecksums = currentChecksums(dataSource, module);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var delete = connection.prepareStatement(
                        "DELETE FROM public." + quotedIdentifier(module.historyTable()))) {
                    delete.executeUpdate();
                }
                try (var select = connection.prepareStatement("""
                        SELECT version, description, type, script, checksum, installed_by, execution_time, success
                        FROM public.flyway_schema_history
                        WHERE success = true AND script = ANY (?)
                        ORDER BY installed_rank
                        """)) {
                    select.setArray(1, connection.createArrayOf("text", moduleScripts.toArray(String[]::new)));
                    try (var rows = select.executeQuery();
                            var insert = connection.prepareStatement("""
                                    INSERT INTO public.%s (
                                        installed_rank, version, description, type, script,
                                        checksum, installed_by, execution_time, success
                                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """.formatted(quotedIdentifier(module.historyTable())))) {
                        int installedRank = 1;
                        while (rows.next()) {
                            String version = rows.getString("version");
                            String script = rows.getString("script");
                            Integer fallbackChecksum = rows.getObject("checksum", Integer.class);
                            insert.setInt(1, installedRank++);
                            insert.setString(2, version);
                            insert.setString(3, rows.getString("description"));
                            insert.setString(4, rows.getString("type"));
                            insert.setString(5, script);
                            insert.setObject(6, currentChecksums.getOrDefault(version, fallbackChecksum));
                            insert.setString(7, rows.getString("installed_by"));
                            insert.setInt(8, rows.getInt("execution_time"));
                            insert.setBoolean(9, rows.getBoolean("success"));
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot seed Flyway history for " + module.module(), exception);
        }
    }

    private static Map<String, Integer> currentChecksums(DataSource dataSource, ModuleMigration module) {
        try {
            return Arrays.stream(Flyway.configure()
                            .dataSource(dataSource)
                            .locations(module.location())
                            .load()
                            .info()
                            .all())
                    .filter(migration -> migration.getScript() != null && migration.getResolvedChecksum() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            migration -> migration.getVersion().getVersion(),
                            migration -> migration.getResolvedChecksum(),
                            (left, right) -> left));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot resolve Flyway checksums for " + module.module(), exception);
        }
    }

    private static Set<String> migrationScripts(ModuleMigration module) {
        try {
            Resource[] resources = RESOLVER.getResources(module.scanPattern());
            return java.util.Arrays.stream(resources)
                    .map(resource -> {
                        try {
                            return Objects.requireNonNull(resource.getFilename(), "Missing migration filename");
                        } catch (Exception exception) {
                            throw new IllegalStateException("Cannot resolve Flyway migrations for " + module.location(), exception);
                        }
                    })
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot resolve Flyway migrations for " + module.location(), exception);
        }
    }

    private static void createHistoryTableLikeLegacy(DataSource dataSource, String historyTable) {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS public.%s
                    (LIKE public.flyway_schema_history INCLUDING ALL)
                    """.formatted(quotedIdentifier(historyTable)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create Flyway history table " + historyTable, exception);
        }
    }

    private static boolean tableExists(DataSource dataSource, String schema, String table) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = ? AND table_name = ?
                        LIMIT 1
                        """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect table " + schema + "." + table, exception);
        }
    }

    private static String quotedIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    record ModuleMigration(String module, String historyTable) {
        String location() {
            return "classpath:db/migration/" + module;
        }

        String scanPattern() {
            return "classpath*:db/migration/" + module + "/*.sql";
        }
    }
}
