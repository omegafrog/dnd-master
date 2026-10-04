package com.dndmaster.aigamemaster.application.endpoint;

import java.util.UUID;

public interface RemoteProviderConnectionPort {
    ProviderConnectionState getStatus(ConnectionContext context, UUID requestId);
    ConnectionOperationState startOperation(ConnectionContext context, UUID requestId,
                                             UUID operationId, ConnectionOperationType type);
    ConnectionOperationState getOperation(ConnectionContext context, UUID requestId, UUID operationId);
    ProviderConnectionState disconnect(ConnectionContext context, UUID requestId);
}
