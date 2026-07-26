package com.dndmaster.character.api;

import com.dndmaster.character.application.AdventureEditionHttpPort;
import com.dndmaster.character.application.CharacterSheetApplicationService;
import com.dndmaster.character.application.CharacterSheetRepository;
import com.dndmaster.character.application.SessionCharacterPolicy;
import com.dndmaster.character.application.SessionCharacterPolicyPort;
import com.dndmaster.character.domain.AdventureId;
import com.dndmaster.character.domain.SheetEdition;
import com.dndmaster.character.infrastructure.persistence.PostgresCharacterSheetRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class CharacterManagementApiConfiguration {

    @Bean
    CharacterSheetRepository characterSheetRepository(DataSource dataSource) {
        return new PostgresCharacterSheetRepository(dataSource);
    }

    @Bean
    AdventureEditionHttpPort adventureEditionHttpPort() {
        return adventureId -> SheetEdition.DND_5E_2024;
    }

    @Bean
    SessionCharacterPolicyPort sessionCharacterPolicyPort() {
        return ignored -> SessionCharacterPolicy.draft();
    }

    @Bean
    CharacterSheetApplicationService characterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort,
            SessionCharacterPolicyPort sessionCharacterPolicyPort) {
        return new CharacterSheetApplicationService(repository, adventureEditionHttpPort, sessionCharacterPolicyPort);
    }

    @Bean
    CharacterSheetController characterSheetController(
            CharacterSheetApplicationService characterSheetService) {
        return new CharacterSheetController(characterSheetService);
    }
}
