package com.dndmaster.aigamemaster.localcodex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentConnectionControlCommand(String messageType, String requestId, String action,
                                             String operationId, String operationType) { }
