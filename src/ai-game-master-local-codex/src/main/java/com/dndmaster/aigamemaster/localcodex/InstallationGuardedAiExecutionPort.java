package com.dndmaster.aigamemaster.localcodex;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
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
        ConnectionStatus status = connection.getExecutionStatus();
        if (status.status() != ProviderConnectionStatus.CONNECTED) {
            AiExecutionFailure.Reason reason = switch (status.status()) {
                case CLI_UNAVAILABLE -> AiExecutionFailure.Reason.CONNECTION_UNAVAILABLE;
                case REAUTH_REQUIRED -> AiExecutionFailure.Reason.REAUTH_REQUIRED;
                default -> AiExecutionFailure.Reason.CONNECTION_REQUIRED;
            };
            return new AiExecutionFailure(reason, reason.name());
        }
        AiExecutionResult result = delegate.execute(request);
        if (result instanceof AiExecutionFailure failure
                && failure.reason() == AiExecutionFailure.Reason.REAUTH_REQUIRED) {
            connection.requireReauthentication();
        }
        return result;
    }
}
