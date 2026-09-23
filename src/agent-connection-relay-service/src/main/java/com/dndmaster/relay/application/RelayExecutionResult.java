package com.dndmaster.relay.application;

public record RelayExecutionResult(String requestId, String content, RelayFailureType failureType, RelayExecutionUsage usage) {
    public RelayExecutionResult(String requestId, String content, RelayFailureType failureType) {
        this(requestId, content, failureType, RelayExecutionUsage.unknown());
    }
    public RelayExecutionResult {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        content = content == null ? "" : content;
        usage = usage == null ? RelayExecutionUsage.unknown() : usage;
    }
    public static RelayExecutionResult success(String requestId, String content) {
        return success(requestId, content, RelayExecutionUsage.unknown());
    }
    public static RelayExecutionResult success(String requestId, String content, RelayExecutionUsage usage) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        return new RelayExecutionResult(requestId, content, null, usage);
    }
    public static RelayExecutionResult failure(String requestId, RelayFailureType type) {
        if (type == null) throw new IllegalArgumentException("failureType is required");
        return new RelayExecutionResult(requestId, "", type);
    }
    public boolean success() { return failureType == null; }
}
