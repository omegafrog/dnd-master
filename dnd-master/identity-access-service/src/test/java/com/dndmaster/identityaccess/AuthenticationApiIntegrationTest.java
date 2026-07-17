package com.dndmaster.identityaccess;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthenticationApiIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String CLIENT_OWNER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE identity_access.login_sessions, identity_access.players CASCADE");
    }

    @Test
    void serverDeterminesPlayerIdAndSupportsLoginIntrospectionAndLogout() throws Exception {
        String registration = mockMvc.perform(post("/api/v1/auth/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"solo-player","password":"a-secure-password","ownerPlayerId":"%s"}
                                """.formatted(CLIENT_OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId", not(CLIENT_OWNER_ID)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String serverPlayerId = objectMapper.readTree(registration).get("playerId").asText();

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"solo-player","password":"a-secure-password","ownerPlayerId":"%s"}
                                """.formatted(CLIENT_OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId", is(serverPlayerId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginJson = objectMapper.readTree(login);
        String token = loginJson.get("token").asText();

        mockMvc.perform(post("/internal/v1/auth/introspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.playerId", is(serverPlayerId)));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/internal/v1/auth/introspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(false)));
    }

    @Test
    void invalidPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"player\",\"password\":\"a-secure-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"player\",\"password\":\"wrong-password-value\"}"))
                .andExpect(status().isUnauthorized());
    }
}
