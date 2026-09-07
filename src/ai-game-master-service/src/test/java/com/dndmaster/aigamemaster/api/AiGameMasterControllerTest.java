package com.dndmaster.aigamemaster.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiGameMasterControllerTest {

    @Test
    void proposesCompanionWithoutSeparateModelEndpoint() {
        AiGameMasterController controller = new AiGameMasterController(null, null, null, null, null);

        AiGameMasterController.CompanionCandidateResponse response = controller.proposeCompanion(
                new AiGameMasterController.CompanionCandidateRequest(UUID.randomUUID()));

        assertEquals("린", response.name());
        assertEquals("엘프", response.race());
        assertEquals("클레릭", response.characterClass());
    }
}
