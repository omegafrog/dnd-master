package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureSessionRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresAdventureAiRequestIntegrationTest {
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

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE adventure_session CASCADE");
        }
    }

    @Test
    void accepts_only_one_of_two_simultaneous_requests_for_the_same_session() throws Exception {
        Fixture fixture = seedStartedSession();
        UUID firstRequestId = UUID.randomUUID();
        UUID secondRequestId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                await(start);
                return fixture.repository().tryAcquireAiRequest(fixture.sessionId(), fixture.owner(), firstRequestId);
            });
            var second = executor.submit(() -> {
                await(start);
                return fixture.repository().tryAcquireAiRequest(fixture.sessionId(), fixture.owner(), secondRequestId);
            });
            start.countDown();

            long accepted = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, accepted);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void only_the_matching_request_id_can_release_the_session() {
        Fixture fixture = seedStartedSession();
        UUID acceptedRequestId = UUID.randomUUID();

        assertTrue(fixture.repository().tryAcquireAiRequest(fixture.sessionId(), fixture.owner(), acceptedRequestId));
        assertFalse(fixture.repository().releaseAiRequest(
                fixture.sessionId(), fixture.owner(), UUID.randomUUID()));
        assertEquals(acceptedRequestId,
                fixture.repository().findById(fixture.sessionId()).orElseThrow().activeAiRequestId());
        assertTrue(fixture.repository().releaseAiRequest(fixture.sessionId(), fixture.owner(), acceptedRequestId));
        assertNull(fixture.repository().findById(fixture.sessionId()).orElseThrow().activeAiRequestId());
    }

    private Fixture seedStartedSession() {
        AdventureSessionRepository repository = new PostgresAdventureSessionRepository(dataSource);
        SessionId sessionId = SessionId.generate();
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        UUID packageId = UUID.randomUUID();
        AdventureSession session = AdventureSession.rehydrate(sessionId, owner, packageId, 1, packageId, 1,
                "DND_5E_2014", 1, List.of(), null, AdventureSession.Status.STARTED,
                AdventureId.generate(), UUID.randomUUID(), 0);
        repository.save(session, 0);
        return new Fixture(repository, sessionId, owner);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(AdventureSessionRepository repository, SessionId sessionId, OwnerPlayerId owner) { }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, username, password); }
        @Override public Connection getConnection(String user, String pass) throws SQLException { return DriverManager.getConnection(url, user, pass); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("unwrap unsupported"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
