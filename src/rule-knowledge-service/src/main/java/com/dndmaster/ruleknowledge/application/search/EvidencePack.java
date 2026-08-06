package com.dndmaster.ruleknowledge.application.search;

import java.util.List;

public record EvidencePack(List<EvidencePackEntry> entries, boolean degraded) {
    public EvidencePack {
        entries = List.copyOf(entries);
    }
}
