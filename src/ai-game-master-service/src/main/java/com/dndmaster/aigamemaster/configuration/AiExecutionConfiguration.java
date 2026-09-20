package com.dndmaster.aigamemaster.configuration;

import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.infrastructure.ai.RemoteAiExecutionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
public class AiExecutionConfiguration {
    @Bean
    @ConditionalOnMissingBean(AiExecutionPort.class)
    AiExecutionPort aiExecutionPort(ObjectMapper mapper, MeterRegistry meterRegistry,
            @Value("${ai-game-master.relay.base-url:http://agent-connection-relay-service:8080}") URI baseUri,
            @Value("${ai-game-master.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token,
            @Value("${ai-game-master.relay.timeout:PT3M}") Duration timeout) {
        return new RemoteAiExecutionPort(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), mapper, baseUri, token, timeout, meterRegistry);
    }
}
