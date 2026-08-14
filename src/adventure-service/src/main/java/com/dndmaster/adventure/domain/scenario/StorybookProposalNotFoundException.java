package com.dndmaster.adventure.domain.scenario;

public final class StorybookProposalNotFoundException extends RuntimeException {
    private final String proposalId;

    public StorybookProposalNotFoundException(String proposalId) {
        super("storybook proposal was not found: " + proposalId);
        this.proposalId = proposalId;
    }

    public String proposalId() {
        return proposalId;
    }
}
