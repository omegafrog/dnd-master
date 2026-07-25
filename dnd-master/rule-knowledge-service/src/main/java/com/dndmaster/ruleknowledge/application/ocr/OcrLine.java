package com.dndmaster.ruleknowledge.application.ocr;

import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import java.util.Objects;

public record OcrLine(int lineNumber, String text, BoundingBox bounds, double confidence) {
    public OcrLine {
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(bounds, "bounds must not be null");
        if (confidence < 0d || confidence > 100d) {
            throw new IllegalArgumentException("confidence must be between 0 and 100");
        }
    }
}
