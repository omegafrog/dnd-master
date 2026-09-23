package com.dndmaster.aigamemaster.application.ai;

public record AiExecutionSuccess(String finalText, AiExecutionUsage usage) implements AiExecutionResult {
    public AiExecutionSuccess(String finalText) { this(finalText, AiExecutionUsage.unknown()); }
    public AiExecutionSuccess {
        if (finalText == null || finalText.isBlank()) throw new IllegalArgumentException("final text is required");
        finalText = finalText.trim();
        usage = usage == null ? AiExecutionUsage.unknown() : usage;
    }
}
