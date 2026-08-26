package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndmaster.adventure.application.runtime.TacticalScenePreparationJobRepository.Status;
import com.dndmaster.adventure.infrastructure.persistence.PostgresTacticalScenePreparationJobRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class TacticalScenePreparationJobRepositoryIntegrationTest {
    @Test
    void persists_state_across_repository_instances_and_deduplicates_concurrent_creation() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            var first = new PostgresTacticalScenePreparationJobRepository(dataSource);
            UUID session = UUID.randomUUID(); UUID owner = UUID.randomUUID();
            var pool = Executors.newFixedThreadPool(2);
            Callable<?> create = () -> first.createOrGet(session, owner, 1, "지하 묘지", true);
            Future<?> one = pool.submit(create); Future<?> two = pool.submit(create);
            var jobOne = (com.dndmaster.adventure.application.runtime.TacticalScenePreparationJobRepository.Job) one.get();
            var jobTwo = (com.dndmaster.adventure.application.runtime.TacticalScenePreparationJobRepository.Job) two.get();
            pool.shutdownNow();
            assertEquals(jobOne.jobId(), jobTwo.jobId());
            assertEquals(Status.QUEUED, jobOne.status());
            assertEquals(true, first.claim(jobOne.jobId()));
            assertFalse(first.claim(jobOne.jobId()), "a claimed job must not execute twice");
            first.update(jobOne.jobId(), Status.FAILED_RETRYABLE, 100, 3, "재시도 필요", "AI 후보 검증 실패");

            var afterRestart = new PostgresTacticalScenePreparationJobRepository(dataSource);
            var restored = afterRestart.find(session, 1).orElseThrow();
            assertEquals(jobOne.jobId(), restored.jobId());
            assertEquals(Status.FAILED_RETRYABLE, restored.status());
            assertEquals(3, restored.attempts());
            assertEquals("AI 후보 검증 실패", restored.failureReason());
        }
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
