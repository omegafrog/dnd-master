package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.domain.adventure.RetrievalScope;
import com.dndmaster.adventure.domain.adventure.SemanticVerdict;
import java.util.UUID;

public interface SemanticJudgeProvider {
    Response judge(Request request);
    record Request(EvidencePack evidencePack, String candidate, RetrievalScope scope,
                   ScopedEvidenceReadPort evidenceRead, UUID ownerId) {
        public Request(EvidencePack evidencePack, String candidate, RetrievalScope scope,
                ScopedEvidenceReadPort evidenceRead) {
            this(evidencePack, candidate, scope, evidenceRead, null);
        }
    }
    record Response(SemanticVerdict verdict) {
        public Response { if (verdict == null) throw new IllegalArgumentException("judge verdict is required"); }
        public static Response uncertain(String path, String summary) { return new Response(SemanticVerdict.uncertain(path, summary)); }
    }
}
