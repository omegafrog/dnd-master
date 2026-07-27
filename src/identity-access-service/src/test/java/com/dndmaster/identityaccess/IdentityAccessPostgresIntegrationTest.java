package com.dndmaster.identityaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.identityaccess.infrastructure.security.CredentialAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentityAccessPostgresIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private CredentialAuthenticationService authenticationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE identity_access.login_sessions, identity_access.players CASCADE");
    }

    @Test
    void persistsHashedCredentialAndSessionInDedicatedSchema() {
        String password = "correct-horse-battery-staple";
        String playerId = authenticationService.register("dungeon-master", password);

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM identity_access.players WHERE player_id = ?::uuid",
                String.class,
                playerId);
        assertNotEquals(password, storedHash);
        assertTrue(passwordEncoder.matches(password, storedHash));

        var session = authenticationService.login("dungeon-master", password);
        assertEquals(playerId, session.playerId());
        assertEquals(playerId, authenticationService.introspect(session.token()).orElseThrow().identify().value());

        authenticationService.logout(session.token());
        assertFalse(authenticationService.introspect(session.token()).isPresent());
    }
}
