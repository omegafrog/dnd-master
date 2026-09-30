package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresConversationCompactionJobRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresLongTermFactPersistenceIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure").withUsername("adventure").withPassword("adventure");
    private static DataSource dataSource;

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

    @Test
    void persists_a_confirmed_fact_with_its_source_and_selects_it_after_reload() {
        AdventureId adventureId = AdventureId.generate();
        SessionId sessionId = SessionId.generate();
        PostgresAdventureRepository adventures = new PostgresAdventureRepository(dataSource);
        adventures.save(Adventure.create(adventureId, sessionId, new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("시작", null, null, null)));

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        PostgresConversationCompactionJobRepository repository = new PostgresConversationCompactionJobRepository(dataSource);
        repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 0, now));

        UUID factId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        RuntimeAddedFact confirmed = new RuntimeAddedFact(factId, "경비는 성문을 열어 주기로 했다", turnId, "경비");
        List<ConversationEntry> conversation = List.of(
                entry(0, "PLAYER", "경비에게 협력하겠다고 약속한다. ".repeat(12)),
                entry(1, "AI_GAME_MASTER", "경비는 성문을 열어 주기로 했다. ".repeat(12)));
        ConversationCompactionCandidatePort candidate = (job, source) -> new ConversationCompactionCandidate(
                job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(),
                "경비는 협력 약속을 받아들여 성문을 열어 주기로 했다.",
                List.of(new LongTermFactCandidate(factId, turnId, "EVENT", "후보가 지정한 내용은 사용하지 않는다", false)));

        boolean published = new ConversationCompactionCoordinator(repository, candidate)
                .runOnce(adventureId, UUID.randomUUID(), 0, conversation, List.of(confirmed), now);

        assertTrue(published);
        LongTermAdventureFact stored = repository.longTermFacts(adventureId).getFirst();
        assertEquals(factId, stored.factId());
        assertEquals(turnId, stored.establishedTurnId());
        assertEquals(0, stored.sourceAdventureVersion());
        assertEquals("RELATIONSHIP", stored.kind());
        assertEquals(confirmed.content(), stored.relevance());
        assertTrue(stored.playerVisible());
        assertEquals(1, stored.version());

        String prompt = RuntimeGmPromptComposer.compose(
                "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=[]\nRUNTIME_CONTEXT={}\nACTION=go"
                        + "\nLOOKUP_ORDER_RULE=rules\nOUTPUT_CONTRACT=json", List.of(), List.of(),
                java.util.Map.of("currentSituation", "CurrentSituation[location=성문, problem=경비에게 협력 약속 확인, threat=없음, goal=약속 확인]"),
                repository.longTermFacts(adventureId), 10_000);
        String memory = prompt.substring(prompt.indexOf("현재 상황 관련 장기 기록"), prompt.indexOf("압축된 이전 대화"));
        assertTrue(memory.contains(confirmed.content()));
        assertFalse(memory.contains("후보가 지정한 내용은 사용하지 않는다"));
    }

    private static ConversationEntry entry(long sequence, String speaker, String content) {
        return new ConversationEntry(sequence, speaker, content);
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
