package com.dndmaster.adventure.application.runtime;

import java.util.List;

public interface RuntimeEvidenceSearchPort {
    List<RuntimeEvidence> search(RuntimeEvidenceSearchRequest request);
}
