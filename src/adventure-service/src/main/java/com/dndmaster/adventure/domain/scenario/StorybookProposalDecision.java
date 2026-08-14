package com.dndmaster.adventure.domain.scenario;

import java.util.Objects;

public record StorybookProposalDecision(String proposalId, String fieldKey, ProposalDecisionState state) {
    public StorybookProposalDecision {
        if (proposalId == null || proposalId.isBlank()) throw new IllegalArgumentException("proposal id must not be blank");
        if (fieldKey == null || fieldKey.isBlank()) throw new IllegalArgumentException("proposal field key must not be blank");
        state = Objects.requireNonNull(state, "proposal decision state must not be null");
    }
}
