package com.dndmaster.ruleknowledge.application.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.application.search.AuthorizedDocumentScope;
import com.dndmaster.ruleknowledge.application.search.DenseEvidenceCandidateSearchPort;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceLocationLookupPort;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest;
import com.dndmaster.ruleknowledge.application.search.DenseEvidenceSearchService;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RulebookActionDefinitionExtractionServiceTest {
    private static final UUID DOCUMENT_ID = UUID.fromString("89ef62fc-b2dd-4b1b-91c9-04810d880f3a");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EVIDENCE_ID = UUID.fromString("82ad2366-a013-4d13-b922-9fa2c5970634");
    private static final String VERSION = "ev-test";
    private static final List<PreprocessingPageState> PAGES = List.of(new PreprocessingPageState(1, "COMPLETED", 1, List.of()));
    private static final EvidenceCandidate CANDIDATE = new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID),
            new ChunkId(EVIDENCE_ID), 7, DocumentType.RULEBOOK, "page=1:section=Spells", "Magic Missile has range 120 feet.",
            new SourceProvenance(1, List.of("Spells"), List.of(), null, "page=1"), 1,null);

    @Test
    void targetedCandidateCountPreservesTheFull64ResultSearchLimit() {
        var candidate = new RulebookActionCandidate("action-id", "Action", "SPELL", CANDIDATE.locator(), EVIDENCE_ID.toString(), "Action");
        var assignment = new RulebookActionAssignment(candidate, List.of(EVIDENCE_ID.toString()), "fingerprint", 64);

        assertEquals(64, assignment.candidateCount());
        assertThrows(IllegalArgumentException.class,
                () -> new RulebookActionAssignment(candidate, List.of(EVIDENCE_ID.toString()), "fingerprint", 65));
    }

    @Test
    void searchesBoundedDenseQueriesAndDeduplicatesEvidenceBeforeTheAgentCall(CapturedOutput output) {
        var repository = new MemoryRepository();
        var requests = new ArrayList<EvidenceSearchRequest>();
        DenseEvidenceCandidateSearchPort search = request -> { requests.add(request); return List.of(CANDIDATE); };
        var searchBoundary = new DenseEvidenceSearchService(search);
        var service = new RulebookActionDefinitionExtractionService(repository, searchBoundary,
                (owner, scope, locators, limit) -> Map.of(), (document, version, evidence, coverage, actor) -> {
            assertEquals(1, evidence.size());
            assertEquals(EVIDENCE_ID.toString(), evidence.getFirst().id());
            assertTrue(coverage.isEmpty() || coverage.size() == 1 || coverage.size() == 5);
            return List.of(new RulebookActionDefinition("evidence:" + evidence.getFirst().id() + ":spell", "Magic Missile", "SPELL",
                    evidence.getFirst().locator(), evidence.getFirst().id(), "Magic Missile has range 120 feet.", Map.of("range", "120 feet"), List.of()));
        }, null, 128_000, 24_000);

        String previousDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        RulebookActionDefinitionExtraction result;
        try { result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID); }
        finally { restoreDiagnostics(previousDiagnostics); }

        assertEquals(5, requests.size());
        assertEquals(5, requests.stream().map(EvidenceSearchRequest::query).distinct().count());
        assertEquals(30, requests.getFirst().denseLimit());
        AuthorizedDocumentScope scope = requests.getFirst().scope().getFirst();
        assertEquals(DOCUMENT_ID, scope.documentId().value());
        assertEquals(7, scope.extractionVersion());
        assertEquals(DocumentType.RULEBOOK, scope.documentType());
        assertEquals(0, result.scannedChunkCount());
        assertEquals(1, result.candidateEvidenceCount());
        assertEquals(5, result.queryCoverage().size());
        assertEquals("thrown-actions", result.queryCoverage().getLast().queryId());
        assertEquals(1, result.queryCoverage().getLast().candidateCount());
        assertEquals(false, result.evidenceLimitReached());
        assertEquals(1, result.definitions().size());
        assertTrue(output.toString().contains("stage=batch_completed documentId=" + DOCUMENT_ID
                + " extractionVersion=" + VERSION + " batchIndex=0 expectedEvidenceCount=1 actualCandidateCount=1 elapsedMs="));
    }

    @Test
    void gathersEveryQueryBeforeApplyingBalancedEvidenceCaps() {
        var repository = new MemoryRepository();
        AtomicInteger queryIndex = new AtomicInteger();
        AtomicInteger agentEvidence = new AtomicInteger();
        var service = new RulebookActionDefinitionExtractionService(repository, request -> {
            int currentQuery = queryIndex.getAndIncrement();
            return java.util.stream.IntStream.range(0, 8).mapToObj(rank -> {
                UUID chunkId = UUID.nameUUIDFromBytes((currentQuery + ":" + rank).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(chunkId), 7,
                        DocumentType.RULEBOOK, "page=" + (currentQuery + 1), "query=" + currentQuery + " rank=" + rank,
                        CANDIDATE.provenance(), rank + 1,null);
            }).toList();
        }, (document, version, evidence, coverage, actor) -> {
            assertEquals(5, coverage.size());
            assertTrue(evidence.size() <= 48);
            agentEvidence.addAndGet(evidence.size());
            return List.of();
        });

        var result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals(5, queryIndex.get());
        assertEquals(40, agentEvidence.get());
        assertEquals(40, result.candidateEvidenceCount());
        assertEquals(true, result.evidenceLimitReached());
    }

    @Test
    void emptyRetrievalCompletesWithZeroDefinitionsWithoutCallingTheAgent() {
        var service = new RulebookActionDefinitionExtractionService(new MemoryRepository(), request -> List.of(),
                (document, version, evidence, coverage, actor) -> { throw new AssertionError("empty retrieval must not call agent"); });

        var result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals("COMPLETED", result.status());
        assertEquals(0, result.candidateEvidenceCount());
        assertEquals(0, result.definitions().size());
    }

    @Test
    void splitsOutputLimitedAssignedBatchAndKeepsEachChildEvidenceScope() {
        EvidenceCandidate list = new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(EVIDENCE_ID), 7,
                DocumentType.RULEBOOK, "page=1", "Alpha Beta", CANDIDATE.provenance(), 1,null);
        EvidenceCandidate alpha = evidenceCandidate("alpha-source", 30);
        EvidenceCandidate beta = evidenceCandidate("beta-source", 30);
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(new RulebookActionCandidate("alpha-id", "Alpha", "SPELL", list.locator(), EVIDENCE_ID.toString(), "Alpha"),
                        new RulebookActionCandidate("beta-id", "Beta", "SPELL", list.locator(), EVIDENCE_ID.toString(), "Beta"));
            }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                if (assigned.size() == 2) throw new RulebookActionOutputLimitException();
                assertEquals(1, evidence.size());
                var candidate = assigned.getFirst().candidate();
                var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(),
                        list.locator(), EVIDENCE_ID.toString(), candidate.name(), Map.of(), List.of());
                return List.of(new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", definition, null));
            }
        };
        AtomicInteger assignedCalls = new AtomicInteger();
        RulebookActionDefinitionExtractionPort counted = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return extractor.extract(document, version, evidence, coverage, actor); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return extractor.discover(document, version, evidence, coverage, actor); }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                assignedCalls.incrementAndGet();
                return extractor.extractAssignedBatch(document, version, assigned, evidence, coverage, actor);
            }
        };
        var repository = new MemoryRepository();
        DenseEvidenceCandidateSearchPort search = request -> request.query().startsWith("Alpha ") ? List.of(alpha)
                : request.query().startsWith("Beta ") ? List.of(beta) : List.of(list);

        var result = new RulebookActionDefinitionExtractionService(repository, search, counted)
                .extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals(3, assignedCalls.get());
        assertEquals("COMPLETED", result.status());
        assertEquals(List.of("alpha-id", "beta-id"), result.definitions().stream().map(RulebookActionDefinition::id).toList());
        assertEquals(List.of("alpha-id", "beta-id"), repository.batches.get(DOCUMENT_ID + ":" + VERSION + ":100000")
                .results().stream().map(RulebookActionDefinitionResult::candidateId).toList());
    }

    @Test
    void requiresOneAccountedResultForEachDiscoveredCandidate() {
        EvidenceCandidate listEntry = new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(EVIDENCE_ID), 7,
                DocumentType.RULEBOOK, "page=1:section=Spell List", "Magic Missile; Fireball",
                CANDIDATE.provenance(), 1,null);
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(new RulebookActionCandidate("magic-id", "Magic Missile", "SPELL", listEntry.locator(),
                                EVIDENCE_ID.toString(), "Magic Missile"),
                        new RulebookActionCandidate("fireball-id", "Fireball", "SPELL", listEntry.locator(),
                                EVIDENCE_ID.toString(), "Fireball"));
            }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return assigned.stream().map(item -> item.candidate().id().equals("magic-id")
                        ? new RulebookActionDefinitionResult("magic-id", "COMPLETED",
                                new RulebookActionDefinition("magic-id", "Magic Missile", "SPELL", listEntry.locator(),
                                        EVIDENCE_ID.toString(), "Magic Missile", Map.of(), List.of()), null)
                        : new RulebookActionDefinitionResult("fireball-id", "INSUFFICIENT_EVIDENCE", null, "missing_effect_details"))
                        .toList();
            }
        };
        var repository = new MemoryRepository();
        var service = new RulebookActionDefinitionExtractionService(repository, request -> List.of(listEntry), extractor);

        var result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals("COMPLETED", result.status());
        assertEquals(List.of("magic-id:COMPLETED", "fireball-id:INSUFFICIENT_EVIDENCE"), result.actionCoverage().stream()
                .map(item -> item.id() + ":" + item.status()).toList());
        assertEquals(List.of("magic-id"), result.definitions().stream().map(RulebookActionDefinition::id).toList());
        assertEquals(2, repository.batches.get(DOCUMENT_ID + ":" + VERSION + ":100000").results().size());
    }

    @Test
    void eachActionStartsFromItsOwnNamedSourceWithoutAppendingSharedSearchResults() {
        EvidenceCandidate listEntry = new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(EVIDENCE_ID), 7,
                DocumentType.RULEBOOK, "page=1:section=Actions", "Alpha; Beta", CANDIDATE.provenance(), 1,null);
        var assignedInputs = new ArrayList<List<String>>();
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(new RulebookActionCandidate("alpha-id", "Alpha", "SPELL", listEntry.locator(), EVIDENCE_ID.toString(), "Alpha"),
                        new RulebookActionCandidate("beta-id", "Beta", "SPELL", listEntry.locator(), EVIDENCE_ID.toString(), "Beta"));
            }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                assignedInputs.add(evidence.stream().map(RulebookActionEvidence::id).toList());
                assertEquals(List.of(EVIDENCE_ID.toString()), assignedInputs.getLast());
                return assigned.stream().map(item -> {
                    var candidate = item.candidate();
                    var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(),
                            listEntry.locator(), EVIDENCE_ID.toString(), candidate.name(), Map.of(), List.of());
                    return new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", definition, null);
                }).toList();
            }
        };
        var result = new RulebookActionDefinitionExtractionService(new MemoryRepository(), request -> List.of(listEntry), extractor)
                .extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals("COMPLETED", result.status());
        assertEquals(1, assignedInputs.size());
        assertEquals(List.of("alpha-id", "beta-id"), result.definitions().stream().map(RulebookActionDefinition::id).toList());
    }

    @Test
    void retrievesOnlyExplicitlyNeededMissingFieldsAndPreservesKnownFacts(CapturedOutput output) {
        EvidenceCandidate source = new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(UUID.nameUUIDFromBytes(
                "own-source".getBytes(java.nio.charset.StandardCharsets.UTF_8))), 7, DocumentType.RULEBOOK, "page=1:section=staff",
                "Signal Staff description.", CANDIDATE.provenance(), 1,null);
        EvidenceCandidate supplement = new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(UUID.nameUUIDFromBytes(
                "needed-cost".getBytes(java.nio.charset.StandardCharsets.UTF_8))), 7, DocumentType.RULEBOOK, "page=1:chunk=page-one-neighbor",
                "Using the staff costs one action.", CANDIDATE.provenance(), 1,null);
        var requests = new ArrayList<EvidenceSearchRequest>();
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(new RulebookActionCandidate("item-id", "Signal Staff", "THROWN", source.locator(),
                        source.chunkId().value().toString(), "Signal Staff"));
            }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                var assignment = assigned.getFirst();
                if (assignment.neededFields().isEmpty()) {
                    assertEquals(List.of(source.chunkId().value().toString()), evidence.stream().map(RulebookActionEvidence::id).toList());
                    Map<String, Object> known = new java.util.LinkedHashMap<>(); known.put("range", "30 feet"); known.put("cost", null);
                    var definition = new RulebookActionDefinition("item-id", "Signal Staff", "THROWN", source.locator(),
                            source.chunkId().value().toString(), "Signal Staff", known, List.of());
                    return List.of(new RulebookActionDefinitionResult("item-id", "COMPLETED", definition, null, List.of("cost")));
                }
                assertEquals(List.of("cost"), assignment.neededFields());
                assertEquals("30 feet", assignment.knownFields().get("range"));
                assertEquals(2, evidence.size());
                Map<String, Object> refined = new java.util.LinkedHashMap<>(); refined.put("range", "60 feet"); refined.put("cost", "one action");
                var definition = new RulebookActionDefinition("item-id", "Signal Staff", "THROWN", supplement.locator(),
                        supplement.chunkId().value().toString(), "Using the staff costs one action.", refined, List.of());
                return List.of(new RulebookActionDefinitionResult("item-id", "COMPLETED", definition, null));
            }
        };
        RulebookActionDefinitionExtractionService service = new RulebookActionDefinitionExtractionService(new MemoryRepository(), request -> {
            requests.add(request); return List.of(source);
        }, exactLookup(supplement), extractor, null, 128_000, 24_000);

        String previousDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        RulebookActionDefinitionExtraction result;
        try { result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID); }
        finally { restoreDiagnostics(previousDiagnostics); }

        assertEquals(5, requests.size());
        assertTrue(requests.stream().allMatch(request -> request.stageKey().equals("rulebook-action-extraction")));
        assertEquals(source.locator(), result.definitions().getFirst().sourceLocator());
        assertEquals("30 feet", result.definitions().getFirst().fields().get("range"));
        assertEquals("one action", result.definitions().getFirst().fields().get("cost"));
        assertTrue(output.toString().contains("stage=targeted_followup_completed documentId=" + DOCUMENT_ID + " actionId=item-id requestedFields=[cost]"));
    }

    @Test
    void batchesDetailedFollowupForMultipleActionsAndKeepsEachActionsEvidenceScope() {
        EvidenceCandidate source = evidenceCandidate("action-list", 30);
        EvidenceCandidate alphaDetail = withLocator(evidenceCandidate("alpha-detail", 25), source.locator());
        EvidenceCandidate betaDetail = withLocator(evidenceCandidate("beta-detail", 24), source.locator());
        List<RulebookActionCandidate> candidates = List.of(
                new RulebookActionCandidate("alpha-id", "Alpha", "SPELL", source.locator(), source.chunkId().value().toString(), "x"),
                new RulebookActionCandidate("beta-id", "Beta", "SPELL", source.locator(), source.chunkId().value().toString(), "x"));
        AtomicInteger followupCalls = new AtomicInteger();
        var service = new RulebookActionDefinitionExtractionService(new MemoryRepository(), request -> List.of(source),
                exactLookup(alphaDetail, betaDetail), new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return candidates; }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                if (assigned.getFirst().neededFields().isEmpty()) return assigned.stream().map(item -> {
                    var candidate = item.candidate();
                    Map<String, Object> knownFields = new java.util.LinkedHashMap<>();
                    knownFields.put("range", "30 feet"); knownFields.put("cost", null);
                    var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(),
                            source.locator(), source.chunkId().value().toString(), "x", knownFields, List.of());
                    return new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", definition, null, List.of("cost"));
                }).toList();
                followupCalls.incrementAndGet();
                assertEquals(List.of("alpha-id", "beta-id"), assigned.stream().map(item -> item.candidate().id()).toList());
                assertEquals(3, evidence.size());
                assertEquals(List.of("cost"), assigned.getFirst().neededFields());
                assertEquals("30 feet", assigned.getFirst().knownFields().get("range"));
                return assigned.stream().map(item -> {
                    RulebookActionEvidence cited = evidence.stream().filter(value -> value.id().equals(item.candidate().id().startsWith("alpha")
                            ? alphaDetail.chunkId().value().toString() : betaDetail.chunkId().value().toString())).findFirst().orElseThrow();
                    var definition = new RulebookActionDefinition(item.candidate().id(), item.candidate().name(), "SPELL",
                            cited.locator(), cited.id(), cited.text(), Map.of("range", "60 feet", "cost", "one action"), List.of());
                    return new RulebookActionDefinitionResult(item.candidate().id(), "COMPLETED", definition, null);
                }).toList();
            }
        }, null, 128_000, 24_000);

        var result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals(1, followupCalls.get());
        assertEquals(List.of("30 feet", "30 feet"), result.definitions().stream().map(item -> item.fields().get("range")).toList());
        assertEquals(List.of("one action", "one action"), result.definitions().stream().map(item -> item.fields().get("cost")).toList());
    }

    @Test
    void retrievesExactLocatorCandidatesAndKeepsEveryReturnedLongEvidenceExcerpt() {
        var candidate = new RulebookActionCandidate("missing-id", "Named Action", "SPELL", CANDIDATE.locator(),
                EVIDENCE_ID.toString(), CANDIDATE.excerpt());
        List<EvidenceCandidate> targeted = java.util.stream.IntStream.range(0, 49).mapToObj(index -> {
            UUID id = UUID.nameUUIDFromBytes(("targeted-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(id), 7, DocumentType.RULEBOOK,
                    CANDIDATE.locator(), "detail-" + index + " " + "x".repeat(1_000), CANDIDATE.provenance(), index + 1,null);
        }).toList();
        var batchSizes = new ArrayList<Integer>();
        var exactLocators = new ArrayList<List<String>>();
        var exactLimits = new ArrayList<Integer>();
        DenseEvidenceCandidateSearchPort search = new DenseEvidenceCandidateSearchPort() {
            @Override public List<EvidenceCandidate> search(EvidenceSearchRequest request) { throw new AssertionError("batch search expected"); }
            @Override public List<List<EvidenceCandidate>> searchBatch(List<EvidenceSearchRequest> requests) {
                batchSizes.add(requests.size());
                return requests.stream().map(request -> List.of(CANDIDATE)).toList();
            }
        };
        EvidenceLocationLookupPort exact = (owner, scope, locators, limit) -> {
            exactLocators.add(locators); exactLimits.add(limit); return Map.of(locators.getFirst(), targeted);
        };
        AtomicInteger assignedCalls = new AtomicInteger();
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version,
                    List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> coverage, UUID actor) {
                throw new AssertionError("staged extraction expected");
            }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version,
                    List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(candidate);
            }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                if (assignedCalls.getAndIncrement() == 0)
                    return List.of(new RulebookActionDefinitionResult(candidate.id(), "INSUFFICIENT_EVIDENCE", null, "no_definition"));
                assertEquals(50, evidence.size());
                assertEquals(49, assigned.getFirst().candidateCount());
                assertEquals(49, coverage.getFirst().candidateCount());
                assertTrue(evidence.stream().mapToInt(item -> item.text().length()).sum() > 48_000);
                Map<String, RulebookActionEvidence> byId = evidence.stream().collect(java.util.stream.Collectors.toMap(
                        RulebookActionEvidence::id, item -> item));
                targeted.forEach(item -> assertEquals(item.excerpt(), byId.get(item.chunkId().value().toString()).text()));
                RulebookActionEvidence source = evidence.getLast();
                var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(),
                        source.locator(), source.id(), source.text(), Map.of("range", "10 feet"), List.of());
                return List.of(new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", definition, null));
            }
            @Override public long estimateAssignedBatchInputTokens(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return 1; }
        };

        var result = new RulebookActionDefinitionExtractionService(new MemoryRepository(), search, exact, extractor, null, 128_000, 24_000)
                .extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals(List.of(5), batchSizes);
        assertEquals(List.of(List.of(CANDIDATE.locator())), exactLocators);
        assertEquals(List.of(64), exactLimits);
        assertEquals("10 feet", result.definitions().getFirst().fields().get("range"));
    }

    @Test
    void rejectsExactLookupResultsOutsideTheRequestedDocumentOrPage(CapturedOutput output) {
        var repository = new MemoryRepository();
        var action = new RulebookActionCandidate("action-id", "Named Action", "SPELL", CANDIDATE.locator(),
                EVIDENCE_ID.toString(), CANDIDATE.excerpt());
        EvidenceCandidate wrongPage = evidenceCandidate("wrong-location", 25);
        var foreignLocation = new EvidenceCandidate(wrongPage.documentId(), wrongPage.chunkId(), wrongPage.extractionVersion(),
                wrongPage.documentType(), "page=99:chunk=wrong", wrongPage.excerpt(),
                new SourceProvenance(99, List.of(), List.of(), null, "page=99:chunk=wrong"),
                wrongPage.denseRank(), wrongPage.denseSimilarity());
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version,
                    List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> coverage, UUID actor) {
                throw new AssertionError("staged extraction expected");
            }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version,
                    List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(action);
            }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                Map<String, Object> fields = new java.util.LinkedHashMap<>(); fields.put("cost", null);
                var definition = new RulebookActionDefinition(action.id(), action.name(), action.actionKind(),
                        action.sourceLocator(), EVIDENCE_ID.toString(), CANDIDATE.excerpt(), fields, List.of());
                return List.of(new RulebookActionDefinitionResult(action.id(), "COMPLETED", definition, null, List.of("cost")));
            }
        };
        var denseRequests = new ArrayList<EvidenceSearchRequest>();
        var service = new RulebookActionDefinitionExtractionService(repository, request -> {
            denseRequests.add(request); return List.of(CANDIDATE);
        }, exactLookup(foreignLocation), extractor, null, 128_000, 24_000);

        String previousDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        try {
            assertThrows(IllegalStateException.class, () -> service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID));
        } finally { restoreDiagnostics(previousDiagnostics); }
        assertEquals(5, denseRequests.size());
        assertEquals("FAILED", repository.find(DOCUMENT_ID, VERSION).orElseThrow().status());
        assertTrue(output.toString().contains("stage=targeted_source_scope_validation actionId=action-id expectedDocumentId=" + DOCUMENT_ID
                + " expectedScopeVersion=7 expectedDocumentType=RULEBOOK expectedPageNumber=1 lookupAnchorLocatorFingerprint="));
    }

    @Test
    void rejectsUnexpectedTargetedFollowupIdAndLogsExpectedAndActualIds(CapturedOutput output) {
        var candidate = new RulebookActionCandidate("magic-id", "Magic Missile", "SPELL", CANDIDATE.locator(), EVIDENCE_ID.toString(), CANDIDATE.excerpt());
        EvidenceCandidate supplement = evidenceCandidate("magic-cost", 20);
        var service = new RulebookActionDefinitionExtractionService(new MemoryRepository(), request -> List.of(CANDIDATE),
                exactLookup(withLocator(supplement, CANDIDATE.locator())), new RulebookActionDefinitionExtractionPort() {
                    @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                            List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
                    @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                            List<RulebookActionQueryCoverage> coverage, UUID actor) { return List.of(candidate); }
                    @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                            List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                            List<RulebookActionQueryCoverage> coverage, UUID actor) {
                        if (assigned.getFirst().neededFields().isEmpty()) {
                            Map<String, Object> fields = new java.util.LinkedHashMap<>(); fields.put("range", "120 feet"); fields.put("cost", null);
                            var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(),
                                    CANDIDATE.locator(), EVIDENCE_ID.toString(), CANDIDATE.excerpt(), fields, List.of());
                            return List.of(new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", definition, null, List.of("cost")));
                        }
                        return List.of(new RulebookActionDefinitionResult("unexpected-id", "INSUFFICIENT_EVIDENCE", null, "no_definition"));
                    }
                }, null, 128_000, 24_000);
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        try {
            var result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);
            assertEquals("COMPLETED", result.status());
            assertEquals("magic-id", result.definitions().getFirst().id());
            assertEquals(null, result.definitions().getFirst().fields().get("cost"));
        } finally { restoreDiagnostics(previous); }
        assertTrue(output.toString().contains("stage=targeted_batch_id_validation expectedCandidateIds=[magic-id] actualCandidateIds=[unexpected-id]"));
    }

    @Test
    void packsWholeActionsByEstimatedTokensAndKeepsLongEvidenceComplete(CapturedOutput output) {
        EvidenceCandidate source = evidenceCandidate("split-actions", 30);
        List<String> names = List.of("Alpha", "Beta", "Gamma");
        List<RulebookActionCandidate> candidates = names.stream().map(name -> new RulebookActionCandidate(name.toLowerCase() + "-id",
                name, "SPELL", source.locator(), source.chunkId().value().toString(), "x")).toList();
        Map<String, EvidenceCandidate> details = new java.util.LinkedHashMap<>();
        names.forEach(name -> details.put(name.toLowerCase() + "-id", withLocator(evidenceCandidate(name + "-detail", 25_000), source.locator())));
        AtomicInteger followupCalls = new AtomicInteger();
        var receivedEvidenceLengths = new ArrayList<Integer>();
        var service = new RulebookActionDefinitionExtractionService(new MemoryRepository(), request -> List.of(source),
                exactLookup(details.values().toArray(EvidenceCandidate[]::new)), new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return candidates; }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                if (assigned.getFirst().neededFields().isEmpty()) return assigned.stream().map(item -> {
                    var candidate = item.candidate();
                    Map<String, Object> fields = new java.util.LinkedHashMap<>(); fields.put("cost", null);
                    var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), "SPELL", source.locator(),
                            source.chunkId().value().toString(), "x", fields, List.of());
                    return new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", definition, null, List.of("cost"));
                }).toList();
                followupCalls.incrementAndGet();
                receivedEvidenceLengths.addAll(evidence.stream().map(item -> item.text().length()).toList());
                return assigned.stream().map(item -> {
                    EvidenceCandidate detail = details.get(item.candidate().id());
                    RulebookActionEvidence cited = evidence.stream().filter(value -> value.id().equals(detail.chunkId().value().toString()))
                            .findFirst().orElseThrow();
                    var definition = new RulebookActionDefinition(item.candidate().id(), item.candidate().name(), "SPELL",
                            cited.locator(), cited.id(), cited.text(), Map.of("cost", "one action"), List.of());
                    return new RulebookActionDefinitionResult(item.candidate().id(), "COMPLETED", definition, null);
                }).toList();
            }
            @Override public long estimateAssignedBatchInputTokens(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return 79_691;
            }
        }, null, 128_000, 24_000);
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        RulebookActionDefinitionExtraction result;
        try { result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID); }
        finally { restoreDiagnostics(previous); }

        assertEquals(1, followupCalls.get());
        assertEquals(3, result.definitions().size());
        assertEquals(3, receivedEvidenceLengths.stream().filter(length -> length >= 25_000).count());
        assertTrue(output.toString().contains("estimatedInputTokens=79691 totalInputTokenLimit=128000 externalTokenReserve=24000 usableInputTokenLimit=104000"));
    }

    @Test
    void failsOneOversizedTargetedActionWithoutSendingPartialEvidence(CapturedOutput output) {
        EvidenceCandidate supplement = evidenceCandidate("oversized-detail", 45_000);
        var repository = new MemoryRepository();
        var previousDefinition = new RulebookActionDefinition("large-id", "Large Action", "SPELL", CANDIDATE.locator(),
                EVIDENCE_ID.toString(), CANDIDATE.excerpt(), Map.of("range", "10 feet"), List.of());
        repository.save(new RulebookActionDefinitionExtraction(DOCUMENT_ID, VERSION, "COMPLETED", 1, 0, 0, 0,
                List.of(previousDefinition), null, "rulebook-action-definition-v7-focused-rules"));
        AtomicInteger assignedCalls = new AtomicInteger();
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version,
                    List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> coverage, UUID actor) {
                throw new AssertionError("staged extraction expected");
            }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version,
                    List<RulebookActionEvidence> evidence, List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(new RulebookActionCandidate("large-id", "Large Action", "SPELL", CANDIDATE.locator(),
                        EVIDENCE_ID.toString(), CANDIDATE.excerpt()));
            }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                assignedCalls.incrementAndGet();
                if (assigned.getFirst().neededFields().isEmpty()) {
                    Map<String, Object> fields = new java.util.LinkedHashMap<>(); fields.put("cost", null);
                    var definition = new RulebookActionDefinition("large-id", "Large Action", "SPELL", CANDIDATE.locator(),
                            EVIDENCE_ID.toString(), CANDIDATE.excerpt(), fields, List.of());
                    return List.of(new RulebookActionDefinitionResult("large-id", "COMPLETED", definition, null, List.of("cost")));
                }
                throw new AssertionError("oversized targeted evidence must not reach the agent");
            }
            @Override public long estimateAssignedBatchInputTokens(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return evidence.stream().mapToLong(item -> item.text().length()).sum();
            }
        };
        var service = new RulebookActionDefinitionExtractionService(repository, request -> List.of(CANDIDATE),
                exactLookup(withLocator(supplement, CANDIDATE.locator())), extractor, 0.45, 64_000, 24_000);
        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        try {
            assertThrows(IllegalArgumentException.class, () -> service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID));
        } finally { restoreDiagnostics(previous); }

        assertEquals(1, assignedCalls.get());
        var failedRun = repository.find(DOCUMENT_ID, VERSION).orElseThrow();
        assertEquals("FAILED", failedRun.status());
        assertEquals("rulebook-action-definition-v7-focused-rules", failedRun.contractVersion());
        assertEquals(List.of("large-id"), failedRun.definitions().stream().map(RulebookActionDefinition::id).toList());
        assertTrue(output.toString().contains("stage=targeted_action_over_budget documentId=" + DOCUMENT_ID
                + " actionId=large-id estimatedInputTokens=45033 totalInputTokenLimit=64000 externalTokenReserve=24000 usableInputTokenLimit=40000"));
        assertTrue(output.toString().contains("stage=targeted_budget_failure_preserved_prior documentId=" + DOCUMENT_ID
                + " extractionVersion=" + VERSION + " preservedContractVersion=rulebook-action-definition-v7-focused-rules preservedDefinitionCount=1"));
    }

    @Test
    void appliesStoredSharedRuleBeforeDenseSearchWhenItResolvesTheNeededField() {
        var repository = new MemoryRepository();
        var shared = new RulebookActionSharedRule("shared:known", "AMBIGUOUS", "When using a listed spell", "other-source",
                "page=2:section=General rules", "A listed spell costs one action to activate.", Map.of("cost", "one action"));
        repository.save(new RulebookActionDefinitionExtraction(DOCUMENT_ID, VERSION, "FAILED", 1, 0, 0, 0,
                List.of(), "retry", "rulebook-action-definition-v8-complete-targeted-evidence", List.of(), false, 0, List.of(), List.of(shared)));
        var discovery = new RulebookActionCandidate("item-id", "Magic Missile", "SPELL", "page=1:section=Spells",
                EVIDENCE_ID.toString(), CANDIDATE.excerpt());
        var denseRequests = new ArrayList<EvidenceSearchRequest>();
        var port = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return List.of(discovery); }
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged extraction expected"); }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                var action = assigned.getFirst();
                Map<String, Object> fields = new java.util.LinkedHashMap<>(); fields.put("cost", action.availableSharedRules().isEmpty() ? null : "one action");
                var definition = new RulebookActionDefinition(action.candidate().id(), action.candidate().name(), action.candidate().actionKind(),
                        evidence.getFirst().locator(), evidence.getFirst().id(), evidence.getFirst().text(), fields, List.of(),
                        action.availableSharedRules().isEmpty() ? List.of() : List.of("shared:known"));
                return List.of(new RulebookActionDefinitionResult(action.candidate().id(), "COMPLETED", definition, null,
                        List.of("cost"), List.of(), definition.sharedRuleRefs()));
            }
        };
        var service = new RulebookActionDefinitionExtractionService(repository, request -> { denseRequests.add(request); return List.of(CANDIDATE); }, port);

        var result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals("one action", result.definitions().getFirst().fields().get("cost"));
        assertEquals(List.of("shared:known"), result.definitions().getFirst().sharedRuleRefs());
        assertTrue(result.sharedRules().stream().anyMatch(rule -> rule.id().equals("shared:known")));
        assertEquals(5, denseRequests.size());
        assertFalse(denseRequests.stream().anyMatch(request -> request.stageKey().equals("rulebook-action-needed-fields")));
    }

    @Test
    void ambiguousStoredSharedRuleFallsBackToExactSourceLookupWithoutInheritingItsValue() {
        var repository = new MemoryRepository();
        var shared = new RulebookActionSharedRule("shared:uncertain", "AMBIGUOUS", "When using a listed spell", "other-source",
                "page=2:section=General rules", "A listed spell costs one action to activate.", Map.of("cost", "one action"));
        repository.save(new RulebookActionDefinitionExtraction(DOCUMENT_ID, VERSION, "FAILED", 1, 0, 0, 0,
                List.of(), "retry", "rulebook-action-definition-v8-complete-targeted-evidence", List.of(), false, 0, List.of(), List.of(shared)));
        var discovery = new RulebookActionCandidate("item-id", "Magic Missile", "SPELL", "page=1:section=Spells",
                EVIDENCE_ID.toString(), CANDIDATE.excerpt());
        UUID supplementId = UUID.fromString("f5c2a8ed-125f-46fc-9df2-e0c49916af91");
        EvidenceCandidate supplement = new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(supplementId), 7,
                DocumentType.RULEBOOK, discovery.sourceLocator(), "Magic Missile costs one action.",
                new SourceProvenance(1, List.of("Spells"), List.of(), null, discovery.sourceLocator()), 1,null);
        var denseRequests = new ArrayList<EvidenceSearchRequest>();
        var port = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return List.of(discovery); }
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged extraction expected"); }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                var action = assigned.getFirst();
                boolean foundSpecificEvidence = evidence.size() > 1;
                boolean tryingStoredRule = !action.availableSharedRules().isEmpty() && !foundSpecificEvidence;
                Map<String, Object> fields = new java.util.LinkedHashMap<>();
                fields.put("cost", foundSpecificEvidence || tryingStoredRule ? "one action" : null);
                var cited = evidence.getLast();
                var definition = new RulebookActionDefinition(action.candidate().id(), action.candidate().name(), action.candidate().actionKind(),
                        cited.locator(), cited.id(), cited.text(), fields, List.of(), List.of());
                return List.of(new RulebookActionDefinitionResult(action.candidate().id(), "COMPLETED", definition, null,
                        List.of("cost"), List.of(), List.of()));
            }
        };
        var service = new RulebookActionDefinitionExtractionService(repository, request -> {
            denseRequests.add(request); return List.of(CANDIDATE);
        }, exactLookup(withLocator(supplement, discovery.sourceLocator())), port, null, 128_000, 24_000);

        var result = service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals("one action", result.definitions().getFirst().fields().get("cost"));
        assertTrue(result.definitions().getFirst().sharedRuleRefs().isEmpty());
        assertEquals(5, denseRequests.size());
        assertTrue(denseRequests.stream().allMatch(request -> request.stageKey().equals("rulebook-action-extraction")));
    }

    @Test
    void discoveredActionsWithNoCompletedDefinitionsDoNotReportSuccessfulExtraction() {
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(new RulebookActionCandidate("magic-id", "Magic Missile", "SPELL", CANDIDATE.locator(), EVIDENCE_ID.toString(), "Magic Missile"));
            }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                return List.of(new RulebookActionDefinitionResult("magic-id", "INSUFFICIENT_EVIDENCE", null, "missing_definition_fields"));
            }
        };

        var result = new RulebookActionDefinitionExtractionService(new MemoryRepository(), request -> List.of(CANDIDATE), extractor)
                .extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals("FAILED", result.status());
        assertEquals("no_definitions_extracted", result.failureCode());
        assertEquals(1, result.actionCoverage().size());
        assertEquals("INSUFFICIENT_EVIDENCE", result.actionCoverage().getFirst().status());
        assertEquals(0, result.definitions().size());
    }

    @Test
    void failedAssignedCheckpointIsRetriedAfterSchemaContractChange() {
        var repository = new MemoryRepository();
        var candidate = new RulebookActionCandidate("magic-id", "Magic Missile", "SPELL", CANDIDATE.locator(), EVIDENCE_ID.toString(), "Magic Missile");
        repository.saveBatch(new RulebookActionDefinitionBatch(DOCUMENT_ID, VERSION, 100_000, List.of(EVIDENCE_ID.toString()),
                "COMPLETED", List.of(), "assigned_actions_failed", "rulebook-action-definition-v6-grounded-discovery",
                List.of(candidate), List.of(new RulebookActionDefinitionResult("magic-id", "FAILED", null, "IllegalStateException"))));
        AtomicInteger assignedCalls = new AtomicInteger();
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return List.of(candidate); }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                assignedCalls.incrementAndGet();
                var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(), candidate.sourceLocator(),
                        EVIDENCE_ID.toString(), "Magic Missile", Map.of(), List.of());
                return List.of(new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", definition, null));
            }
        };

        var result = new RulebookActionDefinitionExtractionService(repository, request -> List.of(CANDIDATE), extractor)
                .extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);

        assertEquals(1, assignedCalls.get());
        assertEquals("COMPLETED", result.status());
        assertEquals(List.of("magic-id"), result.definitions().stream().map(RulebookActionDefinition::id).toList());
    }

    @Test
    void retriesCheckpointWhoseDefinitionCitesFollowupEvidenceMissingFromCurrentAssignment(CapturedOutput output) {
        var repository = new MemoryRepository();
        var candidate = new RulebookActionCandidate("magic-id", "Magic Missile", "SPELL", CANDIDATE.locator(),
                EVIDENCE_ID.toString(), "Magic Missile");
        UUID followupSourceId = UUID.nameUUIDFromBytes("followup-source".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var staleDefinition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(),
                "page=2:section=Magic Missile", followupSourceId.toString(), "Magic Missile range 120 feet.", Map.of("range", "120 feet"), List.of());
        repository.saveBatch(new RulebookActionDefinitionBatch(DOCUMENT_ID, VERSION, 100_000, List.of(EVIDENCE_ID.toString()),
                "COMPLETED", List.of(staleDefinition), null, "rulebook-action-definition-v8-complete-targeted-evidence",
                List.of(candidate), List.of(new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", staleDefinition, null))));
        AtomicInteger assignedCalls = new AtomicInteger();
        RulebookActionDefinitionExtractionPort extractor = new RulebookActionDefinitionExtractionPort() {
            @Override public List<RulebookActionDefinition> extract(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { throw new AssertionError("staged contract expected"); }
            @Override public List<RulebookActionCandidate> discover(UUID document, String version, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) { return List.of(candidate); }
            @Override public List<RulebookActionDefinitionResult> extractAssignedBatch(UUID document, String version,
                    List<RulebookActionAssignment> assigned, List<RulebookActionEvidence> evidence,
                    List<RulebookActionQueryCoverage> coverage, UUID actor) {
                assignedCalls.incrementAndGet();
                var definition = new RulebookActionDefinition(candidate.id(), candidate.name(), candidate.actionKind(),
                        CANDIDATE.locator(), EVIDENCE_ID.toString(), "Magic Missile has range 120 feet.", Map.of("range", "120 feet"), List.of());
                return List.of(new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", definition, null));
            }
        };

        String previousDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        RulebookActionDefinitionExtraction result;
        try {
            result = new RulebookActionDefinitionExtractionService(repository, request -> List.of(CANDIDATE), extractor)
                    .extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID);
        } finally { restoreDiagnostics(previousDiagnostics); }

        assertEquals(1, assignedCalls.get());
        assertEquals("COMPLETED", result.status());
        assertEquals(EVIDENCE_ID.toString(), result.definitions().getFirst().sourceChunkId());
        assertTrue(output.toString().contains("stage=assigned_checkpoint_source_validation documentId=" + DOCUMENT_ID
                + " extractionVersion=" + VERSION + " batchIndex=100000 expectedSourceIds=[" + EVIDENCE_ID
                + "] actualSourceIds=[" + followupSourceId + "] invalidDefinitionIds=[magic-id] recoveryAction=retry_assigned_batch outcome=failed"));
    }

    private static void restoreDiagnostics(String previous) {
        if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
    }

    private static EvidenceCandidate evidenceCandidate(String seed, int chars) {
        UUID id = UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new EvidenceCandidate(new KnowledgeDocumentId(DOCUMENT_ID), new ChunkId(id), 7, DocumentType.RULEBOOK,
                "page=2:section=" + seed, "x".repeat(chars), CANDIDATE.provenance(), 1,null);
    }

    private static EvidenceLocationLookupPort exactLookup(EvidenceCandidate... candidates) {
        List<EvidenceCandidate> indexed = List.of(candidates);
        return (owner, scope, locators, limit) -> locators.stream().collect(java.util.stream.Collectors.toMap(
                locator -> locator, locator -> indexed.stream()
                        .filter(candidate -> scope.stream().anyMatch(item -> item.documentId().equals(candidate.documentId())
                                && item.extractionVersion() == candidate.extractionVersion()
                                && item.documentType() == candidate.documentType()))
                        .limit(limit).toList(), (first, ignored) -> first, java.util.LinkedHashMap::new));
    }

    private static EvidenceCandidate withLocator(EvidenceCandidate candidate, String locator) {
        return new EvidenceCandidate(candidate.documentId(), candidate.chunkId(), candidate.extractionVersion(),
                candidate.documentType(), locator, candidate.excerpt(), candidate.provenance(), candidate.denseRank(),
                candidate.denseSimilarity());
    }

    @Test
    void rejectsEvidenceOutsideTheSelectedDocumentAndVersion() {
        var repository = new MemoryRepository();
        var other = new EvidenceCandidate(new KnowledgeDocumentId(UUID.randomUUID()), new ChunkId(EVIDENCE_ID), 6,
                DocumentType.RULEBOOK, "page=1", "text", CANDIDATE.provenance(), 1,null);
        var service = new RulebookActionDefinitionExtractionService(repository, request -> List.of(other),
                (document, version, evidence, coverage, actor) -> List.of());

        assertThrows(com.dndmaster.ruleknowledge.application.search.EvidenceSearchUnavailableException.class,
                () -> service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID));
        assertEquals("FAILED", repository.find(DOCUMENT_ID, VERSION).orElseThrow().status());
    }

    @Test
    void rejectsDefinitionQuotesThatDoNotExistInTheRetrievedSource() {
        var repository = new MemoryRepository();
        var service = new RulebookActionDefinitionExtractionService(repository, request -> List.of(CANDIDATE),
                (document, version, evidence, coverage, actor) -> List.of(new RulebookActionDefinition("bad", "Spell", "SPELL",
                        evidence.getFirst().locator(), evidence.getFirst().id(), "invented wording", Map.of(), List.of())));

        assertThrows(IllegalArgumentException.class, () -> service.extract(DOCUMENT_ID, VERSION, 7, PAGES, ACTOR_ID));
        assertEquals("FAILED", repository.find(DOCUMENT_ID, VERSION).orElseThrow().status());
    }

    private static final class MemoryRepository implements RulebookActionDefinitionRepository {
        private final Map<String, RulebookActionDefinitionExtraction> extractions = new HashMap<>();
        private final Map<String, RulebookActionDefinitionBatch> batches = new HashMap<>();
        private String key(UUID document, String version) { return document + ":" + version; }
        @Override public Optional<RulebookActionDefinitionExtraction> find(UUID document, String version) { return Optional.ofNullable(extractions.get(key(document, version))); }
        @Override public void save(RulebookActionDefinitionExtraction extraction) { extractions.put(key(extraction.documentId(), extraction.extractionVersion()), extraction); }
        @Override public boolean claim(UUID document, String version, int pages, int indexed, int chunks, int sections, String contractVersion) { return true; }
        @Override public Optional<RulebookActionDefinitionBatch> findBatch(UUID document, String version, int index) { return Optional.ofNullable(batches.get(key(document, version) + ":" + index)); }
        @Override public void saveBatch(RulebookActionDefinitionBatch batch) { batches.put(key(batch.documentId(), batch.extractionVersion()) + ":" + batch.batchIndex(), batch); }
    }
}
