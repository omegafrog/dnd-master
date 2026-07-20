package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.saved.CreateAdventureCommand;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureAccessDeniedException;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.infrastructure.persistence.AdventurePersistenceException;
import com.dndmaster.adventure.infrastructure.persistence.OptimisticAdventureLockException;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class SavedAdventurePostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure")
            .withUsername("adventure")
            .withPassword("adventure");

    private static DataSource dataSource;
    private PostgresAdventureRepository repository;
    private SavedAdventureApplicationService service;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE adventure_conversation DROP CONSTRAINT IF EXISTS test_content_length");
            statement.execute("TRUNCATE adventure CASCADE");
        }
        repository = new PostgresAdventureRepository(dataSource);
        service = new SavedAdventureApplicationService(repository);
    }

    @Test
    void preservesConversationAndCurrentContextTogetherAndRestoresThem() {
        OwnerPlayerId owner = owner();
        Adventure created = service.createAdventure(command(owner, context("entrance")));
        List<ConversationEntry> conversation = List.of(
                new ConversationEntry(0, "AI_GAME_MASTER", "You enter the dungeon."),
                new ConversationEntry(1, "PLAYER", "I light a torch."));

        service.preserveProgress(created.id(), owner, 0, context("torch-lit hall"), conversation);

        Adventure restored = repository.findById(created.id()).orElseThrow();
        assertEquals("torch-lit hall", restored.currentContext().currentScene());
        assertEquals(conversation, restored.conversation());
        assertEquals(1, restored.version());
    }

    @Test
    void onlyOwnerCanReopenOrDeleteAndDeletedAdventureIsExcludedFromList() {
        OwnerPlayerId owner = owner();
        Adventure created = service.createAdventure(command(owner, context("camp")));

        assertThrows(
                AdventureAccessDeniedException.class,
                () -> service.reopenAdventure(created.id(), owner()));
        assertThrows(
                AdventureAccessDeniedException.class,
                () -> service.deleteAdventure(created.id(), owner(), 0));
        assertEquals(1, service.listSavedAdventures(owner).size());

        service.deleteAdventure(created.id(), owner, 0);

        assertEquals(List.of(), service.listSavedAdventures(owner));
    }

    @Test
    void conversationFailureRollsBackContextConversationAndVersionAtomically() throws SQLException {
        OwnerPlayerId owner = owner();
        Adventure created = service.createAdventure(command(owner, context("before")));
        service.preserveProgress(
                created.id(), owner, 0, context("stable"),
                List.of(new ConversationEntry(0, "PLAYER", "short")));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE adventure_conversation ADD CONSTRAINT test_content_length CHECK (char_length(content) <= 20)");
        }

        assertThrows(
                AdventurePersistenceException.class,
                () -> service.preserveProgress(
                        created.id(), owner, 1, context("must roll back"),
                        List.of(new ConversationEntry(0, "PLAYER", "this content is intentionally too long"))));

        Adventure restored = repository.findById(created.id()).orElseThrow();
        assertEquals("stable", restored.currentContext().currentScene());
        assertEquals(List.of(new ConversationEntry(0, "PLAYER", "short")), restored.conversation());
        assertEquals(1, restored.version());
    }

    @Test
    void staleAggregateCannotOverwriteConcurrentChange() {
        OwnerPlayerId owner = owner();
        Adventure created = service.createAdventure(command(owner, context("start")));
        Adventure winner = repository.findById(created.id()).orElseThrow();
        Adventure loser = repository.findById(created.id()).orElseThrow();
        winner.delete(owner, 0);
        repository.save(winner);
        loser.preserveProgress(owner, 0, context("stale"), List.of());

        assertThrows(OptimisticAdventureLockException.class, () -> repository.save(loser));
        assertEquals(0, service.listSavedAdventures(owner).size());
    }

    private static CreateAdventureCommand command(OwnerPlayerId owner, AdventureContext context) {
        return new CreateAdventureCommand(
                owner,
                new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()),
                context);
    }

    private static AdventureContext context(String scene) {
        return new AdventureContext(scene, "npc state", null, null);
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.randomUUID());
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
