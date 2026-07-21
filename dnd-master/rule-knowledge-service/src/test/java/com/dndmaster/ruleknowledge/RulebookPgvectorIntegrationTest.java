package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.api.SourceLocationResponse;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.IndexId;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.infrastructure.persistence.EmbeddedRulebookChunk;
import com.dndmaster.ruleknowledge.infrastructure.persistence.IndexMetadata;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PgvectorRuleSearchRepository;
import com.dndmaster.ruleknowledge.infrastructure.persistence.RuleVectorPersistenceException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
import org.testcontainers.utility.DockerImageName;

class RulebookPgvectorIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("rule_knowledge")
            .withUsername("rule_knowledge")
            .withPassword("rule_knowledge");

    private static DataSource dataSource;
    private static PgvectorRuleSearchRepository repository;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new PgvectorRuleSearchRepository(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE rulebook_vector_index CASCADE");
        }
    }

    @Test
    void searchAlwaysRestrictsOwnerAndSelectedRulebooksAndReturnsSourceLocation() {
        OwnerPlayerId owner = owner();
        OwnerPlayerId otherOwner = owner();
        RulebookId selectedRulebook = RulebookId.generate();
        RulebookId unselectedRulebook = RulebookId.generate();
        store(owner, selectedRulebook, "page 12", "selected rule", new float[] {1, 0, 0});
        store(owner, unselectedRulebook, "page 99", "unselected rule", new float[] {1, 0, 0});
        store(otherOwner, selectedRulebook, "page 77", "other owner's rule", new float[] {1, 0, 0});

        var hits = repository.search(owner, List.of(selectedRulebook), new float[] {1, 0, 0}, 10);

        assertEquals(1, hits.size());
        assertEquals("selected rule", hits.getFirst().content());
        assertEquals(
                new SourceLocationResponse(selectedRulebook.value(), "page 12"),
                SourceLocationResponse.from(hits.getFirst().rulebookId(), hits.getFirst().locator()));
    }

    @Test
    void failedChunkBatchRollsBackIndexAndAllChunks() throws SQLException {
        OwnerPlayerId owner = owner();
        RulebookId rulebookId = RulebookId.generate();
        IndexMetadata metadata = metadata(owner, rulebookId);
        ChunkId duplicateId = new ChunkId(UUID.randomUUID());
        RulebookChunk first = chunk(rulebookId, duplicateId, 0, "first");
        RulebookChunk duplicate = chunk(rulebookId, duplicateId, 1, "second");

        assertThrows(
                RuleVectorPersistenceException.class,
                () -> repository.storeReadyIndex(
                        metadata,
                        List.of(
                                new EmbeddedRulebookChunk(first, "page 1", new float[] {1, 0, 0}),
                                new EmbeddedRulebookChunk(duplicate, "page 2", new float[] {0, 1, 0}))));

        assertEquals(0, countRows("rulebook_vector_index"));
        assertEquals(0, countRows("rulebook_vector_chunk"));
    }

    private static void store(
            OwnerPlayerId owner,
            RulebookId rulebookId,
            String locator,
            String content,
            float[] embedding) {
        RulebookChunk chunk = chunk(rulebookId, new ChunkId(UUID.randomUUID()), 0, content);
        repository.storeReadyIndex(
                metadata(owner, rulebookId), List.of(new EmbeddedRulebookChunk(chunk, locator, embedding)));
    }

    private static IndexMetadata metadata(OwnerPlayerId owner, RulebookId rulebookId) {
        return new IndexMetadata(IndexId.generate(), rulebookId, owner, "mock-embedding", 3, UUID.randomUUID().toString());
    }

    private static RulebookChunk chunk(RulebookId rulebookId, ChunkId chunkId, int sequence, String content) {
        return new RulebookChunk(
                rulebookId, chunkId, sequence, new ExtractedContentRange(0, content.length()), content, null, null);
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.randomUUID());
    }

    private static long countRows(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String suppliedUsername, String suppliedPassword) throws SQLException {
            return DriverManager.getConnection(url, suppliedUsername, suppliedPassword);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unwrap is not supported");
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
