package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ConcurrentStateUpdateIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @BeforeEach
    void createRaceSchema() throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS race CASCADE");
            statement.execute("CREATE SCHEMA race");
            for (String table : List.of("adventure_state", "inquiry_state", "rulebook_state", "combat_token")) {
                statement.execute("CREATE TABLE race." + table
                        + " (id UUID PRIMARY KEY, state TEXT NOT NULL, version BIGINT NOT NULL CHECK(version>=0))");
            }
            statement.execute("CREATE TABLE race.worker_job (id UUID PRIMARY KEY, status TEXT NOT NULL, "
                    + "lease_owner TEXT, lease_until TIMESTAMPTZ, operation_key TEXT NOT NULL UNIQUE)");
        }
    }

    @Test
    void staleWritersReceive409AndNeverOverwriteWinningState() throws Exception {
        assertRace("adventure_state", "PROGRESSED", "DELETED");
        assertRace("inquiry_state", "ANSWERED", "CANDIDATE_SELECTED");
        assertRace("rulebook_state", "PARTIAL_CONFIRMED", "INDEXING");
        assertRace("combat_token", "MOVED_NORTH", "MOVED_EAST");
    }

    @Test
    void workersClaimDifferentJobsWithLeaseAndSkipLocked() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try (Connection setup = connection(); var statement = setup.prepareStatement(
                "INSERT INTO race.worker_job VALUES (?, 'PENDING', NULL, NULL, ?)")) {
            statement.setObject(1, first);
            statement.setString(2, "op-1");
            statement.executeUpdate();
            statement.setObject(1, second);
            statement.setString(2, "op-2");
            statement.executeUpdate();
        }

        try (Connection workerOne = connection(); Connection workerTwo = connection()) {
            workerOne.setAutoCommit(false);
            workerTwo.setAutoCommit(false);
            UUID claimedOne = claim(workerOne, "worker-1");
            UUID claimedTwo = claim(workerTwo, "worker-2");
            assertNotEquals(claimedOne, claimedTwo);
            workerOne.commit();
            workerTwo.commit();
        }

        try (Connection connection = connection(); var rows = connection.createStatement().executeQuery(
                "SELECT count(*) FROM race.worker_job WHERE lease_owner IS NOT NULL AND lease_until > now()")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void operationKeyHasDatabaseUniqueness() throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO race.worker_job VALUES ('" + UUID.randomUUID()
                    + "','PENDING',NULL,NULL,'same-operation')");
            assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO race.worker_job VALUES ('"
                    + UUID.randomUUID() + "','PENDING',NULL,NULL,'same-operation')"));
        }
    }

    private static void assertRace(String table, String firstState, String secondState) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection setup = connection(); var statement = setup.prepareStatement(
                "INSERT INTO race." + table + " VALUES (?, 'INITIAL', 0)")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> updateWithExpectedVersion(table, id, firstState, ready, start));
            var second = executor.submit(() -> updateWithExpectedVersion(table, id, secondState, ready, start));
            ready.await();
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(), second.get()));
            Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses);
        }

        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "SELECT state, version FROM race." + table + " WHERE id=?")) {
            statement.setObject(1, id);
            try (var row = statement.executeQuery()) {
                assertTrue(row.next());
                assertTrue(row.getString(1).equals(firstState) || row.getString(1).equals(secondState));
                assertEquals(1, row.getLong(2), "losing writer must not increment or overwrite state");
            }
        }
    }

    private static int updateWithExpectedVersion(
            String table, UUID id, String state, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            ready.countDown();
            start.await();
            try (var statement = connection.prepareStatement(
                    "UPDATE race." + table + " SET state=?, version=version+1 WHERE id=? AND version=?")) {
                statement.setString(1, state);
                statement.setObject(2, id);
                statement.setLong(3, 0);
                int updated = statement.executeUpdate();
                if (updated == 0) {
                    connection.rollback();
                    return OptimisticConflictException.HTTP_STATUS;
                }
                connection.commit();
                return 200;
            }
        }
    }

    private static UUID claim(Connection connection, String worker) throws Exception {
        UUID id;
        try (var select = connection.prepareStatement("""
                SELECT id FROM race.worker_job
                WHERE status='PENDING' AND (lease_until IS NULL OR lease_until < now())
                ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                """)) {
            try (var row = select.executeQuery()) {
                assertTrue(row.next());
                id = row.getObject(1, UUID.class);
            }
        }
        try (var update = connection.prepareStatement(
                "UPDATE race.worker_job SET lease_owner=?, lease_until=now()+interval '30 seconds' WHERE id=?")) {
            update.setString(1, worker);
            update.setObject(2, id);
            assertEquals(1, update.executeUpdate());
        }
        return id;
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static final class OptimisticConflictException extends RuntimeException {
        private static final int HTTP_STATUS = 409;
    }
}
