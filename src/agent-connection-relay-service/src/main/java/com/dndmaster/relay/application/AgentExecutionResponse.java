package com.dndmaster.relay.application;

/** 최종 실행 결과를 사용자 PC 에이전트에서 중계 서비스로 전달하는 응답. */
public record AgentExecutionResponse(String requestId, String content, RelayFailureType failureType) {

    public AgentExecutionResponse {
        requestId = required(requestId, "requestId");
        content = content == null ? "" : content;
    }

    public static AgentExecutionResponse success(String requestId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        return new AgentExecutionResponse(requestId, content, null);
    }

    public static AgentExecutionResponse failure(String requestId, RelayFailureType failureType) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType is required");
        }
        return new AgentExecutionResponse(requestId, "", failureType);
    }

    public boolean success() {
        return failureType == null;
    }

    public RelayExecutionResult toRelayExecutionResult() {
        return new RelayExecutionResult(requestId, content, failureType);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
