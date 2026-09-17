package com.dndmaster.adventure.domain.runtime;

/** 게임 마스터가 제안한 모험 완료 상태. 모험 집계가 안전한 턴과 함께 반영한다. */
public record CompletionProposal(boolean complete, String concludingScene) {
    public CompletionProposal {
        concludingScene = concludingScene == null ? "" : concludingScene.trim();
        if (complete && concludingScene.isBlank()) {
            throw new IllegalArgumentException("completed adventure requires a concluding scene");
        }
    }

    public static CompletionProposal continueAdventure() {
        return new CompletionProposal(false, "");
    }
}
