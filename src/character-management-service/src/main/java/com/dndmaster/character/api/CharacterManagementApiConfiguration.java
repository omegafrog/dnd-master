package com.dndmaster.character.api;

import com.dndmaster.character.application.AdventureEditionHttpPort;
import com.dndmaster.character.application.CharacterSheetApplicationService;
import com.dndmaster.character.application.CharacterSheetRepository;
import com.dndmaster.character.application.SessionCharacterPolicyPort;
import com.dndmaster.character.application.CharacterSheetsDeletionConsumer;
import com.dndmaster.character.domain.SheetEdition;
import com.dndmaster.character.infrastructure.persistence.PostgresCharacterSheetRepository;
import com.dndmaster.character.infrastructure.CrossContextHttpSessionCharacterPolicyAdapter;
import com.dndmaster.character.application.auth.PlayerSessionLookupPort;
import com.dndmaster.character.infrastructure.CrossContextHttpPlayerSessionLookupGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class CharacterManagementApiConfiguration {

    @Bean
    ApiRequestGuard characterApiRequestGuard(@Value("${character.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token) { return new ApiRequestGuard(token); }

    @Bean
    CharacterSheetRepository characterSheetRepository(DataSource dataSource) {
        return new PostgresCharacterSheetRepository(dataSource);
    }

    @Bean
    PlayerSessionLookupPort characterPlayerSessionLookupPort(
            ObjectMapper objectMapper,
            @Value("${character.integration.identity.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${character.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        return new CrossContextHttpPlayerSessionLookupGateway(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(5), objectMapper, internalToken);
    }

    @Bean
    AdventureEditionHttpPort adventureEditionHttpPort(
            @Value("${character.default-edition:DND_5E_2014}") String edition) {
        SheetEdition appliedEdition = SheetEdition.valueOf(edition);
        return adventureId -> appliedEdition;
    }

    @Bean
    SessionCharacterPolicyPort sessionCharacterPolicyPort(
            ObjectMapper objectMapper,
            @Value("${character.integration.adventure.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${character.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        return new CrossContextHttpSessionCharacterPolicyAdapter(HttpClient.newHttpClient(), URI.create(baseUrl), Duration.ofSeconds(5), objectMapper, internalToken);
    }

    @Bean
    CharacterSheetApplicationService characterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort,
            SessionCharacterPolicyPort sessionCharacterPolicyPort) {
        return new CharacterSheetApplicationService(repository, adventureEditionHttpPort, sessionCharacterPolicyPort);
    }

    @Bean
    CharacterSheetsDeletionConsumer characterSheetsDeletionConsumer(CharacterSheetRepository repository) {
        return new CharacterSheetsDeletionConsumer(repository);
    }

    @Bean
    CharacterSheetController characterSheetController(
            CharacterSheetApplicationService characterSheetService,
            @Qualifier("characterPlayerSessionLookupPort") PlayerSessionLookupPort playerSessionLookupPort) {
        var controller = new CharacterSheetController(characterSheetService);
        controller.setPlayerSessionLookupPort(playerSessionLookupPort);
        return controller;
    }
}
