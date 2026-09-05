package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.infrastructure.persistence.PostgresCombatEncounterRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
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
            statement.execute("DROP TABLE IF EXISTS combat_participant");
            statement.execute("DROP TABLE IF EXISTS combat_encounter");
            statement.execute("CREATE TABLE combat_encounter (encounter_id UUID PRIMARY KEY, adventure_id UUID NOT NULL, status TEXT NOT NULL, round INT NOT NULL, current_participant_id UUID NOT NULL, version BIGINT NOT NULL, event_cursor BIGINT NOT NULL)");
            statement.execute("CREATE TABLE combat_participant (encounter_id UUID NOT NULL, participant_id UUID NOT NULL, display_name TEXT NOT NULL, controller TEXT NOT NULL, initiative INT NOT NULL, public_condition TEXT, PRIMARY KEY (encounter_id, participant_id))");
        }
    }

    @Test
    void loads_persisted_encounter_after_loading_its_participants() throws Exception {
        UUID encounterId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection(); var encounter = connection.prepareStatement(
                "INSERT INTO combat_encounter VALUES (?, ?, 'ACTIVE', 1, ?, 3, 1)");
             var participant = connection.prepareStatement(
                     "INSERT INTO combat_participant VALUES (?, ?, 'Hero', 'PLAYER', 12, NULL)")) {
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
