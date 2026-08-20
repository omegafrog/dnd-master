package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.NarrationSafetyAssessment;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnOrigin;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeTurnRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class RuntimeTurnPostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure")
            .withUsername("adventure")
            .withPassword("adventure");

    private static DataSource dataSource;
    private RuntimeTurnRepository repository;

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
            statement.execute("TRUNCATE adventure_runtime_turn CASCADE");
            statement.execute("TRUNCATE adventure_runtime_binding CASCADE");
            statement.execute("TRUNCATE adventure_conversation CASCADE");
            statement.execute("TRUNCATE adventure CASCADE");
        }
        repository = new PostgresRuntimeTurnRepository(dataSource, new ObjectMapper());
    }

    @Test
    void savesAndReloadsRuntimeTurnThroughPostgres() {
        AdventureId adventureId = AdventureId.generate();
        SessionId sessionId = SessionId.generate();
        PostgresAdventureRepository adventureRepository = new PostgresAdventureRepository(dataSource);
        adventureRepository.save(Adventure.create(
                adventureId,
                sessionId,
                new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("start", null, null, null)));
        UUID commandId = UUID.randomUUID();
        RuntimeTurn turn = new RuntimeTurn(
                UUID.randomUUID(),
                commandId,
                adventureId,
                sessionId.value(),
                UUID.randomUUID(),
                3L,
                "Open the door",
                new EvidencePack(
                        List.of(new RuntimeEvidence(
                                RuntimeEvidenceType.STORYBOOK,
                                new KnowledgeDocumentId(UUID.randomUUID()),
                                1L,
                                "page:1:span:1",
                                "A sealed door")),
                        List.of(new RuntimeEvidence(
                                RuntimeEvidenceType.RULEBOOK,
                                new KnowledgeDocumentId(UUID.randomUUID()),
                                1L,
                                "rulebook:1",
                                "Door rules")),
                        List.of()),
                new RuntimePlan(
                        "The door creaks open.",
                        "alert",
                        "The action succeeds.",
                        "근거를 바탕으로 응답한다.",
                        new ActiveSourceContext(new KnowledgeDocumentId(UUID.randomUUID()), 1L, "page:1:span:1", "A sealed door"),
                        List.of(),
                        List.of("resolution evidence not prefetched")),
                new ActiveSourceContext(new KnowledgeDocumentId(UUID.randomUUID()), 1L, "page:1:span:1", "A sealed door"),
                new AdventureContext("The door creaks open.", "alert", "Open the door", "The action succeeds."),
                List.of(new ConversationEntry(0, "AI_GAME_MASTER", "The door creaks open.")),
                1L,
                List.of("storybook:page:1:span:1"),
                List.of("resolution evidence not prefetched"));

        repository.save(turn);

        RuntimeTurn restored = repository.findByTurnId(turn.turnId()).orElseThrow();
        assertEquals(turn, restored);
        assertEquals(RuntimeTurnOrigin.GM, restored.origin());
        assertEquals(false, restored.advancesState());
        assertEquals(List.of(turn), repository.findAllByAdventureId(adventureId));
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
