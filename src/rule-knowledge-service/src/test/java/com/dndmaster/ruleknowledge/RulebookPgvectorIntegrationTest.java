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
import com.dndmaster.ruleknowledge.domain.index.EmbeddedRulebookChunk;
import com.dndmaster.ruleknowledge.infrastructure.persistence.IndexMetadata;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PgvectorRuleSearchRepository;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PostgresRagExtractionPublicationRepository;
import com.dndmaster.ruleknowledge.infrastructure.persistence.PgvectorRuleEvidenceSearchRepository;
import com.dndmaster.ruleknowledge.application.search.QueryIntent;
import com.dndmaster.ruleknowledge.application.publication.EmbeddedPublishedRagChunk;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPage;
import com.dndmaster.ruleknowledge.application.publication.RagExtractionPublicationRequest;
import com.dndmaster.ruleknowledge.application.publication.PublishedRagChunk;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.infrastructure.persistence.RuleVectorPersistenceException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
    private static PostgresRagExtractionPublicationRepository publicationRepository;
    private static PgvectorRuleEvidenceSearchRepository evidenceRepository;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new PgvectorRuleSearchRepository(dataSource);
        publicationRepository = new PostgresRagExtractionPublicationRepository(dataSource);
        evidenceRepository = new PgvectorRuleEvidenceSearchRepository(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE rulebook_vector_index, rulebook_registration CASCADE");
        }
    }

    @Test
    void publishesOnlyIndexedCandidateAndKeepsPreviousPublicVersionOnCandidateFailure() throws SQLException {
        OwnerPlayerId owner = owner();
        RulebookId documentId = RulebookId.generate();
        register(documentId, owner);

        RagExtractionPublicationRequest first = publicationRequest(documentId, owner, "version-1", 1);
        publicationRepository.beginCandidate(first);
        publicationRepository.publish(first, List.of(publicationChunk(first, 1)));
        var publishedEvidence = evidenceRepository.search(
                owner, List.of(documentId), new float[] {1, 0, 0}, QueryIntent.RULE, 10).getFirst();
        assertEquals("page=1", publishedEvidence.locator());
        assertEquals(1, publishedEvidence.extractionVersion());
        assertEquals(1, publishedEvidence.provenance().pageNumber());
        assertEquals(List.of("Chapter", "version-1"), publishedEvidence.provenance().sectionPath());
        assertEquals("r1:c1", publishedEvidence.provenance().tableCell());

        RagExtractionPublicationRequest second = publicationRequest(documentId, owner, "version-2", 2);
        publicationRepository.beginCandidate(second);
        EmbeddedPublishedRagChunk invalidPage = publicationChunk(second, 3);
        assertThrows(RuntimeException.class, () -> publicationRepository.publish(second, List.of(invalidPage)));

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT published_extraction_version FROM rulebook_registration WHERE rulebook_id = ?")) {
            statement.setObject(1, documentId.value());
            try (ResultSet rows = statement.executeQuery()) {
                assertEquals(true, rows.next());
                assertEquals("version-1", rows.getString(1));
            }
        }
        assertEquals(1, countRows("published_rag_chunk"));
        assertEquals("page=1", evidenceRepository.search(
                owner, List.of(documentId), new float[] {1, 0, 0}, QueryIntent.RULE, 10).getFirst().locator());
    }

    private static RagExtractionPublicationRequest publicationRequest(
            RulebookId documentId, OwnerPlayerId owner, String version, int pageNumber) {
        return new RagExtractionPublicationRequest(
                documentId, owner, "operation-" + version, version, "a".repeat(64), "policy-1", "b".repeat(64),
                List.of(new RagExtractionPage(pageNumber, "VALIDATED", 1, List.of())),
                List.of(new PublishedRagChunk(
                        "processor-" + version, 0, "published content", "published content",
                        new SourceProvenance(pageNumber, List.of("Chapter", version), List.of(1d, 2d, 3d, 4d), "r1:c1", "page=" + pageNumber))),
                "mock-embedding");
    }

    private static EmbeddedPublishedRagChunk publicationChunk(
            RagExtractionPublicationRequest request, int pageNumber) {
        PublishedRagChunk source = request.chunks().getFirst();
        PublishedRagChunk withPage = new PublishedRagChunk(
                source.processorChunkId(), source.sequence(), source.content(), source.embeddingText(),
                new SourceProvenance(pageNumber, source.provenance().sectionPath(), source.provenance().bbox(),
                        source.provenance().tableCell(), source.provenance().originalLocator()));
        return new EmbeddedPublishedRagChunk(withPage, new float[] {1, 0, 0});
    }

    private static void register(RulebookId documentId, OwnerPlayerId owner) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO rulebook_registration
                    (rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                     storage_key, processing_status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PDF', 1, ?, 'QUEUED', 0, now(), now())
                """)) {
            statement.setObject(1, documentId.value());
            statement.setObject(2, owner.value());
            statement.setString(3, "registration-" + documentId.value());
            statement.setString(4, "c".repeat(64));
            statement.setString(5, "storage/" + documentId.value());
            statement.executeUpdate();
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
