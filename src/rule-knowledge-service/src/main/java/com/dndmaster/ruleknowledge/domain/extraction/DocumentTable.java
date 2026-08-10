package com.dndmaster.ruleknowledge.domain.extraction;

import java.util.List;

public record DocumentTable(String id, int page, List<List<String>> rows) {
    public DocumentTable {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
    }
}
