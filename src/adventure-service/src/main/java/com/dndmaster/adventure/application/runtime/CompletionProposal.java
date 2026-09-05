package com.dndmaster.adventure.application.runtime;

/** GM proposal; it becomes canonical only together with a safe turn commit. */
public record CompletionProposal(boolean complete, String concludingScene) {
    public CompletionProposal {
        concludingScene = concludingScene == null ? "" : concludingScene.trim();
        if (complete && concludingScene.isBlank()) {
            throw new IllegalArgumentException("completed adventure requires a concluding scene");
        }
    }

    public static CompletionProposal continueAdventure() { return new CompletionProposal(false, ""); }
}
