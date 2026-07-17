package com.dndmaster.identityaccess.api;

import com.dndmaster.identityaccess.infrastructure.security.CredentialAuthenticationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AuthenticationController {
    private final CredentialAuthenticationService authenticationService;

    public AuthenticationController(CredentialAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/api/v1/auth/registrations")
    RegistrationResponse register(@RequestBody RegistrationRequest request) {
        return new RegistrationResponse(authenticationService.register(request.username(), request.password()));
    }

    @PostMapping("/api/v1/auth/login")
    LoginResponse login(@RequestBody LoginRequest request) {
        var session = authenticationService.login(request.username(), request.password());
        return new LoginResponse(session.token(), session.playerId());
    }

    @PostMapping("/api/v1/auth/logout")
    ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        authenticationService.logout(bearerToken(authorization));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/v1/auth/introspections")
    IntrospectionResponse introspect(@RequestBody IntrospectionRequest request) {
        return authenticationService.introspect(request.token())
                .map(player -> new IntrospectionResponse(true, player.identify().value()))
                .orElseGet(() -> new IntrospectionResponse(false, null));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer authorization is required");
        }
        return authorization.substring("Bearer ".length());
    }

    public record RegistrationRequest(String username, String password, String ownerPlayerId) {}

    public record RegistrationResponse(String playerId) {}

    public record LoginRequest(String username, String password, String ownerPlayerId) {}

    public record LoginResponse(String token, String playerId) {}

    public record IntrospectionRequest(String token) {}

    public record IntrospectionResponse(boolean authenticated, String playerId) {}
}
