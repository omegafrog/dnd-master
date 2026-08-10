package com.dndmaster.ruleknowledge.domain.document.evidence;

/** Printed navigation entry, independent of table/text parser representation. */
public record NavigationEntry(String id, String title, String locator, Integer levelSuggestion,
                              String sourceId, String rawText, double confidence) {
    public NavigationEntry {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        title = title == null ? "" : title.trim();
        locator = locator == null ? "" : locator.trim();
        sourceId = sourceId == null ? "" : sourceId;
        rawText = rawText == null ? "" : rawText;
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
    }
}
