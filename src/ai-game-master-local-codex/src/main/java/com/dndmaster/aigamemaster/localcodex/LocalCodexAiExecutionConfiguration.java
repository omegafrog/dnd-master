package com.dndmaster.aigamemaster.localcodex;

import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;

@AutoConfiguration
public class LocalCodexAiExecutionConfiguration {
    @Bean(destroyMethod = "close")
    @Primary
    AiExecutionPort localCodexAiExecutionPort(
            @Value("${ai.codex.executable:codex}") String executable,
            @Value("${ai.codex.work-directory:/tmp}") String workDirectory,
            @Value("${ai.codex.timeout:PT5M}") Duration timeout,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new LocalCodexAiExecutionPort(executable, Path.of(workDirectory), timeout, objectMapper);
    }
}
