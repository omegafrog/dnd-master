package com.dndmaster.ruleknowledge.domain.extraction;

import java.util.List;

public record DocumentExtractionResult(
        List<DocumentNode> nodes,
        List<DocumentTable> tables,
        List<DocumentImage> images,
        List<ExtractionWarning> warnings,
        String rawText) {
    public DocumentExtractionResult {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        tables = tables == null ? List.of() : List.copyOf(tables);
        images = images == null ? List.of() : List.copyOf(images);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        rawText = rawText == null ? "" : rawText;
    }
}
