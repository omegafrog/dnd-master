package com.dndmaster.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.character.api.CharacterSheetAdventureAccessDeniedException;
import com.dndmaster.character.api.CharacterSheetApiService;
import com.dndmaster.character.domain.*;
import com.dndmaster.character.infrastructure.persistence.OptimisticCharacterSheetLockException;
import com.dndmaster.character.infrastructure.persistence.PostgresCharacterSheetRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class CharacterSheetPostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("character_management")
            .withUsername("character_management")
            .withPassword("character_management");
    private static DataSource dataSource;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll static void stopDatabase() { POSTGRES.stop(); }

    @BeforeEach
    void clearDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE character_management.character_sheet CASCADE");
        }
    }

    @Test
    void storesAndRestoresDedicated2014And2024Data() {
        PostgresCharacterSheetRepository repository = new PostgresCharacterSheetRepository(dataSource);
        CharacterSheet sheet2014 = sheet(SheetEdition.DND_5E_2014, new CharacterSheetData2014("Aria", 4, true));
        CharacterSheet sheet2024 = sheet(SheetEdition.DND_5E_2024, new CharacterSheetData2024("Borin", 8, true));
        repository.save(sheet2014);
        repository.save(sheet2024);

        PostgresCharacterSheetRepository freshRepository = new PostgresCharacterSheetRepository(dataSource);
        CharacterSheet restored2014 = freshRepository.findById(sheet2014.id()).orElseThrow();
        CharacterSheet restored2024 = freshRepository.findById(sheet2024.id()).orElseThrow();

        assertInstanceOf(CharacterSheetData2014.class, restored2014.data());
        assertInstanceOf(CharacterSheetData2024.class, restored2024.data());
        assertEquals(sheet2014.data(), restored2014.data());
        assertEquals(sheet2024.data(), restored2024.data());
    }

    @Test
    void storesBuildAndMutableStateSeparately() {
        PostgresCharacterSheetRepository repository = new PostgresCharacterSheetRepository(dataSource);
        CharacterSheet sheet = sheet(SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Aria", 1, false, "Elf", "Wizard", "Sage", "dexterity=15",
                        "{\"armorClass\":13}", "{\"subrace\":\"High Elf\"}", "{\"currentHitPoints\":6}"));
        repository.save(sheet);

        CharacterSheet restored = new PostgresCharacterSheetRepository(dataSource).findById(sheet.id()).orElseThrow();
        assertEquals("{\"subrace\":\"High Elf\"}", restored.data().characterBuild());
        assertEquals("{\"currentHitPoints\":6}", restored.data().characterState());
    }

    @Test
    void adventureBoundApiRejectsSheetFromAnotherAdventure() {
        PostgresCharacterSheetRepository repository = new PostgresCharacterSheetRepository(dataSource);
        CharacterSheet sheet = sheet(SheetEdition.DND_5E_2014, new CharacterSheetData2014("Aria", 4, false));
        repository.save(sheet);
        CharacterSheetApiService api = new CharacterSheetApiService(repository);

        assertEquals(sheet.id().value(), api.getForAdventure(sheet.adventureId(), sheet.id()).characterSheetId());
        assertThrows(
                CharacterSheetAdventureAccessDeniedException.class,
                () -> api.getForAdventure(new AdventureId(UUID.randomUUID()), sheet.id()));
    }

    @Test
    void staleVersionCannotOverwriteCompletedStructuredUpdate() {
        PostgresCharacterSheetRepository firstRepository = new PostgresCharacterSheetRepository(dataSource);
        CharacterSheet original = sheet(SheetEdition.DND_5E_2024, new CharacterSheetData2024("Borin", 8, false));
        firstRepository.save(original);
        PostgresCharacterSheetRepository secondRepository = new PostgresCharacterSheetRepository(dataSource);
        CharacterSheet winner = firstRepository.findById(original.id()).orElseThrow();
        CharacterSheet stale = secondRepository.findById(original.id()).orElseThrow();
        CharacterSheetUpdate winnerUpdate = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Borin", 9, true),
                InputMode.STRUCTURED_SHEET,
                UUID.randomUUID(),
                0);
        CharacterSheetUpdate staleUpdate = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Borin", 10, false),
                InputMode.STRUCTURED_SHEET,
                UUID.randomUUID(),
                0);
        winner.applyUpdate(winnerUpdate);
        stale.applyUpdate(staleUpdate);

        firstRepository.save(winner, 1, winnerUpdate.commandId(), winnerUpdate.fingerprint());
        assertThrows(OptimisticCharacterSheetLockException.class, () ->
                secondRepository.save(stale, 1, staleUpdate.commandId(), staleUpdate.fingerprint()));

        CharacterSheet restored = new PostgresCharacterSheetRepository(dataSource).findById(original.id()).orElseThrow();
        assertEquals(9, restored.data().level());
    }

    @Test
    void preserves_latest_command_metadata_when_updating() {
        PostgresCharacterSheetRepository repository = new PostgresCharacterSheetRepository(dataSource);
        CharacterSheet original = sheet(SheetEdition.DND_5E_2024, new CharacterSheetData2024("Borin", 8, false));
        repository.save(original);
        CharacterSheet loaded = repository.findById(original.id()).orElseThrow();
        UUID commandId = UUID.randomUUID();
        CharacterSheetUpdate update = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Borin", 9, true),
                InputMode.STRUCTURED_SHEET,
                commandId,
                loaded.version());
        loaded.applyUpdate(update);

        repository.save(loaded, loaded.version() + 1, commandId, update.fingerprint());

        CharacterSheet restored = new PostgresCharacterSheetRepository(dataSource).findById(original.id()).orElseThrow();
        assertEquals(commandId, restored.operationKey());
        assertEquals(loaded.operationFingerprint(), restored.operationFingerprint());
        assertEquals(9, restored.data().level());
        assertEquals(1L, restored.version());
    }

    @Test
    void replays_an_older_character_command_from_history_even_after_a_later_update() {
        PostgresCharacterSheetRepository repository = new PostgresCharacterSheetRepository(dataSource);
        CharacterSheet original = sheet(SheetEdition.DND_5E_2024, new CharacterSheetData2024("Borin", 8, false));
        repository.save(original);

        CharacterSheet loaded = repository.findById(original.id()).orElseThrow();
        UUID firstCommandId = UUID.randomUUID();
        CharacterSheetUpdate firstUpdate = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Borin", 9, true),
                InputMode.STRUCTURED_SHEET,
                firstCommandId,
                loaded.version());
        loaded.applyUpdate(firstUpdate);
        repository.save(loaded, loaded.version() + 1, firstCommandId, firstUpdate.fingerprint());

        CharacterSheet later = repository.findById(original.id()).orElseThrow();
        UUID laterCommandId = UUID.randomUUID();
        CharacterSheetUpdate laterUpdate = new CharacterSheetUpdate(
                SheetEdition.DND_5E_2024,
                new CharacterSheetData2024("Borin", 10, false),
                InputMode.STRUCTURED_SHEET,
                laterCommandId,
                later.version());
        later.applyUpdate(laterUpdate);
        repository.save(later, later.version() + 1, laterCommandId, laterUpdate.fingerprint());

        CharacterSheet replay = repository.findByCommandId(firstCommandId).orElseThrow();

        assertEquals(9, replay.data().level());
        assertEquals(firstCommandId, replay.operationKey());
        assertEquals(1L, replay.version());
    }

    private static CharacterSheet sheet(SheetEdition edition, CharacterSheetData data) {
        return new CharacterSheet(
                CharacterSheetId.generate(), new AdventureId(UUID.randomUUID()), edition, data);
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
