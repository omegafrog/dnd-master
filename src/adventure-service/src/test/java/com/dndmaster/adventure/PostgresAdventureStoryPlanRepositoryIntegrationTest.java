package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureStoryPlanRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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

class PostgresAdventureStoryPlanRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure")
            .withUsername("adventure")
            .withPassword("adventure");
    private static DataSource dataSource;
    private SessionId sessionId;
    private PostgresAdventureStoryPlanRepository repository;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @BeforeEach
    void resetDatabase() throws SQLException {
        sessionId = new SessionId(UUID.randomUUID());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE adventure_story_plan_history, adventure_story_plan, adventure_session CASCADE");
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO adventure_session(session_id, owner_player_id, scenario_package_id, scenario_package_revision, character_limit, version, blueprint_id, blueprint_revision, status, character_edition) VALUES (?, ?, ?, 1, 2, 0, ?, 1, 'DRAFT', 'DND_5E_2014')")) {
            statement.setObject(1, sessionId.value());
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, UUID.randomUUID());
            statement.setObject(4, UUID.randomUUID());
            statement.executeUpdate();
        }
        repository = new PostgresAdventureStoryPlanRepository(dataSource);
    }

    @Test
    void savesAndReadsBlockedPlanAndHistory() {
        AdventureStoryPlan blocked = AdventureStoryPlan.blocked(
                UUID.randomUUID(), sessionId, 1, 0, 1,
                new AdventurePlanConfiguration(2, AdventureLength.STANDARD),
                List.of(), "tactical scene generation failed");

        repository.save(blocked);

        AdventureStoryPlan loaded = repository.findBySessionId(sessionId).orElseThrow();
        assertEquals(AdventureStoryPlanStatus.BLOCKED, loaded.status());
        assertEquals("tactical scene generation failed", loaded.failureReason());
        assertEquals(AdventureStoryPlanStatus.BLOCKED, repository.readHistory(sessionId).getFirst().status());
    }

    @Test
    void linksPredecessorByLowerPlanVersionEvenWhenRecordedTimesAreSkewed() throws SQLException {
        AdventureStoryPlan first = AdventureStoryPlan.blocked(UUID.randomUUID(), sessionId, 1, 0, 1,
                new AdventurePlanConfiguration(2, AdventureLength.STANDARD), List.of(), "first");
        repository.save(first);
        AdventureStoryPlan third = AdventureStoryPlan.rehydrate(first.planId(), sessionId, first.packageRevision(), first.partyRevision(), 3,
                AdventureStoryPlanStatus.BLOCKED, first.configuration(), first.stages(), first.currentStage(), "third", first.updatedAt().plusSeconds(-100));
        repository.save(third, "GM_TURN:" + UUID.randomUUID());
        AdventureStoryPlan fourth = AdventureStoryPlan.rehydrate(first.planId(), sessionId, first.packageRevision(), first.partyRevision(), 4,
                AdventureStoryPlanStatus.BLOCKED, first.configuration(), first.stages(), first.currentStage(), "fourth", first.updatedAt().plusSeconds(-200));
        repository.save(fourth, "GM_TURN:" + UUID.randomUUID());

        var history = repository.readHistoryEntries(sessionId);
        assertEquals(3, history.size());
        assertNotNull(history.get(1).historyId());
        assertEquals(history.get(1).historyId(), history.get(2).predecessorHistoryId());
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
