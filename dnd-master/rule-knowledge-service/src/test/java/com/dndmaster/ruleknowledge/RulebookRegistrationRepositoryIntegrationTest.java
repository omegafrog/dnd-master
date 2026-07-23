package com.dndmaster.ruleknowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.ruleknowledge.application.registration.RulebookRegistrationRepository;
import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class RulebookRegistrationRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("rule_knowledge")
            .withUsername("rule_knowledge")
            .withPassword("rule_knowledge");

    private static DataSource dataSource;
    private static RulebookRegistrationRepository repository;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new com.dndmaster.ruleknowledge.infrastructure.persistence.PostgresRulebookRegistrationRepository(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE rulebook_registration");
        }
    }

    @Test
    void concurrentSameOwnerSameHashSavesCollapseIntoOneRow() throws Exception {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        String contentHash = "same-hash";
        StoredRulebookRegistration first = registration(
                RulebookId.generate(), owner, "op-a", contentHash, "first.txt");
        StoredRulebookRegistration second = registration(
                RulebookId.generate(), owner, "op-b", contentHash, "second.txt");

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<StoredRulebookRegistration> firstResult = executor.submit(() -> saveAfterLatch(first, ready, start));
        Future<StoredRulebookRegistration> secondResult = executor.submit(() -> saveAfterLatch(second, ready, start));
        ready.await();
        start.countDown();

        StoredRulebookRegistration savedFirst = firstResult.get();
        StoredRulebookRegistration savedSecond = secondResult.get();
        executor.shutdownNow();

        assertThat(countRows()).isEqualTo(1);
        assertThat(savedFirst.rulebookId()).isEqualTo(savedSecond.rulebookId());
        StoredRulebookRegistration persisted = repository.findByOwnerAndContentHash(owner, contentHash).orElseThrow();
        assertThat(persisted.rulebookId()).isEqualTo(savedFirst.rulebookId());
        assertThat(persisted.operationKey()).contains("op-a");
        assertThat(persisted.operationKey()).contains("op-b");
    }

    private StoredRulebookRegistration saveAfterLatch(
            StoredRulebookRegistration registration, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return repository.save(registration);
    }

    private long countRows() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery("SELECT COUNT(*) FROM rulebook_registration")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static StoredRulebookRegistration registration(
            RulebookId rulebookId, OwnerPlayerId owner, String operationKey, String contentHash, String originalFilename) {
        Instant timestamp = Instant.parse("2026-07-23T00:00:00Z");
        return new StoredRulebookRegistration(
                rulebookId,
                owner,
                operationKey,
                contentHash,
                RulebookFormat.TXT,
                1L,
                rulebookId.value().toString(),
                ProcessingStatus.QUEUED,
                null,
                null,
                List.of(),
                null,
                0L,
                timestamp,
                timestamp,
                DocumentType.RULEBOOK,
                originalFilename);
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override
        public Connection getConnection() throws java.sql.SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String suppliedUsername, String suppliedPassword)
                throws java.sql.SQLException {
            return DriverManager.getConnection(url, suppliedUsername, suppliedPassword);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
            throw new java.sql.SQLException("unwrap is not supported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }
}
