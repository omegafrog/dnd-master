package com.dndmaster.adventure.api;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.adventure.application.auth.PlayerSessionLookupPort;
import com.dndmaster.adventure.application.runtime.TacticalScenePreparationApplicationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "INTERNAL_SERVICE_TOKEN=test-internal-token")
@AutoConfigureMockMvc
@Import({AdventureSecurityConfiguration.class, AdventureSecurityConfigurationTest.PingController.class})
class AdventureSecurityConfigurationTest {
    @Autowired MockMvc mockMvc;
    @MockBean PlayerSessionLookupPort playerSessionLookupPort;
    @MockBean TacticalScenePreparationApplicationService tacticalScenePreparationApplicationService;

    @Test
    void bearerTokenFilterStaysOnAdventurePathsOnly() throws Exception {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        org.mockito.Mockito.when(playerSessionLookupPort.resolvePlayerId("token")).thenReturn(Optional.of(playerId));

        mockMvc.perform(get("/api/v1/rulebooks").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(playerSessionLookupPort, never()).resolvePlayerId("token");
    }

    @RestController
    static class PingController {
        @GetMapping("/api/v1/rulebooks")
        ResponseEntity<String> ping() {
            return ResponseEntity.ok("ok");
        }
    }
}
