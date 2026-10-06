package com.dndmaster.aigamemaster.application.endpoint;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ProviderConnectionApplicationService {
    private final RemoteProviderConnectionPort connectionPort;

    public ProviderConnectionApplicationService(RemoteProviderConnectionPort connectionPort) {
        this.connectionPort = connectionPort;
    }

    public ProviderConnectionState getStatus(ConnectionContext context) {
        return connectionPort.getStatus(context, UUID.randomUUID());
    }

    public ConnectionOperationState start(ConnectionContext context, ConnectionOperationType type) {
        UUID operationId = UUID.randomUUID();
        return connectionPort.startOperation(context, UUID.randomUUID(), operationId, type);
    }

    public ConnectionOperationState getOperation(ConnectionContext context, UUID operationId) {
        return connectionPort.getOperation(context, UUID.randomUUID(), operationId);
    }

    public ProviderConnectionState disconnect(ConnectionContext context) {
        return connectionPort.disconnect(context, UUID.randomUUID());
    }
}
