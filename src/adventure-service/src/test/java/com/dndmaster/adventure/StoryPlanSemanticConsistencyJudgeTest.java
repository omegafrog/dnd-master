package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.storyplan.*;
import com.dndmaster.adventure.domain.adventure.SemanticVerdict;
import com.dndmaster.adventure.domain.adventure.SemanticVerdictType;
import com.dndmaster.adventure.domain.adventure.RetrievalScope;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryPlanSemanticConsistencyJudgeTest {
    private static final EvidencePack PACK = new EvidencePack(List.of(new RuntimeEvidence(
            RuntimeEvidenceType.STORYBOOK, new KnowledgeDocumentId(UUID.randomUUID()), 1,
            "chapter/1", "The gate is closed.", "sb-1")), List.of(), List.of());

    @Test
    void accepts_compatible_elaboration_and_keeps_verdict_shape() {
        var rag = mock(ScopedEvidenceReadPort.class);
        SemanticJudgeProvider provider = request -> new SemanticJudgeProvider.Response(
                SemanticVerdict.compatible(.92, "stages[0].obstacle", "Adds difficulty without changing the gate state",
                        Set.of("sb-1"), Set.of("rag-1")));
        var judge = new StoryPlanSemanticConsistencyJudge(provider, rag,
                new RetrievalScope(Set.of(PACK.storybook().getFirst().knowledgeDocumentId()), Set.of(UUID.randomUUID()), 3));

        var verdict = judge.judge(PACK, "The party must find a key before approaching the closed gate.");

        assertEquals(SemanticVerdictType.COMPATIBLE, verdict.type());
        assertTrue(verdict.confidence() > 0);
        assertFalse(verdict.summary().isBlank());
    }

    @Test
    void limits_scoped_retrieval_to_three_calls_and_never_allows_unlocked_documents() {
        var rag = mock(ScopedEvidenceReadPort.class);
        when(rag.search(any(), any())).thenReturn(new ScopedEvidenceReadPort.Result(List.of(), Set.of()));
        SemanticJudgeProvider provider = request -> {
            request.evidenceRead().search(request.scope(), "claim");
            return SemanticJudgeProvider.Response.uncertain("stages[0]", "Insufficient evidence");
        };
        var scope = new RetrievalScope(Set.of(PACK.storybook().getFirst().knowledgeDocumentId()), Set.of(), 3);
        var verdict = new StoryPlanSemanticConsistencyJudge(provider, rag, scope).judge(PACK, "claim");

        assertEquals(SemanticVerdictType.UNCERTAIN, verdict.type());
        verify(rag, times(3)).search(eq(scope), any());
        assertFalse(scope.allows(new KnowledgeDocumentId(UUID.randomUUID())));
    }

    @Test
    void maps_uncertain_to_ready_warning_and_failures_to_bounded_block() {
        assertEquals(StoryPlanVerdictPolicy.Decision.READY_WITH_WARNING,
                StoryPlanVerdictPolicy.decide(SemanticVerdict.uncertain("stage", "unknown"), 1, 3));
        var unavailable = SemanticVerdict.judgeUnavailable("timeout");
        assertEquals(StoryPlanVerdictPolicy.Decision.RETRY,
                StoryPlanVerdictPolicy.decide(unavailable, 1, 3));
        assertEquals(StoryPlanVerdictPolicy.Decision.BLOCK,
                StoryPlanVerdictPolicy.decide(unavailable, 3, 3));
    }

    @Test
    void treats_provider_timeout_and_malformed_response_as_unavailable() {
        var scope = new RetrievalScope(Set.of(), Set.of(), 3);
        var rag = mock(ScopedEvidenceReadPort.class);
        var timeout = new StoryPlanSemanticConsistencyJudge(request -> { throw new RuntimeException("timeout"); }, rag, scope)
                .judge(PACK, "claim");
        var malformed = new StoryPlanSemanticConsistencyJudge(request -> null, rag, scope)
                .judge(PACK, "claim");

        assertEquals("JUDGE_UNAVAILABLE", timeout.failureCode());
        assertEquals("JUDGE_UNAVAILABLE", malformed.failureCode());
        assertEquals(StoryPlanVerdictPolicy.Decision.BLOCK,
                StoryPlanVerdictPolicy.decide(timeout, 3, 3));
    }

    @Test
    void serializes_verdict_and_history_without_hidden_reasoning() throws Exception {
        var verdict = SemanticVerdict.contradictory(.99, "stages[1].outcome", "Outcome reverses Storybook result",
                Set.of("sb-1"), Set.of("rag-2"));
        var json = StoryPlanVerdictJson.serialize(List.of(verdict));
        assertTrue(json.contains("CONTRADICTORY"));
        assertTrue(json.contains("claimPath"));
        assertFalse(json.contains("chainOfThought"));
        assertEquals(1, StoryPlanVerdictJson.deserialize(json).size());
    }
}
