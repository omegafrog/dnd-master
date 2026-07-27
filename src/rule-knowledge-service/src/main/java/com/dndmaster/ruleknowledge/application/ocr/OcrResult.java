package com.dndmaster.ruleknowledge.application.ocr;

import java.util.List;
import java.util.Objects;

public record OcrResult(List<OcrLine> lines, List<String> warnings, OcrFailure failure) {
    public OcrResult {
        lines = lines == null ? List.of() : List.copyOf(lines);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        failure = failure == null ? OcrFailure.NONE : failure;
        for (OcrLine line : lines) {
            Objects.requireNonNull(line, "line must not be null");
        }
    }

    public boolean hasText() {
        return !lines.isEmpty();
    }
}
