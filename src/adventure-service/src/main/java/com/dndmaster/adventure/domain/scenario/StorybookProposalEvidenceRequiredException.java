package com.dndmaster.adventure.domain.scenario;

public final class StorybookProposalEvidenceRequiredException extends IllegalArgumentException {
    public StorybookProposalEvidenceRequiredException(String proposalId) {
        super("storybook proposal requires source evidence before it can be applied: " + proposalId);
    }
}
