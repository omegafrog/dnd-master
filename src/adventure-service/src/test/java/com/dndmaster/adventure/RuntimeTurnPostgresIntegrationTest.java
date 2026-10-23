package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.NarrationSafetyAssessment;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandRepository;
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
import com.dndmaster.adventure.infrastructure.persistence.PostgresRuntimeTurnCommandRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCombatActionOperationRepository;
import com.dndmaster.adventure.infrastructure.persistence.RuntimeTurnPersistenceException;
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
    private RuntimeTurnCommandRepository commandRepository;

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
        commandRepository = new PostgresRuntimeTurnCommandRepository(dataSource);
    }

    @Test
    void savesAndReloadsRuntimeTurnThroughPostgres() throws Exception {
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

        ObjectMapper mapper = new ObjectMapper();
        var legacyJson = mapper.valueToTree(turn);
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacyJson).remove(List.of("origin", "playerOrigin", "advancesState"));
        RuntimeTurn legacyRestored = mapper.treeToValue(legacyJson, RuntimeTurn.class);
        assertEquals(RuntimeTurnOrigin.GM, legacyRestored.origin());
        assertEquals(false, legacyRestored.playerOrigin());
        assertEquals(false, legacyRestored.advancesState());
    }

    @Test
    void knownEvidenceIdsIncludeOnlyConfirmedTurnEvidence() {
        AdventureId adventureId = AdventureId.generate();
        SessionId sessionId = SessionId.generate();
        PostgresAdventureRepository adventureRepository = new PostgresAdventureRepository(dataSource);
        adventureRepository.save(Adventure.create(adventureId, sessionId, new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("start", null, null, null)));
        UUID scenarioPackageId = UUID.randomUUID();
        var confirmedEvidence = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, new KnowledgeDocumentId(UUID.randomUUID()),
                1L, "page:1:span:1", "confirmed");
        var unconfirmedEvidence = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK, new KnowledgeDocumentId(UUID.randomUUID()),
                1L, "page:1:span:2", "unconfirmed");
        RuntimePlan plan = new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of());
        RuntimeTurn confirmed = new RuntimeTurn(UUID.randomUUID(), UUID.randomUUID(), adventureId, sessionId.value(),
                scenarioPackageId, 1, "confirmed action", new EvidencePack(List.of(confirmedEvidence), List.of(), List.of()),
                plan, null, new AdventureContext("scene", null, "confirmed action", "judgment"), List.of(), 0,
                List.of(), List.of());
        RuntimeTurn unconfirmed = new RuntimeTurn(UUID.randomUUID(), UUID.randomUUID(), adventureId, sessionId.value(),
                scenarioPackageId, 1, "unconfirmed action", new EvidencePack(List.of(unconfirmedEvidence), List.of(), List.of()),
                plan, null, new AdventureContext("scene", null, "unconfirmed action", "judgment"), List.of(), 0,
                List.of(), List.of()).asRequested();
        repository.save(confirmed);
        repository.save(unconfirmed);

        assertEquals(List.of(confirmedEvidence.immutableEvidenceId()), repository.findKnownImmutableEvidenceIds(adventureId));
    }

    @Test
    void persistsOrderedRuntimeTurnCommandsAndEnforcesOneActiveTurnPerAdventure() {
        AdventureId adventureId = AdventureId.generate();
        SessionId sessionId = SessionId.generate();
        PostgresAdventureRepository adventureRepository = new PostgresAdventureRepository(dataSource);
        var owner = new OwnerPlayerId(UUID.randomUUID());
        adventureRepository.save(Adventure.create(adventureId, sessionId, owner, new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("start", null, null, null)));
        RuntimeTurn base = new RuntimeTurn(UUID.randomUUID(), UUID.randomUUID(), adventureId, sessionId.value(), UUID.randomUUID(),
                1, "open", new EvidencePack(List.of(), List.of(), List.of()),
                new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of()), null,
                new AdventureContext("scene", null, "open", "judgment"), List.of(), 0, List.of(), List.of());
        RuntimeTurn active = base.asRequested();
        repository.save(active);
        RuntimeTurn second = new RuntimeTurn(UUID.randomUUID(), UUID.randomUUID(), adventureId, sessionId.value(),
                base.scenarioPackageId(), base.bindingVersion(), base.action(), base.evidencePack(), base.plan(),
                base.activeSourceContext(), base.context(), base.conversation(), 0, base.citations(), base.warnings(),
                false, false, RuntimeTurnOrigin.GM, false).asRequested();
        assertThrows(RuntimeTurnPersistenceException.class, () -> repository.save(second));

        RuntimeTurnCommand command = RuntimeTurnCommand.create(active.turnId(), UUID.randomUUID(), adventureId.value(),
                sessionId.value(), owner.value(), "combat-map", "combat-map.update", "{}", 1).done("map updated");
        commandRepository.save(command);
        assertEquals(command, commandRepository.findByCommandId(command.commandId()).orElseThrow());
        assertEquals(List.of(command), commandRepository.findByTurnId(active.turnId()));
    }

    @Test
    void reloadsPendingMovementRollAndInitialRulingFromPostgres() throws Exception {
        AdventureId adventureId = AdventureId.generate();
        SessionId sessionId = SessionId.generate();
        var ownerId = UUID.randomUUID();
        new PostgresAdventureRepository(dataSource).save(Adventure.create(adventureId, sessionId,
                new OwnerPlayerId(ownerId), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("start", null, null, null)));
        RuntimeTurn turn = new RuntimeTurn(UUID.randomUUID(), UUID.randomUUID(), adventureId, sessionId.value(),
                UUID.randomUUID(), 1, "move", new EvidencePack(List.of(), List.of(), List.of()),
                new RuntimePlan("scene", null, "judgment", "narration", null, List.of(), List.of()), null,
                new AdventureContext("scene", null, "move", "judgment"), List.of(), 0, List.of(), List.of()).asRequested();
        repository.save(turn);
        UUID commandId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        var check = new com.dndmaster.adventure.application.runtime.RuntimeCheckProposal(true, "확인 판정",
                "Athletics", UUID.randomUUID(), com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.RollMethod.PLAYER,
                "1d20", 2, 12, List.of("book:check"), "계속 진행", "다시 시도합니다.");
        var followUp = new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(commandId, operationId,
                hostileTokenId, turn.turnId(), com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED", 4, List.of(), null, List.of("HOSTILE_OBSERVED")).afterPlayerRoll(check, 14);
        ObjectMapper mapper = new ObjectMapper();
        String rollPayload = mapper.writeValueAsString(followUp);
        var ruling = new com.dndmaster.adventure.application.runtime.CombatContinuationCommand(commandId,
                turn.turnId(), operationId, hostileTokenId, "HOSTILE_OBSERVED",
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "판정 대기", "굴림을 제출해 주세요.", check, true, false);
        String initialRuling = mapper.writeValueAsString(ruling);
        RuntimeTurnCommand pending = RuntimeTurnCommand.create(turn.turnId(), commandId, adventureId.value(),
                sessionId.value(), ownerId, "external", "movement.follow-up", rollPayload, 1)
                .movementInterventionPending(initialRuling);
        commandRepository.save(pending);

        RuntimeTurnCommandRepository reloadedRepository = new PostgresRuntimeTurnCommandRepository(dataSource);
        RuntimeTurnCommand restored = reloadedRepository.findPendingMovementByAdventure(adventureId.value()).getFirst();
        assertEquals(RuntimeTurnCommand.ExecutionStatus.GM_INTERVENTION_PENDING, restored.executionStatus());
        assertEquals(commandId, restored.commandId());
        assertEquals(turn.turnId(), restored.turnId());
        assertEquals(rollPayload, restored.payloadJson());
        assertEquals(initialRuling, restored.outcomeJson());
        assertEquals(1, restored.attemptCount());
        assertEquals(followUp, mapper.readValue(restored.payloadJson(),
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.class));
        assertEquals(ruling, mapper.readValue(restored.outcomeJson(),
                com.dndmaster.adventure.application.runtime.CombatContinuationCommand.class));
    }

    @Test
    void reloadsPendingCombatActionRollAndTypedRulingThroughFreshRepository() throws Exception {
        AdventureId adventureId = AdventureId.generate();
        SessionId sessionId = SessionId.generate();
        UUID ownerId = UUID.randomUUID();
        new PostgresAdventureRepository(dataSource).save(Adventure.create(adventureId, sessionId,
                new OwnerPlayerId(ownerId), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("start", null, null, null)));
        UUID encounterId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); var insert = connection.prepareStatement(
                "INSERT INTO combat_encounter(encounter_id, adventure_id, status, round, current_participant_id, version) VALUES (?, ?, 'ACTIVE', 1, ?, 7)")) {
            insert.setObject(1, encounterId);
            insert.setObject(2, adventureId.value());
            insert.setObject(3, actorId);
            insert.executeUpdate();
        }
        UUID operationId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        UUID mapOperationId = UUID.randomUUID();
        var check = new com.dndmaster.adventure.application.runtime.RuntimeCheckProposal(true, "확인 판정",
                "Athletics", UUID.randomUUID(), com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.RollMethod.PLAYER,
                "1d20", 2, 12, List.of("book:check"), "계속 진행", "다시 시도합니다.");
        var ruling = new com.dndmaster.adventure.application.runtime.CombatContinuationCommand(commandId, turnId,
                operationId, hostileTokenId, "HOSTILE_OBSERVED",
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "판정 대기", "굴림을 제출해 주세요.", check, true, false);
        var response = new com.dndmaster.adventure.application.combat.CombatActionResponse(encounterId, operationId,
                7, "GM_INTERVENTION_PENDING", null, "판정 대기", List.of(), "굴림을 제출해 주세요.", ruling);
        var followUp = new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(commandId, operationId,
                hostileTokenId, turnId, com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED", 4, List.of(), null, List.of("HOSTILE_OBSERVED")).afterPlayerRoll(check, 14);
        var operation = new com.dndmaster.adventure.application.combat.CombatActionOperation(commandId, "fingerprint",
                encounterId, actorId, new com.dndmaster.adventure.domain.combat.TurnResourceCost(4, false, false, false),
                List.of(new com.dndmaster.adventure.application.combat.CombatActionStep("map", mapOperationId.toString(),
                        com.dndmaster.adventure.application.combat.CombatActionStep.Status.DONE)));
        operation.awaitingMovementAdjudication(response, followUp);
        operation.recordDiceTotal(14);
        new PostgresCombatActionOperationRepository(dataSource, new ObjectMapper()).save(operation);

        var restored = new PostgresCombatActionOperationRepository(dataSource, new ObjectMapper())
                .findPendingMovementForEncounter(encounterId).orElseThrow();
        assertEquals(com.dndmaster.adventure.application.combat.CombatActionOperation.Status.GM_INTERVENTION_PENDING,
                restored.status());
        assertEquals(14, restored.diceTotal());
        assertEquals(14, restored.movementFollowUp().playerRoll());
        assertEquals(check, restored.movementFollowUp().playerCheck());
        assertEquals(ruling, restored.response().movementAdjudication());
        assertEquals("굴림을 제출해 주세요.", restored.response().narration());
        assertEquals(mapOperationId, restored.mapOperationId());
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
