package com.dndmaster.ruleknowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RuleSetSaveIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedRulebookRegistration() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rulebook_registration (
                    rulebook_id UUID PRIMARY KEY,
                    owner_player_id UUID NOT NULL,
                    operation_key TEXT NOT NULL,
                    content_hash TEXT NOT NULL,
                    format TEXT NOT NULL,
                    file_size BIGINT NOT NULL,
                    storage_key TEXT NOT NULL,
                    processing_status TEXT NOT NULL,
                    extraction_status TEXT,
                    extracted_content TEXT,
                    missing_locations VARCHAR ARRAY,
                    failure_code TEXT,
                    version BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    document_type TEXT NOT NULL,
                    original_filename TEXT NOT NULL,
                    preview_content TEXT NOT NULL DEFAULT '',
                    preview_warnings VARCHAR ARRAY,
                    preview_spans TEXT NOT NULL DEFAULT '[]',
                    preview_assets TEXT NOT NULL DEFAULT '[]',
                    preprocessing_operation_id TEXT,
                    candidate_extraction_version TEXT,
                    preprocessing_policy_version TEXT,
                    preprocessing_manifest_sha256 TEXT,
                    preprocessing_pages TEXT NOT NULL DEFAULT '[]'
                )
                """);
        jdbcTemplate.update("DELETE FROM rulebook_registration");

        UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID knowledgeDocumentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        jdbcTemplate.update(
                """
                INSERT INTO rulebook_registration (
                    rulebook_id, owner_player_id, operation_key, content_hash, format, file_size,
                    storage_key, processing_status, extraction_status, extracted_content,
                    missing_locations, failure_code, version, created_at, updated_at,
                    document_type, original_filename, preview_content, preview_warnings,
                    preview_spans, preview_assets
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                knowledgeDocumentId,
                ownerId,
                "op-1",
                "hash-1",
                "PDF",
                1L,
                "storage-1",
                "INDEXED",
                null,
                null,
                null,
                null,
                0L,
                Instant.parse("2026-07-23T00:00:00Z"),
                Instant.parse("2026-07-23T00:00:00Z"),
                "RULEBOOK",
                "phb.pdf",
                "",
                null,
                "[]",
                "[]");
    }

    @Test
    void saves_rule_set_for_owned_knowledge_documents() {
        UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID knowledgeDocumentId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        var response = http.exchange(
                url("/api/v1/rulebooks/rule-set"),
                HttpMethod.POST,
                new HttpEntity<>(new RuleSetSaveRequest(List.of(knowledgeDocumentId)), headers),
                Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private record RuleSetSaveRequest(List<UUID> knowledgeDocumentIds) {}
}
