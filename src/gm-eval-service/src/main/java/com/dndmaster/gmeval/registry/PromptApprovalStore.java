package com.dndmaster.gmeval.registry;

import java.util.List;

public interface PromptApprovalStore {
    List<PromptApprovalRecord> load();
    void save(List<PromptApprovalRecord> records);
}
