package com.dndmaster.aigamemaster.localcodex;

import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionResult;
import java.util.Objects;

/** Checks this installation's link immediately before each Codex provider request. */
public final class InstallationGuardedAiExecutionPort implements AiExecutionPort {
    private final AiExecutionPort delegate;
    private final CodexConnectionService connection;

    public InstallationGuardedAiExecutionPort(AiExecutionPort delegate, CodexConnectionService connection) {
        this.delegate = Objects.requireNonNull(delegate, "provider port is required");
        this.connection = Objects.requireNonNull(connection, "connection service is required");
    }

    @Override
    public AiExecutionResult execute(AiExecutionRequest request) {
        return connection.executeIfConnected(() -> delegate.execute(request));
    }
}
