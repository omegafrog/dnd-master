package com.dndmaster.aigamemaster.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Temporary diagnostics for tracing character tag candidates after grounding.
 *
 * <p>The controller already logs the number of candidates accepted by the model parser. This
 * filter records the shape of the HTTP response after grounding so the two stages can be compared
 * without logging source excerpts or full model output.</p>
 */
@Component
public final class CharacterInputTagDiagnosticsFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CharacterInputTagDiagnosticsFilter.class);
    private static final String ENDPOINT = "/internal/v1/gm/character-input-tags";

    private final ObjectMapper objectMapper;

    public CharacterInputTagDiagnosticsFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ENDPOINT.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, wrapped);
            logResponse(wrapped, startedAt);
        } finally {
            wrapped.copyBodyToResponse();
        }
    }

    private void logResponse(ContentCachingResponseWrapper response, long startedAt) {
        byte[] body = response.getContentAsByteArray();
        try {
            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode candidates = root.path("candidates");
            List<String> summaries = new ArrayList<>();
            if (candidates.isArray()) {
                for (JsonNode candidate : candidates) {
                    summaries.add("key=" + candidate.path("key").asText("<missing>")
                            + ",mode=" + candidate.path("inputMode").asText("<missing>")
                            + ",options=" + candidate.path("options").size()
                            + ",optionDetails=" + candidate.path("optionDetails").size()
                            + ",evidence=" + candidate.path("evidence").size()
                            + ",quoteBlank=" + candidate.path("sourceQuote").asText("").isBlank());
                }
            }
            LOGGER.info("character_tag_http_response status={} responseChars={} candidates={} summaries={} elapsedMs={}",
                    response.getStatus(), body.length, candidates.isArray() ? candidates.size() : -1,
                    summaries, (System.nanoTime() - startedAt) / 1_000_000);
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("character_tag_http_response_unreadable status={} responseChars={} elapsedMs={} reason={}",
                    response.getStatus(), body.length, (System.nanoTime() - startedAt) / 1_000_000,
                    reason(exception));
        }
    }

    private static String reason(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        String normalized = message.replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 120));
    }
}
