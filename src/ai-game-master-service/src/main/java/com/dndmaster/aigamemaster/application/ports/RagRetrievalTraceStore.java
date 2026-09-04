package com.dndmaster.aigamemaster.application.ports;

/** Stores the evidence returned by RAG calls used during an AI operation. */
public interface RagRetrievalTraceStore {
    void save(RagRetrievalTrace trace);

    record RagRetrievalTrace(
            String operationId,
            String phase,
            String toolName,
            int callIndex,
            String query,
            String requestJson,
            String rawResponseJson,
            String projectedResponseJson,
            int projectedEvidenceCount) {
        public RagRetrievalTrace {
            operationId = required(operationId, "operationId");
            phase = required(phase, "phase");
            toolName = required(toolName, "toolName");
            query = required(query, "query");
            requestJson = required(requestJson, "requestJson");
            rawResponseJson = required(rawResponseJson, "rawResponseJson");
            projectedResponseJson = required(projectedResponseJson, "projectedResponseJson");
            if (callIndex < 1) throw new IllegalArgumentException("callIndex must be positive");
            if (projectedEvidenceCount < 0) throw new IllegalArgumentException("projected evidence count must not be negative");
        }

        private static String required(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
            return value;
        }
    }
}
