package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.Objects;

public record PreviewAsset(String kind, String locator, String contentType, Integer pageNumber) {
    public PreviewAsset {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind must not be blank");
        }
        kind = kind.trim();
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
        locator = locator.trim();
        if (contentType != null && contentType.isBlank()) {
            contentType = null;
        }
        if (pageNumber != null && pageNumber <= 0) {
            throw new IllegalArgumentException("pageNumber must be positive when present");
        }
    }
}
