package com.dndmaster.ruleknowledge.application.preprocessing;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Safe page-level read model returned by the preprocessing process boundary. */
public record PreprocessingPageState(
        int pageNumber,
        String status,
        int attempts,
        List<String> findings) {
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
            "(?i)(?:^|[\\s(\\[=:])(?:/|\\\\\\\\|[A-Za-z]:[\\\\/])");

    public PreprocessingPageState {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("page number must be positive");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("page status must not be blank");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("page attempts must be positive");
        }
        findings = findings == null ? List.of() : findings.stream()
                .filter(Objects::nonNull)
                .map(PreprocessingPageState::sanitize)
                .toList();
    }

    private static String sanitize(String value) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return "UNSPECIFIED_DIAGNOSTIC";
        }
        if (normalized.matches("(?i).*\\b(?:authorization|bearer|token|internal[_-]?service[_-]?token)\\b.*")
                || ABSOLUTE_PATH.matcher(normalized).find()) {
            return "DIAGNOSTIC_REDACTED";
        }
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }
}
