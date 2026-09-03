package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.domain.adventure.RetrievalScope;
import com.dndmaster.adventure.domain.adventure.SemanticVerdict;
import com.dndmaster.adventure.domain.adventure.SemanticVerdictType;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

/** Semantic boundary: providers receive evidence, never raw document access. */
public final class StoryPlanSemanticConsistencyJudge {
    public static final int MAX_RAG_CALLS = 3;
    private final SemanticJudgeProvider provider;
    private final ScopedEvidenceReadPort rag;
    private final RetrievalScope scope;

    public StoryPlanSemanticConsistencyJudge(SemanticJudgeProvider provider, ScopedEvidenceReadPort rag, RetrievalScope scope) {
        this.provider = Objects.requireNonNull(provider); this.rag = Objects.requireNonNull(rag); this.scope = Objects.requireNonNull(scope);
    }

    public SemanticVerdict judge(EvidencePack evidencePack, String candidate) {
        return judge(evidencePack, candidate, null);
    }

    public SemanticVerdict judge(EvidencePack evidencePack, String candidate, UUID ownerId) {
        if (evidencePack == null || candidate == null || candidate.isBlank()) return SemanticVerdict.judgeUnavailable("invalid judge input");
        AtomicInteger calls = new AtomicInteger();
        ScopedEvidenceReadPort bounded = (requestedScope, query) -> {
            if (requestedScope != scope) throw new IllegalArgumentException("judge scope is locked");
            if (calls.get() >= Math.min(MAX_RAG_CALLS, scope.maxCalls())) throw new IllegalStateException("judge RAG call budget exhausted");
            calls.incrementAndGet();
            return rag.search(scope, query);
        };
        try {
            SemanticVerdict last = SemanticVerdict.uncertain("judge", "semantic evidence is insufficient");
            for (int attempt = 0; attempt < MAX_RAG_CALLS; attempt++) {
                SemanticJudgeProvider.Response response = provider.judge(
                        new SemanticJudgeProvider.Request(evidencePack, candidate, scope, bounded, ownerId));
                if (response == null || response.verdict() == null) return SemanticVerdict.judgeUnavailable("provider returned malformed verdict");
                last = response.verdict();
                if (last.type() != SemanticVerdictType.UNCERTAIN || calls.get() >= Math.min(MAX_RAG_CALLS, scope.maxCalls())) return last;
            }
            return last;
        } catch (RuntimeException failure) {
            return SemanticVerdict.judgeUnavailable(failure.getMessage() == null ? "semantic judge unavailable" : failure.getMessage());
        }
    }
}
