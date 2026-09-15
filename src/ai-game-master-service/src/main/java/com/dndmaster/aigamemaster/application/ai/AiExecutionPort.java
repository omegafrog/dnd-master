package com.dndmaster.aigamemaster.application.ai;

@FunctionalInterface
public interface AiExecutionPort {
    AiExecutionResult execute(AiExecutionRequest request);
}
