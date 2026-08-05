package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.dndmaster.adventure.domain.runtime.GmTurnStatus;

public final class GmTurnCommitPolicy {
    private GmTurnCommitPolicy() {}

    public static void requirePublishable(GmTurn turn, long committedVersion) {
        if (turn.status() != GmTurnStatus.COMMITTED) throw new IllegalStateException("only committed GM turns are publishable");
        if (committedVersion != turn.expectedSessionVersion() + 1) {
            throw new IllegalStateException("published version does not match turn commit");
        }
    }
}
