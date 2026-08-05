package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.GmContextCheckpoint;
import java.util.Objects;

public final class ResumedGmContextAssembler {
    public ResumedGmContext assemble(GmContextCheckpoint checkpoint, AuthoritativeRuntimeSnapshots snapshots) {
        Objects.requireNonNull(checkpoint); Objects.requireNonNull(snapshots);
        var refs = checkpoint.snapshotReferences();
        if (snapshots.characterVersion() >= 0 && snapshots.characterVersion() < refs.characterVersion()
                || snapshots.mapVersion() >= 0 && snapshots.mapVersion() < refs.mapVersion()
                || snapshots.factVersion() >= 0 && snapshots.factVersion() < refs.factVersion()
                || snapshots.clockVersion() >= 0 && snapshots.clockVersion() < refs.clockVersion()) {
            throw new IllegalStateException("authoritative snapshot version mismatch");
        }
        return new ResumedGmContext(checkpoint.summary(), checkpoint.exactTail(), snapshots.characterSnapshot(),
                snapshots.mapSnapshot(), snapshots.factSnapshot(), snapshots.clockSnapshot());
    }
}
