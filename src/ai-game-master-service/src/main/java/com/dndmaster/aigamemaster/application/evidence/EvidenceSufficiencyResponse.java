package com.dndmaster.aigamemaster.application.evidence;

import java.util.List;
import java.util.Map;

public record EvidenceSufficiencyResponse(boolean sufficient, List<String> selectedEvidenceIds,
                                          Map<String, String> selectionReasons, String missing) {
    public EvidenceSufficiencyResponse {
        selectedEvidenceIds = List.copyOf(selectedEvidenceIds);
        selectionReasons = Map.copyOf(selectionReasons);
        missing = missing == null ? "" : missing;
    }
}
