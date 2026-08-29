package com.dndmaster.gmeval.registry;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryPromptApprovalStore implements PromptApprovalStore {
    private List<PromptApprovalRecord> records = List.of();

    @Override public List<PromptApprovalRecord> load() { return List.copyOf(records); }
    @Override public void save(List<PromptApprovalRecord> next) { records = List.copyOf(new ArrayList<>(next)); }
}
