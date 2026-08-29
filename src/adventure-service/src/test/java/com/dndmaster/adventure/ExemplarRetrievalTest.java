package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.ExemplarAdmissionCandidate;
import com.dndmaster.adventure.application.runtime.ExemplarAdmissionPolicy;
import com.dndmaster.adventure.application.runtime.ExemplarQuery;
import com.dndmaster.adventure.application.runtime.ExemplarResult;
import com.dndmaster.adventure.application.runtime.ExemplarRetrieverPort;
import com.dndmaster.adventure.application.runtime.ExemplarRetrievalAudit;
import com.dndmaster.adventure.application.runtime.ExemplarRetrievalAuditor;
import com.dndmaster.adventure.application.runtime.InMemoryExemplarCatalogIndexAdapter;
import com.dndmaster.adventure.application.runtime.Provenance;
import com.dndmaster.adventure.application.runtime.StyleExemplar;
import com.dndmaster.adventure.application.runtime.VerificationResult;
import com.dndmaster.adventure.application.runtime.WriterContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExemplarRetrievalTest {
    private static final Provenance CURATED = new Provenance("catalog-1", "style-exemplar", "v1");

    @Test
    void admitsOnlyCuratedNonErrorNonPollutingExamples() {
        StyleExemplar example = exemplar("good", true);
        ExemplarAdmissionPolicy policy = new ExemplarAdmissionPolicy();

        assertTrue(policy.admit(new ExemplarAdmissionCandidate(example, VerificationResult.pass(), false, false)));
        assertFalse(policy.admit(new ExemplarAdmissionCandidate(example, new VerificationResult(
                com.dndmaster.adventure.application.runtime.VerificationStatus.FAIL, List.of(), 0), false, false)));
        assertFalse(policy.admit(new ExemplarAdmissionCandidate(example, VerificationResult.pass(), true, false)));
        assertFalse(policy.admit(new ExemplarAdmissionCandidate(example, VerificationResult.pass(), false, true)));
    }

    @Test
    void filtersMetadataThenRanksSemanticallyAndBoundsK() {
        StyleExemplar match = exemplar("match", true);
        StyleExemplar otherTone = new StyleExemplar("other", "other", "exploration", "dialogue", "grim", "slow", "short", CURATED, true);
        InMemoryExemplarCatalogIndexAdapter adapter = new InMemoryExemplarCatalogIndexAdapter(List.of(match, otherTone));
        ExemplarQuery query = new ExemplarQuery("exploration", "dialogue", "warm", "steady", "short", "opening a door", 1);

        List<ExemplarResult> results = adapter.retrieve(query);

        assertEquals(1, results.size());
        assertEquals("match", results.getFirst().exemplar().id());
        assertTrue(results.getFirst().provenance().purpose().equals("style-exemplar"));
    }

    @Test
    void writerContextCarriesStyleResultsSeparatelyFromGroundedFacts() {
        ExemplarResult result = new ExemplarResult(exemplar("one", true), .8, .9, .85, 1);
        WriterContext context = new WriterContext(List.of("known fact"), "scene", List.of(result), "");

        assertEquals(List.of("known fact"), context.visibleFacts());
        assertEquals(List.of(result), context.styleExemplars());
        assertEquals("one", context.styleExemplars().getFirst().exemplar().id());
        assertEquals(List.of("scene"), context.styleHints());
    }

    @Test
    void auditRecordsIdsScoresAndUsesEmptyFallbackOnIndexFailure() {
        List<ExemplarRetrievalAudit> audits = new java.util.ArrayList<>();
        ExemplarQuery query = new ExemplarQuery("scene", "interaction", "warm", "steady", "short", "door", 3);
        ExemplarRetrieverPort failing = ignored -> { throw new IllegalStateException("index unavailable"); };

        assertTrue(new ExemplarRetrievalAuditor(failing, audits::add, "embedding-v1").retrieve(query).isEmpty());
        assertEquals(List.of(), audits.getFirst().retrievalIds());
        assertEquals("embedding-v1", audits.getFirst().model());
    }

    private static StyleExemplar exemplar(String id, boolean generic) {
        return new StyleExemplar(id, "scene", "exploration", "dialogue", "warm", "steady", "short", CURATED, generic);
    }
}
