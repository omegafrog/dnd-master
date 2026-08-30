package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.domain.adventure.RetrievalScope;
import java.util.List;
import java.util.Set;

public interface ScopedEvidenceReadPort {
    Result search(RetrievalScope scope, String query);
    record Result(List<RuntimeEvidence> evidence, Set<String> retrievalRefs) {
        public Result { evidence = evidence == null ? List.of() : List.copyOf(evidence); retrievalRefs = retrievalRefs == null ? Set.of() : Set.copyOf(retrievalRefs); }
    }
}
