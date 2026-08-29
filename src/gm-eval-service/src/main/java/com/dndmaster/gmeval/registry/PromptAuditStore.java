package com.dndmaster.gmeval.registry;

import java.util.List;

public interface PromptAuditStore {
    List<PromptAuditEntry> load();
    void save(List<PromptAuditEntry> entries);
}
