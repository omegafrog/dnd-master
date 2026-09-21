package com.dndmaster.adventure.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record SufficiencyDecision(boolean sufficient, List<UUID> selectedEvidenceIds,
                                  Map<UUID, String> selectionReasons, String missing) {
    public SufficiencyDecision {
        selectedEvidenceIds = List.copyOf(Objects.requireNonNull(selectedEvidenceIds, "selected evidence ids must not be null"));
        selectionReasons = Map.copyOf(Objects.requireNonNull(selectionReasons, "selection reasons must not be null"));
        if (selectedEvidenceIds.stream().anyMatch(Objects::isNull) || selectedEvidenceIds.size() != selectedEvidenceIds.stream().distinct().count())
            throw new IllegalArgumentException("selected evidence ids must be unique");
        if (!selectionReasons.keySet().equals(new java.util.LinkedHashSet<>(selectedEvidenceIds))
                || selectionReasons.values().stream().anyMatch(reason -> reason == null || reason.isBlank()))
            throw new IllegalArgumentException("each selected evidence id requires a selection reason");
        missing = missing == null ? "" : missing.trim();
        if (sufficient && selectedEvidenceIds.isEmpty()) throw new IllegalArgumentException("sufficient decision requires selected evidence");
        if (!sufficient && missing.isBlank()) throw new IllegalArgumentException("insufficient decision requires missing information");
    }
    public static SufficiencyDecision sufficient(List<UUID> ids, Map<UUID, String> reasons) { return new SufficiencyDecision(true, ids, reasons, ""); }
    public static SufficiencyDecision insufficient(List<UUID> ids, Map<UUID, String> reasons, String missing) { return new SufficiencyDecision(false, ids, reasons, missing); }
}
