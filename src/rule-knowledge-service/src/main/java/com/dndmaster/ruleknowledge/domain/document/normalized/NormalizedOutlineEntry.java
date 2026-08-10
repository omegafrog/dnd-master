package com.dndmaster.ruleknowledge.domain.document.normalized;

public record NormalizedOutlineEntry(String id, String title, int level, String locator) {
    public NormalizedOutlineEntry {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (title == null) title = "";
        if (level < 1) throw new IllegalArgumentException("level must be positive");
        locator = locator == null ? "" : locator;
    }
}
