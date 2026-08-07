package com.dndmaster.appall.configuration;

import com.dndmaster.adventure.application.runtime.RuntimeCapabilityPreflightPort;
import com.dndmaster.adventure.infrastructure.integration.HttpRuntimeCapabilityPreflight;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("app-all")
public class AllInOneRuntimeCapabilityConfiguration {
    @Bean
    RuntimeCapabilityPreflightPort allInOneRuntimeCapabilityPreflight(
            @Value("${adventure.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String gmBaseUrl,
            @Value("${adventure.integration.dice-roll.base-url:http://127.0.0.1:8080/}") String diceBaseUrl,
            @Value("${adventure.integration.character-management.base-url:http://127.0.0.1:8080/}") String characterBaseUrl,
            @Value("${adventure.integration.combat-map.base-url:http://127.0.0.1:8080/}") String mapBaseUrl) {
        return new HttpRuntimeCapabilityPreflight(HttpClient.newHttpClient(), URI.create(gmBaseUrl), URI.create(diceBaseUrl),
                URI.create(characterBaseUrl), URI.create(mapBaseUrl), Duration.ofSeconds(2));
    }
}
