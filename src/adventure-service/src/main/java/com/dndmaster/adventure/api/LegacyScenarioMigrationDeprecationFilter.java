package com.dndmaster.adventure.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class LegacyScenarioMigrationDeprecationFilter extends OncePerRequestFilter {
    private static final String PATH_PREFIX = "/api/v1/adventures/legacy-scenarios/";
    private static final String SUNSET = "Fri, 31 Dec 2027 00:00:00 GMT";
    private static final String WARNING =
            "299 dnd-master \"Legacy one-file scenario migration is deprecated; migrate to bundle/package flows\"";
    private static final String ALTERNATE_LINK = "</api/v1/adventures/scenario-bundles>; rel=\"alternate\"";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(PATH_PREFIX)) {
            response.setHeader("Deprecation", "true");
            response.setHeader("Warning", WARNING);
            response.setHeader("Sunset", SUNSET);
            response.setHeader("Link", ALTERNATE_LINK);
        }
        filterChain.doFilter(request, response);
    }
}
