package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import java.util.List;

/** Reads already-published evidence from the exact pages referenced by runtime evidence. */
public interface RuntimeEvidenceLocationLookupPort {
    List<RuntimeEvidence> findOnSamePages(EvidenceSearchScope scope, List<RuntimeEvidence> anchors, int limit);
}
