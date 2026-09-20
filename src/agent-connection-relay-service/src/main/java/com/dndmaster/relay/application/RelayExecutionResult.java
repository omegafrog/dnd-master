package com.dndmaster.relay.application;

public record RelayExecutionResult(String requestId, String content, RelayFailureType failureType) {
    public RelayExecutionResult {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        content = content == null ? "" : content;
    }
    public static RelayExecutionResult success(String requestId, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        return new RelayExecutionResult(requestId, content, null);
    }
    public static RelayExecutionResult failure(String requestId, RelayFailureType type) {
        if (type == null) throw new IllegalArgumentException("failureType is required");
        return new RelayExecutionResult(requestId, "", type);
    }
    public boolean success() { return failureType == null; }
}
