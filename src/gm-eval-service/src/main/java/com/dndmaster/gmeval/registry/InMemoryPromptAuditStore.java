package com.dndmaster.gmeval.registry;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryPromptAuditStore implements PromptAuditStore {
    private List<PromptAuditEntry> entries = List.of();
    @Override public List<PromptAuditEntry> load() { return List.copyOf(entries); }
    @Override public void save(List<PromptAuditEntry> next) { entries = List.copyOf(new ArrayList<>(next)); }
}
