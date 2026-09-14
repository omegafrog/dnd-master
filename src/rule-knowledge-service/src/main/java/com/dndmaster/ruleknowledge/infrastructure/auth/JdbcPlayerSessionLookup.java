package com.dndmaster.ruleknowledge.infrastructure.auth;

import com.dndmaster.ruleknowledge.application.auth.PlayerSessionLookupPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public final class JdbcPlayerSessionLookup implements PlayerSessionLookupPort {
    private final JdbcClient jdbcClient;

    public JdbcPlayerSessionLookup(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<UUID> resolvePlayerId(String accessToken) {
        try {
            return jdbcClient.sql("""
                            SELECT player_id FROM identity_access.login_sessions
                            WHERE session_token = :token AND active = TRUE
                            """)
                    .param("token", UUID.fromString(accessToken))
                    .query(UUID.class)
                    .optional();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
