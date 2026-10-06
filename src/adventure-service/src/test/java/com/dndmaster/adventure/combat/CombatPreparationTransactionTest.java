package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService;
import com.dndmaster.adventure.application.combat.CombatWorkItem;
import com.dndmaster.adventure.application.combat.CombatWorkItemRepository;
import com.dndmaster.adventure.application.combat.CombatWorkItemScheduler;
import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.TurnResources;
import com.dndmaster.adventure.domain.runtime.GmInput;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCombatEncounterRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCombatWorkItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

class CombatPreparationTransactionTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Test
    void rolls_back_preparing_encounter_when_durable_work_enqueue_fails() throws Exception {
        DataSource dataSource = new SimpleDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        createSchema(dataSource);
        UUID adventureId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Adventure adventure = Adventure.create(new AdventureId(adventureId), new SessionId(UUID.randomUUID()),
                new OwnerPlayerId(playerId), new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(playerId), new AdventureContext("encounter", "threat", "ambush", null));
        var encounters = new PostgresCombatEncounterRepository(dataSource);
        var durableWork = new PostgresCombatWorkItemRepository(dataSource, new ObjectMapper());
        CombatWorkItemRepository failingWork = new DelegatingWorkRepository(durableWork) {
            @Override public void enqueue(CombatWorkItem item) {
                super.enqueue(item);
                throw new IllegalStateException("simulated failure after durable enqueue");
            }
        };
        var service = new CombatLifecycleApplicationService(encounters, null, new AdventureStore(adventure), null,
                null, null, failingWork, new CombatWorkItemScheduler(failingWork, 10),
                new DataSourceTransactionManager(dataSource));
        var identity = new EnemyCharacterSheetIdentity(adventureId, UUID.randomUUID(), 1, UUID.randomUUID(),
                List.of(UUID.randomUUID()), "goblin");
        var request = new EnemySheetPreparationRequest(UUID.randomUUID(), adventureId,
                List.of(new EnemySheetPreparationRequest.Enemy(identity,
                        new CombatEnemyProposal("scene", "goblin", "Goblin", 1))));
        var participants = List.of(new CombatParticipant(UUID.randomUUID(), "Goblin", CombatParticipant.Controller.AI,
                12, "enemy", TurnResources.initial(), null, null, "goblin"));

        assertThrows(IllegalStateException.class, () -> service.startPreparingFromCommittedGmTurn(adventureId,
                committedTurn(), participants, request));

        assertEquals(0, count(dataSource, "combat_encounter"));
        assertEquals(0, count(dataSource, "combat_work_item"));
    }

    private static GmTurn committedTurn() {
        return GmTurn.start(UUID.randomUUID(), UUID.randomUUID(), 0, new GmInput.TextInput("start combat"))
                .process().commit("provider");
    }

    private static long count(DataSource source, String table) throws Exception {
        try (Connection connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void createSchema(DataSource source) throws Exception {
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS combat_work_item");
            statement.execute("DROP TABLE IF EXISTS combat_narrative_position");
            statement.execute("DROP TABLE IF EXISTS combat_participant");
            statement.execute("DROP TABLE IF EXISTS combat_encounter");
            statement.execute("CREATE TABLE combat_encounter (encounter_id UUID PRIMARY KEY, adventure_id UUID NOT NULL, status TEXT NOT NULL, round INT NOT NULL, current_participant_id UUID NOT NULL, version BIGINT NOT NULL, event_cursor BIGINT NOT NULL, pending_reaction_id UUID, pending_reaction_trigger TEXT, pending_reaction_actor_id UUID, pending_reaction_operation_id UUID, pending_reaction_resume_step TEXT, pending_reaction_options JSONB)");
            statement.execute("CREATE TABLE combat_participant (encounter_id UUID NOT NULL, participant_id UUID NOT NULL, display_name TEXT NOT NULL, controller TEXT NOT NULL, initiative INT NOT NULL, public_condition TEXT, movement_remaining INT NOT NULL DEFAULT 30, action_available BOOLEAN NOT NULL DEFAULT TRUE, bonus_action_available BOOLEAN NOT NULL DEFAULT TRUE, reaction_available BOOLEAN NOT NULL DEFAULT TRUE, stat_block_json JSONB, current_hit_points INT, enemy_kind TEXT, PRIMARY KEY (encounter_id, participant_id))");
            statement.execute("CREATE TABLE combat_narrative_position (encounter_id UUID NOT NULL, subject_id UUID NOT NULL, target_id UUID NOT NULL, range_band TEXT NOT NULL, cover TEXT NOT NULL, PRIMARY KEY (encounter_id, subject_id, target_id))");
            statement.execute("CREATE TABLE combat_work_item (work_item_id UUID PRIMARY KEY, encounter_id UUID NOT NULL REFERENCES combat_encounter(encounter_id), operation_id UUID, ai_request_id UUID, expected_encounter_version BIGINT NOT NULL, work_type TEXT NOT NULL, due_at TIMESTAMPTZ NOT NULL, attempt_count INT NOT NULL, status TEXT NOT NULL, lease_token UUID, worker_id TEXT, lease_until TIMESTAMPTZ, failure TEXT, tactical_instruction TEXT NOT NULL, tactical_constraints JSONB NOT NULL, command_json JSONB, completed_steps INT NOT NULL, enemy_sheet_preparation_request JSONB, decision_plan JSONB)");
        }
    }

    private static class DelegatingWorkRepository implements CombatWorkItemRepository {
        private final CombatWorkItemRepository delegate;
        private DelegatingWorkRepository(CombatWorkItemRepository delegate) { this.delegate = delegate; }
        @Override public void enqueue(CombatWorkItem item) { delegate.enqueue(item); }
        @Override public Optional<CombatWorkItem> claim(String worker, java.time.Duration lease, java.time.Instant now) {
            return delegate.claim(worker, lease, now);
        }
        @Override public void save(CombatWorkItem item) { delegate.save(item); }
        @Override public Optional<CombatWorkItem> findByOperationId(UUID operationId) { return delegate.findByOperationId(operationId); }
        @Override public Optional<CombatWorkItem> findFailedByEncounterId(UUID encounterId) { return delegate.findFailedByEncounterId(encounterId); }
        @Override public boolean hasPendingForEncounter(UUID encounterId) { return delegate.hasPendingForEncounter(encounterId); }
    }

    private record AdventureStore(Adventure adventure) implements AdventureRepository {
        @Override public Optional<Adventure> findById(AdventureId id) { return Optional.of(adventure); }
        @Override public List<Adventure> findSavedByOwner(OwnerPlayerId owner) { return List.of(adventure); }
        @Override public void save(Adventure value) { }
    }

    private record SimpleDataSource(String url, String username, String password) implements DataSource {
        @Override public Connection getConnection() throws java.sql.SQLException { return DriverManager.getConnection(url, username, password); }
        @Override public Connection getConnection(String user, String password) throws java.sql.SQLException { return DriverManager.getConnection(url, user, password); }
        @Override public <T> T unwrap(Class<T> type) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> type) { return false; }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
