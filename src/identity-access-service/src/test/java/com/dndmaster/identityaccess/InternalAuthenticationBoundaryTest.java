package com.dndmaster.identityaccess;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class InternalAuthenticationBoundaryTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void rejectsMissingOrWrongInternalTokenBeforeIntrospection() throws Exception {
        for (String token : new String[] {null, "wrong-token"}) {
            var request = post("/internal/v1/auth/introspections")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"anything\"}");
            if (token != null) request.header("X-Internal-Token", token);
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }
}
