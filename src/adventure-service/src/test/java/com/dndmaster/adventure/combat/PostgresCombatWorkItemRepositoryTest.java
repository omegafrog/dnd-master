package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AiTacticalInstructionContext;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatWorkItem;
import com.dndmaster.adventure.application.combat.AiTurnPlan;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.RuntimePlanningRequest;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
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
        UUID requestId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO combat_encounter(encounter_id) VALUES (?)")) {
            statement.setObject(1, encounterId);
            statement.executeUpdate();
        }
        var command = new CombatActionCommand(operationId, new AdventureId(UUID.randomUUID()), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null, CombatActorRole.AI,
                "AI_TURN", null, null, actorId, 1, null, null, null, null, false);
        AiTurnPlan decisionPlan = new AiTurnPlan(actorId,
                new com.dndmaster.adventure.domain.combat.CombatActionIntent(actorId, "attack",
                        com.dndmaster.adventure.domain.combat.TurnResourceCost.actionOnly()),
                null, null, null, null, false, null, List.of("rule-ref"), null);
        var repository = new PostgresCombatWorkItemRepository(dataSource, new ObjectMapper());
        repository.enqueue(new CombatWorkItem(UUID.randomUUID(), encounterId, operationId, 1,
                CombatWorkItem.WorkType.AI_TURN, Instant.parse("2026-01-01T00:00:00Z"), 0,
                new AiTacticalInstructionContext("Protect the healer", List.of("stay near the party")), command, 0, requestId)
                .withDecisionPlan(decisionPlan));

        var claimed = repository.claim("worker-1", java.time.Duration.ofSeconds(30), Instant.parse("2026-01-01T00:00:01Z")).orElseThrow();
        assertEquals(CombatWorkItem.Status.CLAIMED, claimed.status());
        assertEquals("Protect the healer", claimed.tacticalInstruction().instruction());
        assertTrue(claimed.leaseToken() != null);
        assertEquals(requestId, claimed.aiRequestId());
        repository.save(claimed.failed(claimed.leaseToken(), "AI unavailable"));
        var failed = repository.findByOperationId(operationId).orElseThrow();
        assertEquals(CombatWorkItem.Status.FAILED, failed.status());
        assertEquals(operationId, failed.operationId());
        assertEquals(decisionPlan, failed.decisionPlan());
        repository.save(failed.manualRetry(Instant.parse("2026-01-01T00:00:02Z")));
        var retried = repository.findByOperationId(operationId).orElseThrow();
        assertEquals(CombatWorkItem.Status.PENDING, retried.status());
        assertEquals(decisionPlan, retried.decisionPlan());
    }

    @Test
    void persists_enemy_sheet_preparation_request_across_claim_and_retry() throws Exception {
        DataSource dataSource = new SimpleDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        createSchema(dataSource);
        UUID encounterId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO combat_encounter(encounter_id) VALUES (?)")) {
            statement.setObject(1, encounterId);
            statement.executeUpdate();
        }
        UUID adventureId = UUID.randomUUID();
        var identity = new EnemyCharacterSheetIdentity(adventureId, UUID.randomUUID(), 4, UUID.randomUUID(),
                List.of(UUID.randomUUID()), "goblin");
        var planningRequest = new RuntimePlanningRequest(new AdventureId(adventureId), new OwnerPlayerId(UUID.randomUUID()),
                UUID.randomUUID(), 4, new AdventureContext("scene", null, null, null), null,
                "prepare enemy profile", new EvidencePack(List.of(), List.of(), List.of()));
        var request = new EnemySheetPreparationRequest(UUID.randomUUID(), adventureId,
                List.of(new EnemySheetPreparationRequest.Enemy(identity,
                        new CombatEnemyProposal("scene", "goblin", "Goblin", 1))), planningRequest);
        var repository = new PostgresCombatWorkItemRepository(dataSource, new ObjectMapper());
        repository.enqueue(CombatWorkItem.enemySheetPreparation(UUID.randomUUID(), encounterId, 1,
                Instant.parse("2026-01-01T00:00:00Z"), request));

        var claimed = repository.claim("worker-1", java.time.Duration.ofSeconds(30), Instant.parse("2026-01-01T00:00:01Z")).orElseThrow();
        assertEquals(request, claimed.enemySheetPreparationRequest());
        repository.save(claimed.retry(claimed.leaseToken(), Instant.parse("2026-01-01T00:00:02Z"), "TEMPORARY"));
        var retried = repository.claim("worker-2", java.time.Duration.ofSeconds(30), Instant.parse("2026-01-01T00:00:03Z")).orElseThrow();
        assertEquals(request, retried.enemySheetPreparationRequest());
    }

    @Test
    void reads_legacy_timestamp_columns_when_restoring_claimed_work() throws Exception {
        DataSource dataSource = new SimpleDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        createSchema(dataSource);
        UUID encounterId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE combat_work_item ALTER COLUMN due_at TYPE TIMESTAMP WITHOUT TIME ZONE USING due_at AT TIME ZONE 'UTC'");
            statement.execute("ALTER TABLE combat_work_item ALTER COLUMN lease_until TYPE TIMESTAMP WITHOUT TIME ZONE USING lease_until AT TIME ZONE 'UTC'");
        }
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO combat_encounter(encounter_id) VALUES (?)")) {
            statement.setObject(1, encounterId);
            statement.executeUpdate();
        }
        UUID actorId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        var command = new CombatActionCommand(operationId, new AdventureId(UUID.randomUUID()), UUID.randomUUID(),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(actorId), null, CombatActorRole.AI,
                "AI_TURN", null, null, actorId, 1, null, null, null, null, false);
        var repository = new PostgresCombatWorkItemRepository(dataSource, new ObjectMapper());
        Instant now = Instant.parse("2026-01-01T00:00:01Z");
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO combat_work_item(work_item_id, encounter_id, operation_id, expected_encounter_version, work_type, due_at, attempt_count, status, tactical_instruction, tactical_constraints, command_json, completed_steps) " +
                        "VALUES (?, ?, ?, 1, 'AI_TURN', TIMESTAMP '2026-01-01 00:00:00', 0, 'PENDING', 'Protect the healer', '[]'::jsonb, ?::jsonb, 0)")) {
            statement.setObject(1, workItemId);
            statement.setObject(2, encounterId);
            statement.setObject(3, operationId);
            statement.setString(4, new ObjectMapper().writeValueAsString(command));
            statement.executeUpdate();
        }

        var claimed = repository.claim("worker-legacy", java.time.Duration.ofSeconds(30), now).orElseThrow();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("UPDATE combat_work_item SET lease_until = TIMESTAMP '2026-01-01 00:00:31' WHERE operation_id = '" + operationId + "'");
        }
        var restored = repository.findByOperationId(operationId).orElseThrow();

        assertEquals(CombatWorkItem.Status.CLAIMED, restored.status());
        assertEquals(now.minusSeconds(1), restored.dueAt());
        assertEquals(now.plusSeconds(30), restored.leaseUntil());
        assertEquals(claimed.leaseToken(), restored.leaseToken());
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS combat_work_item");
            statement.execute("DROP TABLE IF EXISTS combat_encounter");
            statement.execute("CREATE TABLE combat_encounter (encounter_id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE combat_work_item (work_item_id UUID PRIMARY KEY, encounter_id UUID NOT NULL REFERENCES combat_encounter(encounter_id), operation_id UUID, ai_request_id UUID, expected_encounter_version BIGINT NOT NULL, work_type TEXT NOT NULL, due_at TIMESTAMPTZ NOT NULL, attempt_count INT NOT NULL, status TEXT NOT NULL, lease_token UUID, worker_id TEXT, lease_until TIMESTAMPTZ, failure TEXT, tactical_instruction TEXT NOT NULL, tactical_constraints JSONB NOT NULL, command_json JSONB, completed_steps INT NOT NULL, enemy_sheet_preparation_request JSONB, decision_plan JSONB)");
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
