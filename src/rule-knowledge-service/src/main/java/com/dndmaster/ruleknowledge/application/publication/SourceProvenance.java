package com.dndmaster.ruleknowledge.application.publication;

import java.util.List;

public record SourceProvenance(
        int pageNumber,
        List<String> sectionPath,
        List<Double> bbox,
        String tableCell,
        String originalLocator) {
    public SourceProvenance {
        if (pageNumber < 1) throw new IllegalArgumentException("page number must be positive");
        sectionPath = sectionPath == null ? List.of() : sectionPath.stream().filter(item -> item != null && !item.isBlank()).toList();
        bbox = bbox == null ? List.of() : List.copyOf(bbox);
        if (!bbox.isEmpty() && bbox.size() != 4) throw new IllegalArgumentException("bbox must contain four coordinates");
        if (bbox.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("bbox values must be finite");
        }
        if (originalLocator == null || originalLocator.isBlank()) {
            throw new IllegalArgumentException("original locator must not be blank");
        }
        originalLocator = originalLocator.trim();
        tableCell = tableCell == null || tableCell.isBlank() ? null : tableCell.trim();
    }
}
