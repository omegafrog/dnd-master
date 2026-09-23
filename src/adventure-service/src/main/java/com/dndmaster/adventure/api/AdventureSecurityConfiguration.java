package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.auth.PlayerSessionLookupPort;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
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
    @Order(1)
    SecurityFilterChain adventureSecurityFilterChain(HttpSecurity http,
            @Qualifier("adventurePlayerSessionLookupPort") PlayerSessionLookupPort sessionLookupPort,
            @Value("${adventure.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken)
            throws Exception {
        BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter = new BearerTokenAuthenticationFilter(sessionLookupPort, internalToken);
        return http.securityMatcher(
                        "/api/v1/adventures/**",
                        "/api/v1/adventure-sessions/**",
                        "/api/v1/scenario-packages/**",
                        "/api/v1/runtime-options",
                        "/internal/v1/adventures/**",
                        "/api/v1/internal/adventures/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                .addFilterBefore(bearerTokenAuthenticationFilter,
                        org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
                .build();
    }

    static final class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
        private final PlayerSessionLookupPort sessionLookupPort;
        private final byte[] internalToken;

        BearerTokenAuthenticationFilter(PlayerSessionLookupPort sessionLookupPort, String internalToken) {
            this.sessionLookupPort = Objects.requireNonNull(sessionLookupPort, "sessionLookupPort must not be null");
            if (internalToken == null || internalToken.isBlank()) throw new IllegalArgumentException("internal token must not be blank");
            this.internalToken = internalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
            if (isInternalAdventurePath(request)) {
                byte[] supplied = java.util.Optional.ofNullable(request.getHeader("X-Internal-Token"))
                        .orElse("").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (java.security.MessageDigest.isEqual(internalToken, supplied)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (isOwnedAdventureList(request)) {
                    String authorization = request.getHeader("Authorization");
                    UUID principal = bearerPlayerId(authorization);
                    UUID requestedOwner = parseOwner(request.getParameter("ownerId"));
                    if (principal != null && requestedOwner != null && principal.equals(requestedOwner)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    response.sendError(principal == null ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN,
                            principal == null ? "UNAUTHENTICATED" : "OWNERSHIP_DENIED");
                    return;
                }
                if (!java.security.MessageDigest.isEqual(internalToken, supplied)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_TOKEN");
                    return;
                }
            }
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

        private static boolean isInternalAdventurePath(HttpServletRequest request) {
            String path = request.getRequestURI();
            String context = request.getContextPath();
            return path.startsWith(context + "/internal/v1/adventures/")
                    || path.equals(context + "/internal/v1/adventures")
                    || path.startsWith(context + "/api/v1/internal/adventures/")
                    || path.equals(context + "/api/v1/internal/adventures")
                    || path.startsWith(context + "/api/v1/adventure-sessions/internal/");
        }

        private boolean isOwnedAdventureList(HttpServletRequest request) {
            String path = request.getRequestURI();
            String context = request.getContextPath();
            return path.equals(context + "/internal/v1/adventures");
        }

        private UUID bearerPlayerId(String authorization) {
            if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank()) return null;
            return sessionLookupPort.resolvePlayerId(authorization.substring(7)).orElse(null);
        }

        private static UUID parseOwner(String value) {
            try { return value == null ? null : UUID.fromString(value); }
            catch (IllegalArgumentException ignored) { return null; }
        }
    }
}
