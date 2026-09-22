package com.dndmaster.aigamemaster.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects every AI model boundary below /internal before a request body is bound. */
public final class InternalServiceTokenFilter extends OncePerRequestFilter {
    private final byte[] expectedToken;

    public InternalServiceTokenFilter(String token) {
        expectedToken = ApiRequestGuard.required(token, "internal token").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Internal-Token");
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, actual)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SERVICE_TOKEN");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        return !(path.startsWith(context + "/internal/v1/gm/")
                || path.startsWith(context + "/internal/gm/"));
    }
}
