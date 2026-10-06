package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.infrastructure.persistence.PostgresCombatEncounterRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import com.dndmaster.adventure.domain.combat.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresCombatEncounterRepositoryTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private DataSource dataSource;

    @BeforeAll
    static void startDatabase() { POSTGRES.start(); }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @BeforeEach
    void createSchema() throws Exception {
        dataSource = new SimpleDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS combat_narrative_position");
            statement.execute("DROP TABLE IF EXISTS combat_participant");
            statement.execute("DROP TABLE IF EXISTS combat_encounter");
            statement.execute("CREATE TABLE combat_encounter (encounter_id UUID PRIMARY KEY, adventure_id UUID NOT NULL, status TEXT NOT NULL, round INT NOT NULL, current_participant_id UUID NOT NULL, version BIGINT NOT NULL, event_cursor BIGINT NOT NULL, pending_reaction_id UUID, pending_reaction_trigger TEXT, pending_reaction_actor_id UUID, pending_reaction_operation_id UUID, pending_reaction_resume_step TEXT, pending_reaction_options JSONB)");
            statement.execute("CREATE TABLE combat_participant (encounter_id UUID NOT NULL, participant_id UUID NOT NULL, display_name TEXT NOT NULL, controller TEXT NOT NULL, initiative INT NOT NULL, public_condition TEXT, movement_remaining INT NOT NULL DEFAULT 30, action_available BOOLEAN NOT NULL DEFAULT TRUE, bonus_action_available BOOLEAN NOT NULL DEFAULT TRUE, reaction_available BOOLEAN NOT NULL DEFAULT TRUE, stat_block_json JSONB, current_hit_points INT, enemy_kind TEXT, PRIMARY KEY (encounter_id, participant_id))");
            statement.execute("CREATE TABLE combat_narrative_position (encounter_id UUID NOT NULL, subject_id UUID NOT NULL, target_id UUID NOT NULL, range_band TEXT NOT NULL, cover TEXT NOT NULL, PRIMARY KEY (encounter_id, subject_id, target_id))");
        }
    }

    @Test
    void loads_persisted_encounter_after_loading_its_participants() throws Exception {
        UUID encounterId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); var encounter = connection.prepareStatement(
                "INSERT INTO combat_encounter(encounter_id, adventure_id, status, round, current_participant_id, version, event_cursor) VALUES (?, ?, 'ACTIVE', 1, ?, 3, 1)");
             var participant = connection.prepareStatement(
                     "INSERT INTO combat_participant VALUES (?, ?, 'Hero', 'PLAYER', 12, NULL, 30, TRUE, TRUE, TRUE, NULL, NULL)")) {
            encounter.setObject(1, encounterId); encounter.setObject(2, adventureId); encounter.setObject(3, participantId);
            encounter.executeUpdate();
            participant.setObject(1, encounterId); participant.setObject(2, participantId);
            participant.executeUpdate();
        }

        var loaded = new PostgresCombatEncounterRepository(dataSource).findActive(adventureId);

        assertTrue(loaded.isPresent());
        assertEquals(encounterId, loaded.orElseThrow().encounterId());
        assertEquals(1, loaded.orElseThrow().participants().size());
    }

    @Test
    void persists_enemy_kind_on_preparing_participant_for_restart_activation() {
        UUID encounterId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID enemyId = UUID.randomUUID();
        var repository = new PostgresCombatEncounterRepository(dataSource);
        var preparing = new CombatEncounter(encounterId, adventureId, CombatEncounter.Status.PREPARING, 1,
                enemyId, List.of(new CombatParticipant(enemyId, "Goblin", CombatParticipant.Controller.AI, 12,
                "enemy", TurnResources.initial(), null, null, "goblin")), 1, 0);

        repository.save(preparing);

        assertEquals("goblin", repository.findByEncounterId(encounterId).orElseThrow().participants().getFirst().enemyKind());
    }

    @Test
    void turn_and_resources_are_visible_together_while_next_turn_is_saved() throws Exception {
        UUID encounterId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID enemyId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        var repository = new PostgresCombatEncounterRepository(dataSource);
        var original = new CombatEncounter(encounterId, adventureId, CombatEncounter.Status.ACTIVE, 1,
                enemyId, List.of(new CombatParticipant(enemyId, "쥐", CombatParticipant.Controller.AI, 20, null),
                new CombatParticipant(playerId, "마법사", CombatParticipant.Controller.PLAYER, 10, null,
                        new TurnResources(30, false, true, true))), 1, 1);
        repository.save(original);
        CountDownLatch headerWritten = new CountDownLatch(1);
        CountDownLatch continueSave = new CountDownLatch(1);
        DataSource pausedWrites = new org.springframework.jdbc.datasource.DelegatingDataSource(dataSource) {
            @Override public Connection getConnection() throws java.sql.SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    try {
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && sql.startsWith("UPDATE combat_participant")) {
                            headerWritten.countDown();
                            if (!continueSave.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("저장 대기 시간을 초과했습니다");
                        }
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
            }
        };
        var executor = Executors.newSingleThreadExecutor();
        var saved = executor.submit(() -> new PostgresCombatEncounterRepository(pausedWrites)
                .save(original.endCurrentTurn(1), 1));
        try {
            assertTrue(headerWritten.await(10, TimeUnit.SECONDS));
            var duringSave = repository.findActive(adventureId).orElseThrow();
            assertEquals(enemyId, duringSave.currentParticipantId());
            assertEquals(1, duringSave.round());
            assertTrue(duringSave.currentParticipant().resources().actionAvailable());
        } finally {
            continueSave.countDown();
            try { saved.get(10, TimeUnit.SECONDS); }
            finally { executor.shutdownNow(); }
        }
        var afterSave = repository.findActive(adventureId).orElseThrow();
        assertEquals(playerId, afterSave.currentParticipantId());
        assertTrue(afterSave.currentParticipant().resources().actionAvailable());
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
