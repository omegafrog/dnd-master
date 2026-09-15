package com.dndmaster.aigamemaster.application.ai;

public sealed interface AiExecutionResult permits AiExecutionSuccess, AiExecutionFailure {
    String finalText();

    default String requireFinalText() {
        if (this instanceof AiExecutionSuccess success) return success.finalText();
        throw new AiExecutionUnavailableException((AiExecutionFailure) this);
    }
}
