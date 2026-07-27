package com.dndmaster.ruleknowledge.application.ocr;

import java.util.Objects;

public record OcrRequest(byte[] content, String sourceLabel, String mimeType) {
    public OcrRequest {
        Objects.requireNonNull(content, "content must not be null");
        content = content.clone();
        if (sourceLabel == null || sourceLabel.isBlank()) {
            sourceLabel = "document";
        } else {
            sourceLabel = sourceLabel.trim();
        }
        if (mimeType != null && mimeType.isBlank()) {
            mimeType = null;
        }
    }
}
