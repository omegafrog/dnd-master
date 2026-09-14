package com.dndmaster.ruleknowledge.application.preprocessing;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Safe page-level read model returned by the preprocessing process boundary. */
public record PreprocessingPageState(
        int pageNumber,
        String status,
        int attempts,
        List<String> findings,
        LayoutReview layoutReview) {
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

    public PreprocessingPageState(int pageNumber, String status, int attempts, List<String> findings) {
        this(pageNumber, status, attempts, findings, null);
    }

    public record LayoutReview(List<LayoutRegionReview> regions, List<LayoutBlockReview> blocks) {
        public LayoutReview {
            regions = regions == null ? List.of() : List.copyOf(regions);
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    public record LayoutRegionReview(String regionId, List<LayoutCandidateReview> candidates) {
        public LayoutRegionReview {
            regionId = regionId == null ? "" : regionId;
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record LayoutCandidateReview(int candidateIndex, int columnCount, double score, List<List<Double>> columns) {
        public LayoutCandidateReview {
            if (candidateIndex < 0 || columnCount < 1 || score < 0 || score > 1) throw new IllegalArgumentException("invalid layout candidate");
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    public record LayoutBlockReview(String blockId, String text, List<Double> bbox) {
        public LayoutBlockReview {
            blockId = blockId == null ? "" : blockId;
            text = text == null ? "" : text;
            bbox = bbox == null ? List.of() : List.copyOf(bbox);
        }
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
