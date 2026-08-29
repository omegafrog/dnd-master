package com.dndmaster.ruleknowledge.application.publication;

import java.util.List;

public record RagExtractionPage(int pageNumber, String status, int attempts, List<String> findings) {
    public RagExtractionPage {
        if (pageNumber < 1) throw new IllegalArgumentException("page number must be positive");
        if (!"VALIDATED".equals(status) && !"NEEDS_REVIEW".equals(status)) {
            throw new IllegalArgumentException("unsupported extraction page status");
        }
        if (attempts < 1) throw new IllegalArgumentException("page attempts must be positive");
        findings = findings == null ? List.of() : findings.stream().filter(item -> item != null && !item.isBlank()).toList();
    }
}
