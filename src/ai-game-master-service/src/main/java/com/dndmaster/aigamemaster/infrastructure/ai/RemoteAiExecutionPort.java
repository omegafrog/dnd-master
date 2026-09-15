package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionResult;

/** Temporary remote adapter until the connection relay is supplied by the next plan. */
public final class RemoteAiExecutionPort implements AiExecutionPort {
    @Override
    public AiExecutionResult execute(AiExecutionRequest request) {
        return new AiExecutionFailure(AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE,
                "no active user PC agent is connected for this Solo Player");
    }
}
