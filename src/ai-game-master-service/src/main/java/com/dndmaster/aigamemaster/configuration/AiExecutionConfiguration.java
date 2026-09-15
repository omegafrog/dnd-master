package com.dndmaster.aigamemaster.configuration;

import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.infrastructure.ai.RemoteAiExecutionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiExecutionConfiguration {
    @Bean
    @ConditionalOnMissingBean(AiExecutionPort.class)
    AiExecutionPort aiExecutionPort() {
        return new RemoteAiExecutionPort();
    }
}
