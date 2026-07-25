package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.auth.PlayerSessionLookupPort;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration(proxyBeanMethods = false)
public class AdventureSecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                .addFilterBefore(bearerTokenAuthenticationFilter,
                        org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter(PlayerSessionLookupPort sessionLookupPort) {
        return new BearerTokenAuthenticationFilter(sessionLookupPort);
    }

    static final class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
        private final PlayerSessionLookupPort sessionLookupPort;

        BearerTokenAuthenticationFilter(PlayerSessionLookupPort sessionLookupPort) {
            this.sessionLookupPort = Objects.requireNonNull(sessionLookupPort, "sessionLookupPort must not be null");
        }

        @Override
        protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || authorization.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }
            if (authorization.startsWith("Bearer ")) {
                UUID playerId = sessionLookupPort.resolvePlayerId(authorization.substring("Bearer ".length()))
                        .orElse(null);
                if (playerId != null) {
                    var principal = new AdventurePrincipal(playerId);
                    var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
