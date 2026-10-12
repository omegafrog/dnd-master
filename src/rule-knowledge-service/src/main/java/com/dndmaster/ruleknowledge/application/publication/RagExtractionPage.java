package com.dndmaster.ruleknowledge.application.publication;

import java.util.List;

public record RagExtractionPage(int pageNumber, String status, int attempts, List<String> findings,
        String pageClassification, java.util.Map<String, Integer> classificationEvidence) {
    public RagExtractionPage(int pageNumber, String status, int attempts, List<String> findings) {
        this(pageNumber, status, attempts, findings, null, java.util.Map.of());
    }

    public RagExtractionPage {
        if (pageNumber < 1) throw new IllegalArgumentException("page number must be positive");
        if (!"VALIDATED".equals(status) && !"EXCLUDED_FORM".equals(status) && !"NEEDS_REVIEW".equals(status)) {
            throw new IllegalArgumentException("unsupported extraction page status");
        }
        if (attempts < 1) throw new IllegalArgumentException("page attempts must be positive");
        findings = findings == null ? List.of() : findings.stream().filter(item -> item != null && !item.isBlank()).toList();
        pageClassification = pageClassification == null || pageClassification.isBlank() ? null : pageClassification;
        classificationEvidence = classificationEvidence == null ? java.util.Map.of() : java.util.Map.copyOf(classificationEvidence);
        if ("EXCLUDED_FORM".equals(status)
                && (!"form".equals(pageClassification)
                    || !classificationEvidence.keySet().containsAll(List.of("drawing_count", "aligned_line_count",
                            "long_horizontal_line_count", "text_line_count")))) {
            throw new IllegalArgumentException("excluded form requires classification evidence");
        }
    }
}
