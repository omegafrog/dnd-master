package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.GmContextCheckpoint;
import java.util.Objects;

public final class ResumedGmContextAssembler {
    public ResumedGmContext assemble(GmContextCheckpoint checkpoint, AuthoritativeRuntimeSnapshots snapshots) {
        Objects.requireNonNull(checkpoint); Objects.requireNonNull(snapshots);
        return new ResumedGmContext(checkpoint.summary(), checkpoint.exactTail(), snapshots.characterSnapshot(),
                snapshots.mapSnapshot(), snapshots.factSnapshot(), snapshots.clockSnapshot());
    }
}
