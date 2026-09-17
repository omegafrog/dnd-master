package com.dndmaster.aigamemaster.application.ai;

public record AiExecutionSuccess(String finalText) implements AiExecutionResult {
    public AiExecutionSuccess {
        if (finalText == null || finalText.isBlank()) throw new IllegalArgumentException("final text is required");
        finalText = finalText.trim();
    }
}
