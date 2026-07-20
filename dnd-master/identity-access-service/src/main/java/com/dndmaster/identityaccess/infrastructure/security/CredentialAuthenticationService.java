package com.dndmaster.identityaccess.infrastructure.security;

import com.dndmaster.identityaccess.application.PlayerAccessApplicationService;
import com.dndmaster.identityaccess.domain.player.AuthenticatedPlayer;
import com.dndmaster.identityaccess.infrastructure.persistence.IdentityAccessRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialAuthenticationService {
    private final IdentityAccessRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final PlayerAccessApplicationService playerAccessApplicationService;

    public CredentialAuthenticationService(
            IdentityAccessRepository repository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            PlayerAccessApplicationService playerAccessApplicationService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.playerAccessApplicationService = playerAccessApplicationService;
    }

    @Transactional
    public String register(String username, String password) {
        String normalizedUsername = validUsername(username);
        String validPassword = validPassword(password);
        UUID serverDeterminedPlayerId = UUID.randomUUID();
        try {
            repository.insertPlayer(
                    serverDeterminedPlayerId,
                    normalizedUsername,
                    passwordEncoder.encode(validPassword),
                    Instant.now());
        } catch (DuplicateKeyException exception) {
            throw new UsernameAlreadyExistsException(exception);
        }
        return serverDeterminedPlayerId.toString();
    }

    @Transactional
    public AuthenticatedSession login(String username, String password) {
        var request = UsernamePasswordAuthenticationToken.unauthenticated(validUsername(username), validPassword(password));
        var authentication = authenticationManager.authenticate(request);
        var principal = (PlayerPrincipal) authentication.getPrincipal();
        AuthenticatedPlayer authenticatedPlayer =
                playerAccessApplicationService.login(Optional.of(principal.playerId()));
        UUID token = UUID.randomUUID();
        repository.insertSession(token, UUID.fromString(authenticatedPlayer.identify().value()), Instant.now());
        return new AuthenticatedSession(token.toString(), authenticatedPlayer.identify().value());
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedPlayer> introspect(String token) {
        UUID sessionToken = validUuid(token, "session token");
        return repository.findActiveSessionPlayer(sessionToken)
                .map(UUID::toString)
                .map(subject -> playerAccessApplicationService.login(Optional.of(subject)));
    }

    @Transactional
    public void logout(String token) {
        repository.revokeSession(validUuid(token, "session token"), Instant.now());
    }

    private static String validUsername(String username) {
        if (username == null || username.isBlank() || username.length() > 100 || username.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("username must contain 1 to 100 non-control characters");
        }
        return username.trim();
    }

    private static String validPassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 200) {
            throw new IllegalArgumentException("password must contain 12 to 200 characters");
        }
        return password;
    }

    private static UUID validUuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(name + " must be a UUID", exception);
        }
    }
}
