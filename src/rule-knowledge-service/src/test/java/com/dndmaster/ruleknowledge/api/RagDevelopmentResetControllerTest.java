package com.dndmaster.ruleknowledge.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dndmaster.ruleknowledge.application.reset.DevelopmentRagResetService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RagDevelopmentResetControllerTest {
    @Test
    void reset_requires_the_internal_service_token() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new RagDevelopmentResetController(mock(DevelopmentRagResetService.class), "internal-token"))
                .build();

        mockMvc.perform(post("/internal/v1/rag/reset")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"RESET\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/rag/reset")
                        .header("X-Internal-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"RESET\"}"))
                .andExpect(status().isUnauthorized());
    }
}
