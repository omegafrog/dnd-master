package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinition;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionBatch;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionExtraction;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionQueryCoverage;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresRulebookActionDefinitionRepository implements RulebookActionDefinitionRepository {
    private static final TypeReference<List<RulebookActionDefinition>> DEFINITIONS = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<com.dndmaster.ruleknowledge.application.definition.RulebookActionCandidate>> CANDIDATES = new TypeReference<>() {};
    private static final TypeReference<List<com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionResult>> RESULTS = new TypeReference<>() {};
    private static final TypeReference<List<RulebookActionQueryCoverage>> COVERAGE = new TypeReference<>() {};
    private static final TypeReference<List<com.dndmaster.ruleknowledge.application.definition.RulebookActionDefinitionCoverage>> ACTION_COVERAGE = new TypeReference<>() {};
    private static final TypeReference<List<com.dndmaster.ruleknowledge.application.definition.RulebookActionSharedRule>> SHARED_RULES = new TypeReference<>() {};
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public PostgresRulebookActionDefinitionRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    @Override public Optional<RulebookActionDefinitionExtraction> find(UUID documentId, String extractionVersion) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT status,preprocessed_page_count,indexed_page_count,scanned_chunk_count,scanned_section_count,definitions_json::text,failure_code,contract_version,query_coverage_json::text,evidence_limit_reached,candidate_evidence_count,action_coverage_json::text,shared_rules_json::text FROM rulebook_action_definition_extraction WHERE document_id=? AND extraction_version=?")) {
            s.setObject(1, documentId); s.setString(2, extractionVersion);
            try (var rows = s.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new RulebookActionDefinitionExtraction(documentId, extractionVersion, rows.getString(1),
                        rows.getInt(2), rows.getInt(3), rows.getInt(4), rows.getInt(5),
                        read(rows.getString(6), DEFINITIONS), rows.getString(7), rows.getString(8),
                        read(rows.getString(9), COVERAGE), rows.getBoolean(10), rows.getInt(11), read(rows.getString(12), ACTION_COVERAGE),
                        read(rows.getString(13), SHARED_RULES)));
            }
        } catch (SQLException e) { throw new RuntimeException("could not load rulebook action definitions", e); }
    }

    @Override public void save(RulebookActionDefinitionExtraction extraction) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("INSERT INTO rulebook_action_definition_extraction(document_id,extraction_version,status,preprocessed_page_count,indexed_page_count,scanned_chunk_count,scanned_section_count,definitions_json,failure_code,contract_version,query_coverage_json,evidence_limit_reached,candidate_evidence_count,action_coverage_json,shared_rules_json,updated_at) VALUES (?,?,?,?,?,?,?,?::jsonb,?,?,?::jsonb,?,?,?::jsonb,?::jsonb,now()) ON CONFLICT(document_id,extraction_version) DO UPDATE SET status=EXCLUDED.status,preprocessed_page_count=EXCLUDED.preprocessed_page_count,indexed_page_count=EXCLUDED.indexed_page_count,scanned_chunk_count=EXCLUDED.scanned_chunk_count,scanned_section_count=EXCLUDED.scanned_section_count,definitions_json=EXCLUDED.definitions_json,failure_code=EXCLUDED.failure_code,contract_version=EXCLUDED.contract_version,query_coverage_json=EXCLUDED.query_coverage_json,evidence_limit_reached=EXCLUDED.evidence_limit_reached,candidate_evidence_count=EXCLUDED.candidate_evidence_count,action_coverage_json=EXCLUDED.action_coverage_json,shared_rules_json=EXCLUDED.shared_rules_json,updated_at=now()")) {
            s.setObject(1, extraction.documentId()); s.setString(2, extraction.extractionVersion()); s.setString(3, extraction.status());
            s.setInt(4, extraction.preprocessedPageCount()); s.setInt(5, extraction.indexedPageCount());
            s.setInt(6, extraction.scannedChunkCount()); s.setInt(7, extraction.scannedSectionCount());
            s.setString(8, json(extraction.definitions())); s.setString(9, extraction.failureCode()); s.setString(10, extraction.contractVersion());
            s.setString(11, json(extraction.queryCoverage())); s.setBoolean(12, extraction.evidenceLimitReached()); s.setInt(13, extraction.candidateEvidenceCount());
            s.setString(14, json(extraction.actionCoverage())); s.setString(15, json(extraction.sharedRules())); s.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("could not save rulebook action definitions", e); }
    }

    @Override public boolean claim(UUID documentId, String version, int pageCount, int indexedPageCount,
            int chunkCount, int sectionCount, String contractVersion) {
        String sql = "INSERT INTO rulebook_action_definition_extraction(document_id,extraction_version,status,preprocessed_page_count,indexed_page_count,scanned_chunk_count,scanned_section_count,contract_version) VALUES (?,?,'PROCESSING',?,?,?,?,?) ON CONFLICT(document_id,extraction_version) DO UPDATE SET shared_rules_json=CASE WHEN rulebook_action_definition_extraction.contract_version IS DISTINCT FROM EXCLUDED.contract_version THEN '[]'::jsonb ELSE rulebook_action_definition_extraction.shared_rules_json END,status='PROCESSING',preprocessed_page_count=EXCLUDED.preprocessed_page_count,indexed_page_count=EXCLUDED.indexed_page_count,scanned_chunk_count=EXCLUDED.scanned_chunk_count,scanned_section_count=EXCLUDED.scanned_section_count,failure_code=NULL,query_coverage_json='[]'::jsonb,action_coverage_json='[]'::jsonb,evidence_limit_reached=FALSE,candidate_evidence_count=0,contract_version=EXCLUDED.contract_version,updated_at=now() WHERE rulebook_action_definition_extraction.status='FAILED' OR rulebook_action_definition_extraction.contract_version IS DISTINCT FROM EXCLUDED.contract_version OR rulebook_action_definition_extraction.updated_at < now() - interval '30 minutes'";
        try (var c = dataSource.getConnection(); var s = c.prepareStatement(sql)) {
            s.setObject(1, documentId); s.setString(2, version); s.setInt(3, pageCount);
            s.setInt(4, indexedPageCount); s.setInt(5, chunkCount); s.setInt(6, sectionCount); s.setString(7, contractVersion);
            return s.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException("could not claim rulebook action extraction", e); }
    }

    @Override public Optional<RulebookActionDefinitionBatch> findBatch(UUID documentId, String version, int index) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT source_chunk_ids::text,status,definitions_json::text,failure_code,contract_version,candidates_json::text,results_json::text FROM rulebook_action_definition_batch WHERE document_id=? AND extraction_version=? AND batch_index=?")) {
            s.setObject(1, documentId); s.setString(2, version); s.setInt(3, index);
            try (var rows = s.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new RulebookActionDefinitionBatch(documentId, version, index,
                        read(rows.getString(1), STRINGS), rows.getString(2), read(rows.getString(3), DEFINITIONS), rows.getString(4), rows.getString(5), read(rows.getString(6), CANDIDATES), read(rows.getString(7), RESULTS)));
            }
        } catch (SQLException e) { throw new RuntimeException("could not load rulebook action extraction batch", e); }
    }

    @Override public void saveBatch(RulebookActionDefinitionBatch batch) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("INSERT INTO rulebook_action_definition_batch(document_id,extraction_version,batch_index,source_chunk_ids,status,definitions_json,failure_code,contract_version,candidates_json,results_json) VALUES (?,?,?,?::jsonb,?,?::jsonb,?,?,?::jsonb,?::jsonb) ON CONFLICT(document_id,extraction_version,batch_index) DO UPDATE SET source_chunk_ids=EXCLUDED.source_chunk_ids,status=EXCLUDED.status,definitions_json=EXCLUDED.definitions_json,failure_code=EXCLUDED.failure_code,contract_version=EXCLUDED.contract_version,candidates_json=EXCLUDED.candidates_json,results_json=EXCLUDED.results_json")) {
            s.setObject(1, batch.documentId()); s.setString(2, batch.extractionVersion()); s.setInt(3, batch.batchIndex());
            s.setString(4, json(batch.sourceChunkIds())); s.setString(5, batch.status());
            s.setString(6, json(batch.definitions())); s.setString(7, batch.failureCode()); s.setString(8, batch.contractVersion()); s.setString(9, json(batch.candidates())); s.setString(10, json(batch.results())); s.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("could not save rulebook action extraction batch", e); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("could not serialize rulebook action definitions", e); }
    }
    private <T> T read(String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("could not deserialize rulebook action definitions", e); }
    }
}
