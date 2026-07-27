package com.dndmaster.identityaccess.infrastructure.security;

import com.dndmaster.identityaccess.infrastructure.persistence.IdentityAccessRepository;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class DatabaseAuthenticationProvider implements AuthenticationProvider {
    private final IdentityAccessRepository repository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseAuthenticationProvider(IdentityAccessRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());
        var credential = repository.findActiveCredential(username)
                .filter(stored -> passwordEncoder.matches(password, stored.passwordHash()))
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
        var principal = new PlayerPrincipal(credential.playerId().toString(), credential.username());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
