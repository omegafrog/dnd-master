package com.dndmaster.aigamemaster.localcodex;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionResult;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexAppServerClient;
import java.nio.file.Path;
import java.time.Duration;

/** Development-only adapter preserving the existing local app-server protocol. */
public final class LocalCodexAiExecutionPort implements AiExecutionPort, AutoCloseable {
    private final CodexAppServerClient client;

    public LocalCodexAiExecutionPort(String executable, Path workDirectory, Duration timeout,
                                     com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.client = CodexAppServerClient.shared(executable, workDirectory, timeout, mapper);
    }

    @Override
    public AiExecutionResult execute(AiExecutionRequest request) {
        try {
            return new AiExecutionSuccess(client.complete(request.requestId(), request.completedPrompt(), request.model(),
                    request.reasoning(), request.outputSchema(), request.imageDataUri()));
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.CodexTurnTimeoutException timeout) {
            return new AiExecutionFailure(AiExecutionFailure.Reason.TIMEOUT, timeout.getMessage());
        } catch (RuntimeException failure) {
            return new AiExecutionFailure(AiExecutionFailure.Reason.LOCAL_EXECUTION_FAILED, failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    @Override public void close() { client.close(); }
}
