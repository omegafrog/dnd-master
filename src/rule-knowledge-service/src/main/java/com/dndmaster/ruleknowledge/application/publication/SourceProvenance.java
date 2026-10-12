package com.dndmaster.ruleknowledge.application.publication;

import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public record SourceProvenance(
        int pageNumber,
        List<String> sectionPath,
        List<Double> bbox,
        String tableCell,
        String originalLocator,
        List<SourceSpan> sourceSpans) {
    private static final ObjectMapper JSON = new ObjectMapper();
    public SourceProvenance(int pageNumber, List<String> sectionPath, List<Double> bbox,
            String tableCell, String originalLocator) {
        this(pageNumber, sectionPath, bbox, tableCell, originalLocator, List.of());
    }

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
        sourceSpans = sourceSpans == null ? List.of() : List.copyOf(sourceSpans);
    }

    public record SourceSpan(int pageNumber, Integer blockIndex, Integer charStart, Integer charEnd,
            Integer tokenStart, Integer tokenEnd, List<Double> bbox, String tableId, String tableCell) {
        public SourceSpan {
            if (pageNumber < 1) throw new IllegalArgumentException("source span page number must be positive");
            bbox = bbox == null ? List.of() : List.copyOf(bbox);
            if (!bbox.isEmpty() && bbox.size() != 4) throw new IllegalArgumentException("source span bbox must contain four coordinates");
            if (bbox.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException("source span bbox values must be finite");
            }
            validateRange(blockIndex, "block index");
            validateRange(charStart, "character start");
            validateRange(charEnd, "character end");
            validateRange(tokenStart, "token start");
            validateRange(tokenEnd, "token end");
            if (charStart != null && charEnd != null && charStart > charEnd) throw new IllegalArgumentException("character start must not exceed end");
            if (tokenStart != null && tokenEnd != null && tokenStart > tokenEnd) throw new IllegalArgumentException("token start must not exceed end");
            tableId = tableId == null || tableId.isBlank() ? null : tableId.trim();
            tableCell = tableCell == null || tableCell.isBlank() ? null : tableCell.trim();
        }

        private static void validateRange(Integer value, String name) {
            if (value != null && value < 0) throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    public static List<SourceSpan> sourceSpansFromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return List.copyOf(JSON.readValue(json, new TypeReference<List<SourceSpan>>() {}));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("published source spans are invalid", exception);
        }
    }
}
