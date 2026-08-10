package com.dndmaster.ruleknowledge.domain.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;

public record DocumentImage(String id, int page, BoundingBox boundingBox, String mimeType, String caption) {
    public DocumentImage {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        mimeType = mimeType == null ? "application/octet-stream" : mimeType;
        caption = caption == null ? "" : caption;
    }
}
