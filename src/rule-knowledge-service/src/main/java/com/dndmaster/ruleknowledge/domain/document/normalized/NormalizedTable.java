package com.dndmaster.ruleknowledge.domain.document.normalized;

import java.util.List;

public record NormalizedTable(String id, int page, List<List<String>> rows) {
    public NormalizedTable {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        rows = rows == null ? List.of() : rows.stream().map(row -> row == null ? List.<String>of() : List.copyOf(row)).toList();
    }
}
