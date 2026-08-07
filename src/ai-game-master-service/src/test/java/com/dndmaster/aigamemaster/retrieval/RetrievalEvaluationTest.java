package com.dndmaster.aigamemaster.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class RetrievalEvaluationTest {
    @Test
    void relevance_accepts_only_declared_alternatives_and_secrets_are_hard_failures() {
        var expected = new RetrievalReference("doc-1", "page:1", "v1");
        var alternative = new RetrievalReference("doc-2", "page:4", "v1");
        var evaluationCase = new RetrievalEvaluationCase("case-1", "rule", "open door", "owner", "session", "package",
                List.of(expected), List.of(alternative), List.of(new RetrievalReference("secret", "page:9", "v1")), "rule");

        assertEquals(Relevance.RELEVANT, evaluationCase.relevance(alternative));
        assertEquals(Relevance.FORBIDDEN, evaluationCase.relevance(new RetrievalReference("secret", "page:9", "v1")));
        assertEquals(Relevance.IRRELEVANT, evaluationCase.relevance(new RetrievalReference("doc-3", "page:1", "v1")));
    }

    @Test
    void metric_fixture_matches_hand_worked_recall_precision_mrr_and_ndcg() {
        var expected = new RetrievalReference("doc-1", "page:1", "v1");
        var result = new RetrievalEvaluationResult("case-1", List.of(
                new RetrievalCandidate(expected, "owner", "session", "package", 0.9),
                new RetrievalCandidate(new RetrievalReference("doc-2", "page:2", "v1"), "owner", "session", "package", 0.8)),
                12.0);
        var metrics = RetrievalMetrics.evaluate(List.of(new RetrievalEvaluationCase("case-1", "rule", "q", "owner", "session", "package",
                List.of(expected), List.of(), List.of(), "rule")), List.of(result));

        assertEquals(1.0, metrics.recallAt1());
        assertEquals(0.5, metrics.precisionAt5());
        assertEquals(1.0, metrics.mrr());
        assertEquals(1.0, metrics.ndcgAt5());
        assertEquals(12.0, metrics.latencyP50Ms());
    }

    @Test
    void corpus_resource_contains_one_hundred_cases() throws Exception {
        var corpus = RetrievalEvaluationCorpusLoader.load(
                getClass().getResourceAsStream("/retrieval-evaluation-corpus.json"), new ObjectMapper());
        assertEquals(100, corpus.cases().size());
        assertEquals("retrieval-evaluation-v1", corpus.version());
        assertEquals(30, corpus.cases().stream().filter(c -> c.evidenceType().equals("rule")).count());
        assertEquals(5, corpus.cases().stream().filter(c -> c.evidenceType().equals("secrecy")).count());
    }

    @Test
    void runner_records_candidates_and_marks_secret_or_scope_violations_as_hard_failures() {
        var expected = new RetrievalReference("doc-1", "page:1", "v1");
        var corpus = new RetrievalEvaluationCorpus("retrieval-evaluation-v1", List.of(
                new RetrievalEvaluationCase("case-1", "secrecy", "q", "owner", "session", "package",
                        List.of(expected), List.of(), List.of(new RetrievalReference("secret", "page:1", "v1")), "secrecy")));
        var report = new RetrievalEvaluationRunner().runReport(corpus, (evaluationCase, limit) ->
                new RetrievalEvaluationResult("case-1", List.of(
                        new RetrievalCandidate(new RetrievalReference("secret", "page:1", "v1"), "other-owner", "session", "package", 1.0)), 7));

        assertEquals(1, report.results().size());
        assertEquals(1.0, report.metrics().secretRetrievalRate());
        assertEquals(1.0, report.metrics().scopeViolationRate());
        assertEquals(true, report.hasHardFailures());
        assertThrows(IllegalStateException.class, report::assertNoHardFailures);
    }

    @Test
    void report_is_persisted_as_versioned_json() throws Exception {
        var expected = new RetrievalReference("doc-1", "page:1", "v1");
        var corpus = new RetrievalEvaluationCorpus("retrieval-evaluation-v1", List.of(
                new RetrievalEvaluationCase("case-1", "rule", "q", "owner", "session", "package", List.of(expected), List.of(), List.of(), "rule")));
        var report = new RetrievalEvaluationRunner().runReport(corpus, (c, limit) ->
                new RetrievalEvaluationResult(c.id(), List.of(new RetrievalCandidate(expected, c.ownerId(), c.sessionId(), c.packageId(), 1)), 4));
        var path = new RetrievalEvaluationArtifactStore(new ObjectMapper()).write(Files.createTempDirectory("retrieval-report"), report);
        assertEquals(true, Files.exists(path));
        assertEquals(true, Files.readString(path).contains("retrieval-evaluation-report.v1"));
    }

    @Test
    void artifact_persists_reproducibility_identity_alongside_report() throws Exception {
        var expected = new RetrievalReference("doc-1", "page:1", "v1");
        var corpus = new RetrievalEvaluationCorpus("retrieval-evaluation-v1", List.of(
                new RetrievalEvaluationCase("case-1", "rule", "q", "owner", "session", "package", List.of(expected), List.of(), List.of(), "rule")));
        var report = new RetrievalEvaluationRunner().runReport(corpus, (c, limit) ->
                new RetrievalEvaluationResult(c.id(), List.of(new RetrievalCandidate(expected, c.ownerId(), c.sessionId(), c.packageId(), 1)), 4));
        var path = new RetrievalEvaluationArtifactStore(new ObjectMapper()).write(
                Files.createTempDirectory("retrieval-artifact"), report,
                new RetrievalEvaluationIdentity("corpus-sha", "embedding-model", "index-v1", "service-v1", "config-sha"));

        var artifact = new ObjectMapper().readTree(path.toFile());
        assertEquals("embedding-model", artifact.path("identity").path("embeddingModel").asText());
        assertEquals("retrieval-evaluation-report.v1", artifact.path("report").path("schemaVersion").asText());
    }

    @Test
    void corpus_validation_rejects_expected_reference_outside_seed_scope() {
        var expected = new RetrievalReference("doc-1", "page:1", "v1");
        var corpus = new RetrievalEvaluationCorpus("retrieval-evaluation-v1", List.of(
                new RetrievalEvaluationCase("case-1", "rule", "q", "owner", "session", "package",
                        List.of(expected), List.of(), List.of(), "rule", "REQUIRE_EVIDENCE", List.of(
                                new RetrievalReference("doc-2", "page:2", "v1")))));

        assertThrows(IllegalArgumentException.class, () -> RetrievalEvaluationCorpusValidator.validate(corpus));
    }

    @Test
    void reproducibility_identity_rejects_unreleased_values() {
        assertThrows(IllegalArgumentException.class, () -> new RetrievalEvaluationIdentity(
                "unreleased", "embedding", "index", "service", "config"));
    }

    @Test
    void corpus_and_results_reject_duplicate_case_ids() {
        var c = new RetrievalEvaluationCase("case-1", "rule", "q", "owner", "session", "package", List.of(
                new RetrievalReference("doc", "page:1", "v1")), List.of(), List.of(), "rule");
        assertThrows(IllegalArgumentException.class, () -> new RetrievalEvaluationCorpus("v1", List.of(c, c)));
        var result = new RetrievalEvaluationResult("case-1", List.of(), 1);
        assertThrows(IllegalArgumentException.class, () -> RetrievalMetrics.evaluate(List.of(c, new RetrievalEvaluationCase(
                "case-2", "rule", "q", "owner", "session", "package", List.of(new RetrievalReference("doc", "page:2", "v1")), List.of(), List.of(), "rule")), List.of(result, result)));
    }
}
