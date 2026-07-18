package com.dndmaster.aigamemaster.configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OllamaConfigurationResourceTest {
    @Test
    void pinsNeverPullAndQualityProfileToAllowlistedQ4Model() throws IOException {
        String yaml;
        try (var input = getClass().getResourceAsStream("/application.yml")) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(yaml.contains("pull-model-strategy: never"));
        assertTrue(yaml.contains("on-profile: quality"));
        assertTrue(yaml.contains("qwen3:8b-q4_K_M"));
    }
}
