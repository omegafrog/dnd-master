package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.List;
import java.util.Objects;

public record SourcePreviewResult(String content, List<String> warnings, List<PreviewSpan> spans) {
    public SourcePreviewResult {
        content = Objects.requireNonNull(content, "content must not be null");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        spans = spans == null ? List.of() : List.copyOf(spans);
    }
}
