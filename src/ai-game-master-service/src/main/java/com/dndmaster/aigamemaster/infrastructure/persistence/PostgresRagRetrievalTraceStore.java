package com.dndmaster.aigamemaster.infrastructure.persistence;

import com.dndmaster.aigamemaster.application.ports.RagRetrievalTraceStore;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL trace store for the exact retrieval inputs and outputs used by plan generation. */
public final class PostgresRagRetrievalTraceStore implements RagRetrievalTraceStore {
    private final JdbcTemplate jdbc;

    public PostgresRagRetrievalTraceStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void save(RagRetrievalTrace trace) {
        jdbc.update("""
                INSERT INTO rag_retrieval_trace
                    (operation_id, phase, tool_name, call_index, query, request_json,
                     raw_response_json, projected_response_json, projected_evidence_count)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                """,
                trace.operationId(), trace.phase(), trace.toolName(), trace.callIndex(), trace.query(),
                trace.requestJson(), trace.rawResponseJson(), trace.projectedResponseJson(),
                trace.projectedEvidenceCount());
    }
}
