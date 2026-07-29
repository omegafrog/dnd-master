package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.indexing.IndexingCommand;
import com.dndmaster.ruleknowledge.application.indexing.RulebookIndexingApplicationService;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.IndexKey;
import com.dndmaster.ruleknowledge.domain.index.IndexStatus;
import com.dndmaster.ruleknowledge.domain.index.RulebookIndex;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.Rulebook;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.infrastructure.extraction.PdfRulebookContentExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OLLAMA_E2E", matches = "true")
class RulebookOllamaPostgresE2ETest {
    private static final Path RULEBOOK_PDF = Path.of(System.getenv().getOrDefault(
            "RULEBOOK_E2E_PDF", "/mnt/c/users/jiwoo/Downloads/dnd5th.pdf"));

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("rule_knowledge")
            .withUsername("rule_knowledge")
            .withPassword("rule_knowledge");

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("rule-knowledge.embedding-dimension", () -> 1024);
        registry.add("spring.ai.ollama.base-url", () -> "http://127.0.0.1:11434");
    }

    @Autowired RulebookIndexingApplicationService indexingService;
    @Autowired EmbeddingPort embeddingPort;
    @Autowired DataSource dataSource;

    @Test
    void indexesLargeRulebookWithRealOllamaAndPostgres() throws SQLException {
        assertTrue(Files.isRegularFile(RULEBOOK_PDF), "missing real PDF fixture: " + RULEBOOK_PDF);
        byte[] pdf = readPdf();
        ExtractionResult extraction = new PdfRulebookContentExtractor().extract(pdf);
        assertTrue(extraction.content().isPresent(), "real PDF extraction failed: " + extraction);

        RulebookId rulebookId = RulebookId.generate();
        Rulebook rulebook = Rulebook.acceptUpload(
                rulebookId,
                new OwnerPlayerId(UUID.randomUUID()),
                RulebookFormat.PDF,
                new FileSize(pdf.length));
        rulebook.recordExtraction(extraction);

        RulebookIndex result = indexingService.indexContent(new IndexingCommand(
                rulebook,
                new IndexKey(rulebookId, UUID.randomUUID().toString(), "ollama-embedding", "e2e"),
                1024));

        assertEquals(IndexStatus.READY, result.status());
        assertTrue(result.chunks().size() > 1);
        assertEquals(result.chunks().size(), countChunks());
    }

    private static byte[] readPdf() {
        try {
            return Files.readAllBytes(RULEBOOK_PDF);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("cannot read real PDF fixture: " + RULEBOOK_PDF, exception);
        }
    }

    private long countChunks() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM rulebook_vector_chunk")) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
