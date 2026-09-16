package com.dndmaster.relay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "relay")
public record RelayInstanceProperties(
    String instanceId,
    String internalAddress,
    String expiredAt) {
}
