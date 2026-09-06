package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AiTacticalInstructionContext;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatWorkItem;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCombatWorkItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresCombatWorkItemRepositoryTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @BeforeAll static void startDatabase() { POSTGRES.start(); }
    @AfterAll static void stopDatabase() { POSTGRES.stop(); }

    @Test
    void persists_tactical_context_and_claim_lease_then_allows_same_operation_manual_retry() throws Exception {
        DataSource dataSource = new SimpleDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        createSchema(dataSource);
        UUID encounterId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO combat_encounter(encounter_id) VALUES (?)")) {
            statement.setObject(1, encounterId);
            statement.executeUpdate();
        }
        var command = new CombatActionCommand(operationId, new AdventureId(UUID.randomUUID()), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()), null, CombatActorRole.AI,
                "AI_TURN", null, null, UUID.randomUUID(), 1, null, null, null, null, false);
        var repository = new PostgresCombatWorkItemRepository(dataSource, new ObjectMapper());
        repository.enqueue(new CombatWorkItem(UUID.randomUUID(), encounterId, operationId, 1,
                CombatWorkItem.WorkType.AI_TURN, Instant.parse("2026-01-01T00:00:00Z"), 0,
                new AiTacticalInstructionContext("Protect the healer", List.of("stay near the party")), command));

        var claimed = repository.claim("worker-1", java.time.Duration.ofSeconds(30), Instant.parse("2026-01-01T00:00:01Z")).orElseThrow();
        assertEquals(CombatWorkItem.Status.CLAIMED, claimed.status());
        assertEquals("Protect the healer", claimed.tacticalInstruction().instruction());
        assertTrue(claimed.leaseToken() != null);
        repository.save(claimed.failed(claimed.leaseToken(), "AI unavailable"));
        var failed = repository.findByOperationId(operationId).orElseThrow();
        assertEquals(CombatWorkItem.Status.FAILED, failed.status());
        assertEquals(operationId, failed.operationId());
        repository.save(failed.manualRetry(Instant.parse("2026-01-01T00:00:02Z")));
        assertEquals(CombatWorkItem.Status.PENDING, repository.findByOperationId(operationId).orElseThrow().status());
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS combat_work_item");
            statement.execute("DROP TABLE IF EXISTS combat_encounter");
            statement.execute("CREATE TABLE combat_encounter (encounter_id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE combat_work_item (work_item_id UUID PRIMARY KEY, encounter_id UUID NOT NULL REFERENCES combat_encounter(encounter_id), operation_id UUID, expected_encounter_version BIGINT NOT NULL, work_type TEXT NOT NULL, due_at TIMESTAMPTZ NOT NULL, attempt_count INT NOT NULL, status TEXT NOT NULL, lease_token UUID, worker_id TEXT, lease_until TIMESTAMPTZ, failure TEXT, tactical_instruction TEXT NOT NULL, tactical_constraints JSONB NOT NULL, command_json JSONB, completed_steps INT NOT NULL)");
        }
    }

    private record SimpleDataSource(String url, String username, String password) implements DataSource {
        @Override public Connection getConnection() throws java.sql.SQLException { return DriverManager.getConnection(url, username, password); }
        @Override public Connection getConnection(String user, String pass) throws java.sql.SQLException { return DriverManager.getConnection(url, user, pass); }
        @Override public <T> T unwrap(Class<T> type) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> type) { return false; }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
