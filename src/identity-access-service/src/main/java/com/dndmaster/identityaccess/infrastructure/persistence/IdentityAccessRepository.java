package com.dndmaster.identityaccess.infrastructure.persistence;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityAccessRepository {
    private final JdbcClient jdbcClient;

    public IdentityAccessRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertPlayer(UUID playerId, String username, String passwordHash, Instant createdAt) {
        jdbcClient.sql("""
                        INSERT INTO identity_access.players(player_id, username, password_hash, active, created_at)
                        VALUES (:playerId, :username, :passwordHash, TRUE, :createdAt)
                        """)
                .param("playerId", playerId)
                .param("username", username)
                .param("passwordHash", passwordHash)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
    }

    public Optional<StoredCredential> findActiveCredential(String username) {
        return jdbcClient.sql("""
                        SELECT player_id, username, password_hash, active
                        FROM identity_access.players
                        WHERE username = :username AND active = TRUE
                        """)
                .param("username", username)
                .query((resultSet, rowNumber) -> new StoredCredential(
                        resultSet.getObject("player_id", UUID.class),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getBoolean("active")))
                .optional();
    }

    public void insertSession(UUID token, UUID playerId, Instant createdAt) {
        jdbcClient.sql("""
                        INSERT INTO identity_access.login_sessions(session_token, player_id, active, created_at)
                        VALUES (:token, :playerId, TRUE, :createdAt)
                        """)
                .param("token", token)
                .param("playerId", playerId)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
    }

    public Optional<UUID> findActiveSessionPlayer(UUID token) {
        return jdbcClient.sql("""
                        SELECT player_id
                        FROM identity_access.login_sessions
                        WHERE session_token = :token AND active = TRUE
                        """)
                .param("token", token)
                .query(UUID.class)
                .optional();
    }

    public void revokeSession(UUID token, Instant loggedOutAt) {
        jdbcClient.sql("""
                        UPDATE identity_access.login_sessions
                        SET active = FALSE, logged_out_at = :loggedOutAt
                        WHERE session_token = :token AND active = TRUE
                        """)
                .param("token", token)
                .param("loggedOutAt", Timestamp.from(loggedOutAt))
                .update();
    }
}
