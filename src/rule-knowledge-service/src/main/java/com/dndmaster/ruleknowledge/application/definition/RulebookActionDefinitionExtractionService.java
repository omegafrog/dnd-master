package com.dndmaster.ruleknowledge.application.definition;

import com.dndmaster.ruleknowledge.application.search.AuthorizedDocumentScope;
import com.dndmaster.ruleknowledge.application.search.DenseEvidenceCandidateSearchPort;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Collects a bounded set of published dense-RAG evidence for the structured extraction agent. */
public final class RulebookActionDefinitionExtractionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RulebookActionDefinitionExtractionService.class);
    private static final UUID CATALOG_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final int CANDIDATES_PER_QUERY = 8;
    private static final int TARGETED_CANDIDATES_PER_QUERY = 64;
    private static final int MAX_AGENT_EVIDENCE = 48;
    private static final int MAX_DISCOVERY_QUERY_CANDIDATES = 30;
    private static final int DISCOVERY_QUERY_COUNT = 5;
    private static final int MAX_DISCOVERY_EVIDENCE_CHARS = 24_000;
    private static final int MAX_INITIAL_ASSIGNED_EVIDENCE_CHARS = 48_000;
    private static final int TARGETED_SEARCH_BATCH_SIZE = 16;
    private static final String CONTRACT_VERSION = "rulebook-action-definition-v8-complete-targeted-evidence";
    private static final List<Query> QUERIES = List.of(
            new Query("contents", "목차 주문 목록 주문 이름 장 제목 주문 장 구성", MAX_DISCOVERY_QUERY_CANDIDATES),
            new Query("weapon-table", "무기 표 원거리 무기 투척 무기 속성 사거리 피해", CANDIDATES_PER_QUERY),
            new Query("spell-list", "클래스별 주문 목록 가나다순 주문 이름 주문 세부 항목", MAX_DISCOVERY_QUERY_CANDIDATES),
            new Query("ranged-actions", "ranged weapon attacks and ranged attack rules", CANDIDATES_PER_QUERY),
            new Query("thrown-actions", "thrown weapons and thrown attack rules", CANDIDATES_PER_QUERY));

    private final RulebookActionDefinitionRepository repository;
    private final DenseEvidenceCandidateSearchPort denseSearch;
    private final RulebookActionDefinitionExtractionPort extractor;
    private final Double minimumDenseSimilarity;
    private final long targetedInputTokenLimit;
    private final long targetedExternalTokenReserve;

    public RulebookActionDefinitionExtractionService(RulebookActionDefinitionRepository repository,
            DenseEvidenceCandidateSearchPort denseSearch, RulebookActionDefinitionExtractionPort extractor) {
        this(repository, denseSearch, extractor, null, 128_000, 24_000);
    }

    public RulebookActionDefinitionExtractionService(RulebookActionDefinitionRepository repository,
            DenseEvidenceCandidateSearchPort denseSearch, RulebookActionDefinitionExtractionPort extractor,
            Double minimumDenseSimilarity) {
        this(repository, denseSearch, extractor, minimumDenseSimilarity, 128_000, 24_000);
    }

    public RulebookActionDefinitionExtractionService(RulebookActionDefinitionRepository repository,
            DenseEvidenceCandidateSearchPort denseSearch, RulebookActionDefinitionExtractionPort extractor,
            Double minimumDenseSimilarity, long targetedInputTokenLimit) {
        this(repository, denseSearch, extractor, minimumDenseSimilarity, targetedInputTokenLimit, 24_000);
    }

    public RulebookActionDefinitionExtractionService(RulebookActionDefinitionRepository repository,
            DenseEvidenceCandidateSearchPort denseSearch, RulebookActionDefinitionExtractionPort extractor,
            Double minimumDenseSimilarity, long targetedInputTokenLimit, long targetedExternalTokenReserve) {
        this.repository = Objects.requireNonNull(repository);
        this.denseSearch = Objects.requireNonNull(denseSearch);
        this.extractor = Objects.requireNonNull(extractor);
        if (minimumDenseSimilarity != null && (!Double.isFinite(minimumDenseSimilarity)
                || minimumDenseSimilarity < -1 || minimumDenseSimilarity > 1)) {
            throw new IllegalArgumentException("minimum dense similarity must be finite and between -1 and 1");
        }
        this.minimumDenseSimilarity = minimumDenseSimilarity;
        if (targetedInputTokenLimit <= 0 || targetedExternalTokenReserve < 0
                || targetedExternalTokenReserve >= targetedInputTokenLimit)
            throw new IllegalArgumentException("targeted input token budget and reserve are invalid");
        this.targetedInputTokenLimit = targetedInputTokenLimit;
        this.targetedExternalTokenReserve = targetedExternalTokenReserve;
    }

    public RulebookActionDefinitionExtraction extract(UUID documentId, String extractionVersion, long scopeVersion,
            List<com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState> pages,
            UUID executionActorPlayerId) {
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(pages);
        if (extractionVersion == null || extractionVersion.isBlank() || scopeVersion <= 0 || executionActorPlayerId == null)
            throw new IllegalArgumentException("published source identity and extraction actor are required");
        RulebookActionDefinitionExtraction existing = repository.find(documentId, extractionVersion).orElse(null);
        if (existing != null && "COMPLETED".equals(existing.status()) && CONTRACT_VERSION.equals(existing.contractVersion())) return existing;
        List<RulebookActionSharedRule> priorSharedRules = existing != null && CONTRACT_VERSION.equals(existing.contractVersion())
                ? existing.sharedRules() : List.of();
        long started = System.nanoTime();
        log("dev_rulebook_action_extraction stage=started documentId={} extractionVersion={} scopeVersion={} queryCount={} candidateLimitPerQuery={} maxEvidence={} outcome=started",
                documentId, extractionVersion, scopeVersion, QUERIES.size(), MAX_DISCOVERY_QUERY_CANDIDATES, MAX_AGENT_EVIDENCE);
        if (!repository.claim(documentId, extractionVersion, pages.size(), pages.size(), 0, 0, CONTRACT_VERSION)) {
            log("dev_rulebook_action_extraction stage=claim_failed documentId={} extractionVersion={} expectedClaim=true actualClaim=false recoveryAction=retry_after_inflight_completion outcome=failed",
                    documentId, extractionVersion);
            throw new IllegalStateException("action definition extraction is already running or completed");
        }
        List<RulebookActionQueryCoverage> coverage = new ArrayList<>();
        boolean capped = false;
        int collectedEvidenceCount = 0;
        try {
            List<EvidenceSearchRequest> requests = QUERIES.stream().map(query -> new EvidenceSearchRequest(
                    new OwnerPlayerId(CATALOG_OWNER), null, null, "rulebook-action-extraction", query.id(),
                    List.of(new AuthorizedDocumentScope(new KnowledgeDocumentId(documentId), scopeVersion,
                            DocumentType.RULEBOOK, new OwnerPlayerId(CATALOG_OWNER))), List.of(), query.text(),
                    query.candidateLimit(), 0)).toList();
            List<List<EvidenceCandidate>> results = denseSearch.searchBatch(requests);
            if (results == null || results.size() != requests.size()) {
                log("dev_rulebook_action_extraction stage=query_result_validation expectedQueryCount={} actualQueryCount={} failureKind=query_count_mismatch recoveryAction=retry_dense_queries outcome=failed",
                        requests.size(), results == null ? -1 : results.size());
                throw new IllegalStateException("dense RAG query result count mismatch");
            }
            List<List<EvidenceCandidate>> candidatesByQuery = new ArrayList<>();
            for (int index = 0; index < results.size(); index++) {
                Query query = QUERIES.get(index);
                List<EvidenceCandidate> candidates = results.get(index);
                if (candidates == null || candidates.size() > query.candidateLimit()) {
                    log("dev_rulebook_action_extraction stage=query_result_validation queryId={} expectedCandidateCount=0..{} actualCandidateCount={} failureKind=candidate_count_out_of_bounds recoveryAction=retry_dense_query outcome=failed",
                            query.id(), query.candidateLimit(), candidates == null ? -1 : candidates.size());
                    throw new IllegalStateException("dense RAG query returned an invalid result count");
                }
                coverage.add(new RulebookActionQueryCoverage(query.id(), fingerprint(query.text()), candidates.size()));
                if (candidates.size() == query.candidateLimit()) {
                    capped = true;
                    log("dev_rulebook_action_extraction stage=query_limit_reached queryId={} requestedLimit={} actualCandidateCount={} recoveryAction=retain_all_returned_candidates outcome=partial",
                            query.id(), query.candidateLimit(), candidates.size());
                }
                for (EvidenceCandidate candidate : candidates) {
                    if (!candidate.documentId().value().equals(documentId) || candidate.extractionVersion() != scopeVersion) {
                        log("dev_rulebook_action_extraction stage=source_scope_validation expectedDocumentId={} expectedScopeVersion={} actualDocumentId={} actualScopeVersion={} queryId={} failureKind=candidate_outside_published_scope recoveryAction=reject_search_result outcome=failed",
                                documentId, scopeVersion, candidate.documentId().value(), candidate.extractionVersion(), query.id());
                        throw new IllegalStateException("dense RAG returned evidence outside the published source scope");
                    }
                }
                candidatesByQuery.add(candidates);
            }
            Map<String, EvidenceCandidate> unique = new LinkedHashMap<>();
            for (int rank = 0; rank < MAX_DISCOVERY_QUERY_CANDIDATES; rank++) {
                for (List<EvidenceCandidate> queryCandidates : candidatesByQuery) {
                    if (rank >= queryCandidates.size()) continue;
                    EvidenceCandidate candidate = queryCandidates.get(rank);
                    String candidateId = candidate.chunkId().value().toString();
                    if (unique.containsKey(candidateId)) continue;
                    unique.put(candidateId, candidate);
                }
            }
            collectedEvidenceCount = unique.size();
            List<RulebookActionEvidence> evidence = unique.values().stream().map(candidate -> new RulebookActionEvidence(
                    candidate.chunkId().value().toString(), candidate.locator(), candidate.excerpt())).toList();
            Set<String> discoveryIds = candidatesByQuery.subList(0, DISCOVERY_QUERY_COUNT).stream().flatMap(List::stream)
                    .map(candidate -> candidate.chunkId().value().toString()).collect(java.util.stream.Collectors.toSet());
            List<RulebookActionEvidence> discoveryEvidence = evidence.stream().filter(item -> discoveryIds.contains(item.id())).toList();
            List<RulebookActionQueryCoverage> discoveryCoverage = coverage.subList(0, DISCOVERY_QUERY_COUNT);
            log("dev_rulebook_action_extraction stage=evidence_collected documentId={} extractionVersion={} scopeVersion={} queryCount={} completedQueryCount={} coverage={} evidenceCount={} evidenceIds={} capped={} outcome=completed",
                    documentId, extractionVersion, scopeVersion, QUERIES.size(), coverage.size(), coverage, evidence.size(),
                    evidence.stream().map(RulebookActionEvidence::id).toList(), capped);
            List<RulebookActionCandidate> discoveries = new ArrayList<>();
            List<List<RulebookActionEvidence>> batches = evidenceBatches(discoveryEvidence);
            capped = capped || evidence.size() > MAX_AGENT_EVIDENCE || batches.size() > 1;
            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                List<RulebookActionEvidence> batchEvidence = batches.get(batchIndex);
                RulebookActionDefinitionBatch checkpoint = repository.findBatch(documentId, extractionVersion, batchIndex).orElse(null);
                if (checkpoint != null && "COMPLETED".equals(checkpoint.status())
                        && CONTRACT_VERSION.equals(checkpoint.contractVersion())
                        && checkpoint.sourceChunkIds().equals(batchEvidence.stream().map(RulebookActionEvidence::id).toList())) {
                    discoveries.addAll(checkpoint.candidates());
                    log("dev_rulebook_action_discovery stage=batch_reused documentId={} extractionVersion={} batchIndex={} expectedEvidenceCount={} actualCandidateCount={} recoveryAction=use_checkpoint outcome=completed",
                            documentId, extractionVersion, batchIndex, batchEvidence.size(), checkpoint.candidates().size());
                    continue;
                }
                long batchStarted = System.nanoTime();
                log("dev_rulebook_action_extraction stage=batch_started documentId={} extractionVersion={} batchIndex={} evidenceCount={} inputChars={} outcome=started",
                        documentId, extractionVersion, batchIndex, batchEvidence.size(), batchEvidence.stream().mapToInt(item -> item.text().length()).sum());
                try {
                    List<RulebookActionCandidate> batchCandidates = extractor.discover(documentId, extractionVersion,
                            batchEvidence, discoveryCoverage, executionActorPlayerId);
                    validateCandidates(batchEvidence, batchCandidates);
                    discoveries.addAll(batchCandidates);
                    repository.saveBatch(new RulebookActionDefinitionBatch(documentId, extractionVersion, batchIndex,
                            batchEvidence.stream().map(RulebookActionEvidence::id).toList(), "COMPLETED", List.of(), null, CONTRACT_VERSION, batchCandidates));
                    log("dev_rulebook_action_discovery stage=batch_completed documentId={} extractionVersion={} batchIndex={} expectedEvidenceCount={} actualCandidateCount={} elapsedMs={} outcome=completed",
                            documentId, extractionVersion, batchIndex, batchEvidence.size(), batchCandidates.size(), (System.nanoTime() - batchStarted) / 1_000_000);
                } catch (RuntimeException failure) {
                    repository.saveBatch(new RulebookActionDefinitionBatch(documentId, extractionVersion, batchIndex,
                            batchEvidence.stream().map(RulebookActionEvidence::id).toList(), "FAILED", List.of(), failure.getClass().getSimpleName(), CONTRACT_VERSION));
                    log("dev_rulebook_action_extraction stage=batch_failed documentId={} extractionVersion={} batchIndex={} evidenceCount={} failureClass={} elapsedMs={} recoveryAction=retry_failed_batch outcome=failed",
                            documentId, extractionVersion, batchIndex, batchEvidence.size(), failure.getClass().getSimpleName(), (System.nanoTime() - batchStarted) / 1_000_000);
                    throw failure;
                }
            }
            Map<String, RulebookActionEvidence> evidenceById = evidence.stream().collect(java.util.stream.Collectors.toMap(
                    RulebookActionEvidence::id, item -> item, (first, ignored) -> first, LinkedHashMap::new));
            Map<String, RulebookActionCandidate> actions = new LinkedHashMap<>();
            for (RulebookActionCandidate item : discoveries) {
                String key = item.name().strip().toLowerCase(Locale.ROOT) + ":" + item.actionKind() + ":" + item.sourceLocator();
                actions.putIfAbsent(key, item);
            }
            List<RulebookActionDefinition> definitions = new ArrayList<>();
            Map<String, RulebookActionSharedRule> sharedRules = new LinkedHashMap<>();
            priorSharedRules.forEach(rule -> sharedRules.put(rule.id(), rule));
            List<RulebookActionDefinitionCoverage> actionCoverage = new ArrayList<>();
            List<RulebookActionAssignment> ready = new ArrayList<>();
            for (RulebookActionCandidate candidate : actions.values()) {
                Map<String, RulebookActionEvidence> assignedEvidence = new LinkedHashMap<>();
                RulebookActionEvidence discoveredSource = evidenceById.get(candidate.sourceChunkId());
                if (discoveredSource != null) assignedEvidence.putIfAbsent(discoveredSource.id(), discoveredSource);
                if (assignedEvidence.isEmpty()) {
                    actionCoverage.add(new RulebookActionDefinitionCoverage(candidate.id(), candidate.name(), candidate.sourceLocator(),
                            "INSUFFICIENT_EVIDENCE", "discovery_source_missing"));
                    continue;
                }
                ready.add(new RulebookActionAssignment(candidate, List.copyOf(assignedEvidence.keySet()),
                        fingerprint(candidate.name() + ":" + candidate.sourceLocator()), 0));
                assignedEvidence.values().forEach(item -> evidenceById.putIfAbsent(item.id(), item));
            }
            List<RulebookActionAssignment> withinLimit = new ArrayList<>();
            for (RulebookActionAssignment assignment : ready) {
                AssignmentEvidenceMetrics metrics = assignmentEvidenceMetrics(assignment, evidenceById, Set.of());
                if (metrics.evidenceCount() > MAX_AGENT_EVIDENCE || metrics.inputChars() > MAX_INITIAL_ASSIGNED_EVIDENCE_CHARS) {
                    RulebookActionCandidate candidate = assignment.candidate();
                    actionCoverage.add(new RulebookActionDefinitionCoverage(candidate.id(), candidate.name(), candidate.sourceLocator(),
                            "FAILED", "typed_extraction_input_limit_exceeded"));
                    log("dev_rulebook_action_extraction stage=assigned_candidate_rejected documentId={} extractionVersion={} assignmentId={} expectedEvidenceCountMax={} actualEvidenceCount={} expectedInputCharsMax={} actualInputChars={} sharedEvidenceCount={} sharedInputChars={} candidateEvidenceCount={} candidateInputChars={} recoveryAction=continue_remaining_assignments outcome=failed",
                            documentId, extractionVersion, candidate.id(), MAX_AGENT_EVIDENCE, metrics.evidenceCount(), MAX_INITIAL_ASSIGNED_EVIDENCE_CHARS,
                            metrics.inputChars(), metrics.sharedEvidenceCount(), metrics.sharedInputChars(), metrics.candidateEvidenceCount(), metrics.candidateInputChars());
                } else {
                    withinLimit.add(assignment);
                }
            }
            List<List<RulebookActionAssignment>> assignmentBatches = assignmentBatches(withinLimit, evidenceById);
            for (int batchIndex = 0; batchIndex < assignmentBatches.size(); batchIndex++) {
                List<RulebookActionAssignment> assignments = assignmentBatches.get(batchIndex);
                List<RulebookActionEvidence> assignedEvidence = assignments.stream().flatMap(item -> item.evidenceIds().stream())
                        .distinct().map(evidenceById::get).toList();
                int checkpointIndex = 100_000 + batchIndex;
                List<String> sourceIds = assignedEvidence.stream().map(RulebookActionEvidence::id).toList();
                RulebookActionDefinitionBatch checkpoint = repository.findBatch(documentId, extractionVersion, checkpointIndex).orElse(null);
                List<String> assignedIds = assignments.stream().map(item -> item.candidate().id()).toList();
                String batchQueryFingerprint = fingerprint(assignedIds.toString());
                List<String> invalidCheckpointDefinitionIds = checkpoint == null ? List.of()
                        : invalidCheckpointDefinitionIds(checkpoint.results(), List.copyOf(evidenceById.values()));
                if (checkpoint != null && !invalidCheckpointDefinitionIds.isEmpty())
                    log("dev_rulebook_action_extraction stage=assigned_checkpoint_source_validation documentId={} extractionVersion={} batchIndex={} expectedSourceIds={} actualSourceIds={} invalidDefinitionIds={} recoveryAction=retry_assigned_batch outcome=failed",
                            documentId, extractionVersion, checkpointIndex, sourceIds,
                            checkpoint.results().stream().map(RulebookActionDefinitionResult::definition).filter(Objects::nonNull)
                                    .map(RulebookActionDefinition::sourceChunkId).distinct().toList(), invalidCheckpointDefinitionIds);
                    List<RulebookActionQueryCoverage> assignedCoverage = new ArrayList<>();
                    assignments.forEach(item -> assignedCoverage.add(new RulebookActionQueryCoverage(
                            "action:" + item.candidate().id(), item.queryFingerprint(), item.candidateCount())));
                List<RulebookActionDefinitionResult> assignedOutcomes;
                if (checkpoint != null && "COMPLETED".equals(checkpoint.status())
                        && CONTRACT_VERSION.equals(checkpoint.contractVersion()) && checkpoint.sourceChunkIds().equals(sourceIds)
                        && checkpoint.candidates().stream().map(RulebookActionCandidate::id).toList().equals(assignedIds)
                        && checkpoint.results().stream().map(RulebookActionDefinitionResult::candidateId).toList().equals(assignedIds)
                        && invalidCheckpointDefinitionIds.isEmpty()
                        && checkpoint.results().stream().noneMatch(item -> "FAILED".equals(item.status()))) {
                    assignedOutcomes = checkpoint.results();
                    log("dev_rulebook_action_extraction stage=assigned_batch_reused documentId={} extractionVersion={} batchIndex={} expectedActionCount={} actualResultCount={} recoveryAction=use_checkpoint outcome=completed",
                            documentId, extractionVersion, batchIndex, assignments.size(), assignedOutcomes.size());
                } else {
                    long batchStarted = System.nanoTime();
                    try {
                        assignedOutcomes = extractor.extractAssignedBatch(documentId, extractionVersion, assignments, assignedEvidence,
                                assignedCoverage, executionActorPlayerId);
                        Map<String, RulebookActionDefinitionResult> byId = assignedOutcomes.stream().collect(java.util.stream.Collectors.toMap(
                                RulebookActionDefinitionResult::candidateId, item -> item));
                        if (byId.size() != assignments.size() || !byId.keySet().equals(new java.util.HashSet<>(assignedIds)))
                            throw new IllegalArgumentException("assigned result IDs do not match the requested candidate IDs");
                        assignedOutcomes = assignments.stream().map(item -> byId.get(item.candidate().id())).toList();
                        Map<String, List<String>> assignmentEvidenceIds = new LinkedHashMap<>();
                        assignments.forEach(item -> assignmentEvidenceIds.put(item.candidate().id(), item.evidenceIds()));
                        assignedOutcomes.forEach(outcome -> outcome.sharedRules().forEach(rule -> sharedRules.putIfAbsent(rule.id(), rule)));
                        assignedOutcomes = retrieveNeededFields(documentId, extractionVersion, scopeVersion, assignments, assignedOutcomes,
                                evidenceById, assignmentEvidenceIds, sharedRules, executionActorPlayerId);
                        for (int i = 0; i < assignments.size(); i++) {
                            RulebookActionAssignment assignment = assignments.get(i);
                            RulebookActionDefinitionResult result = assignedOutcomes.get(i);
                            if (result.definition() != null) {
                                if (!assignment.candidate().name().equals(result.definition().name())
                                        || !assignment.candidate().actionKind().equals(result.definition().actionKind()))
                                    throw new IllegalArgumentException("assigned result identity does not match the candidate");
                                validateEvidence(assignmentEvidenceIds.get(assignment.candidate().id()).stream().map(evidenceById::get).toList(), List.of(result.definition()));
                            }
                        }
                        repository.saveBatch(new RulebookActionDefinitionBatch(documentId, extractionVersion, checkpointIndex,
                                sourceIds, "COMPLETED", assignedOutcomes.stream().filter(item -> item.definition() != null).map(RulebookActionDefinitionResult::definition).toList(),
                                null, CONTRACT_VERSION, assignments.stream().map(RulebookActionAssignment::candidate).toList(), assignedOutcomes));
                        log("dev_rulebook_action_extraction stage=assigned_batch_completed documentId={} extractionVersion={} batchIndex={} expectedActionCount={} actualResultCount={} completedCount={} insufficientCount={} failedCount={} elapsedMs={} outcome=completed",
                                documentId, extractionVersion, batchIndex, assignments.size(), assignedOutcomes.size(), assignedOutcomes.stream().filter(item -> "COMPLETED".equals(item.status())).count(),
                                assignedOutcomes.stream().filter(item -> "INSUFFICIENT_EVIDENCE".equals(item.status())).count(), assignedOutcomes.stream().filter(item -> "FAILED".equals(item.status())).count(),
                                (System.nanoTime() - batchStarted) / 1_000_000);
                    } catch (RuntimeException failure) {
                        if (failure instanceof TargetedInputBudgetExceededException) throw failure;
                        assignedOutcomes = failure instanceof RulebookActionOutputLimitException
                                ? splitOutputLimitBatch(documentId, extractionVersion, assignments, assignedEvidence,
                                        assignedCoverage, executionActorPlayerId, 1)
                                : assignments.stream().map(item -> new RulebookActionDefinitionResult(item.candidate().id(), "FAILED", null,
                                        failure.getClass().getSimpleName())).toList();
                        repository.saveBatch(new RulebookActionDefinitionBatch(documentId, extractionVersion, checkpointIndex,
                                sourceIds, "COMPLETED", List.of(), failure instanceof RulebookActionOutputLimitException ? "OUTPUT_LIMIT" : failure.getClass().getSimpleName(), CONTRACT_VERSION,
                                assignments.stream().map(RulebookActionAssignment::candidate).toList(), assignedOutcomes));
                        log("dev_rulebook_action_extraction stage=assigned_batch_failed documentId={} extractionVersion={} batchIndex={} expectedActionCount={} actualResultCount={} failureCode={} elapsedMs={} recoveryAction={} outcome=failed",
                                documentId, extractionVersion, batchIndex, assignments.size(), assignedOutcomes.size(),
                                failure instanceof RulebookActionOutputLimitException ? "OUTPUT_LIMIT" : failure.getClass().getSimpleName(),
                                (System.nanoTime() - batchStarted) / 1_000_000,
                                failure instanceof RulebookActionOutputLimitException ? "split_output_limit_batch" : "retry_failed_actions");
                    }
                }
                Set<String> failedIds = assignedOutcomes.stream().filter(result -> "FAILED".equals(result.status()))
                        .filter(result -> !"OUTPUT_LIMIT".equals(result.reason()))
                        .map(RulebookActionDefinitionResult::candidateId).collect(java.util.stream.Collectors.toSet());
                List<RulebookActionAssignment> retryAssignments = assignments.stream()
                        .filter(assignment -> failedIds.contains(assignment.candidate().id())).toList();
                if (!retryAssignments.isEmpty()) {
                    log("dev_rulebook_action_extraction stage=assigned_retry_started documentId={} extractionVersion={} batchIndex={} expectedRetryCount={} recoveryAction=retry_failed_assignments outcome=started",
                            documentId, extractionVersion, batchIndex, retryAssignments.size());
                    try {
                        List<String> retryIds = retryAssignments.stream().map(item -> item.candidate().id()).toList();
                        List<RulebookActionQueryCoverage> retryCoverage = List.of(new RulebookActionQueryCoverage(
                                "assigned-actions", fingerprint(batchQueryFingerprint + ":retry1"), Math.min(CANDIDATES_PER_QUERY, retryIds.size())));
                        List<RulebookActionDefinitionResult> retried = extractor.extractAssignedBatch(documentId, extractionVersion,
                                retryAssignments, assignedEvidence, retryCoverage, executionActorPlayerId);
                        Map<String, RulebookActionDefinitionResult> retriedById = retried.stream().collect(java.util.stream.Collectors.toMap(
                                RulebookActionDefinitionResult::candidateId, item -> item));
                        if (!retriedById.keySet().equals(new java.util.HashSet<>(retryIds)))
                            throw new IllegalArgumentException("retry result IDs do not match failed assignments");
                        Map<String, RulebookActionDefinitionResult> merged = assignedOutcomes.stream().collect(java.util.stream.Collectors.toMap(
                                RulebookActionDefinitionResult::candidateId, item -> item, (first, ignored) -> first, LinkedHashMap::new));
                        merged.putAll(retriedById);
                        assignedOutcomes = assignments.stream().map(item -> merged.get(item.candidate().id())).toList();
                    } catch (RuntimeException retryFailure) {
                        log("dev_rulebook_action_extraction stage=assigned_retry_failed documentId={} extractionVersion={} batchIndex={} expectedRetryCount={} actualRecoveredCount=0 failureClass={} recoveryAction=retain_failed_status outcome=failed",
                                documentId, extractionVersion, batchIndex, retryAssignments.size(), retryFailure.getClass().getSimpleName());
                    }
                }
                repository.saveBatch(new RulebookActionDefinitionBatch(documentId, extractionVersion, checkpointIndex,
                        sourceIds, "COMPLETED", assignedOutcomes.stream().filter(item -> item.definition() != null)
                                .map(RulebookActionDefinitionResult::definition).toList(),
                        assignedOutcomes.stream().anyMatch(item -> "OUTPUT_LIMIT".equals(item.reason())) ? "OUTPUT_LIMIT"
                                : assignedOutcomes.stream().anyMatch(item -> "FAILED".equals(item.status())) ? "assigned_actions_failed" : null,
                        CONTRACT_VERSION, assignments.stream().map(RulebookActionAssignment::candidate).toList(), assignedOutcomes));
                for (RulebookActionDefinitionResult result : assignedOutcomes) {
                    result.sharedRules().forEach(rule -> sharedRules.putIfAbsent(rule.id(), rule));
                    RulebookActionCandidate candidate = assignments.stream().map(RulebookActionAssignment::candidate)
                            .filter(item -> item.id().equals(result.candidateId())).findFirst().orElseThrow();
                    if (result.definition() != null) definitions.add(result.definition());
                    actionCoverage.add(new RulebookActionDefinitionCoverage(candidate.id(), candidate.name(), candidate.sourceLocator(),
                            result.status(), result.reason()));
                }
            }
            validateEvidence(List.copyOf(evidenceById.values()), definitions);
            boolean failedAssignments = actionCoverage.stream().anyMatch(item -> "FAILED".equals(item.status()));
            boolean noDefinitionsForDiscoveredActions = !actions.isEmpty() && definitions.isEmpty();
            boolean failedExtraction = failedAssignments || noDefinitionsForDiscoveredActions;
            String extractionFailureCode = actionCoverage.stream().anyMatch(item -> "OUTPUT_LIMIT".equals(item.reason())) ? "OUTPUT_LIMIT"
                    : failedAssignments ? "assigned_actions_failed"
                    : noDefinitionsForDiscoveredActions ? "no_definitions_extracted" : null;
            RulebookActionDefinitionExtraction complete = new RulebookActionDefinitionExtraction(documentId, extractionVersion,
                    failedExtraction ? "FAILED" : "COMPLETED", pages.size(), 0, 0, 0, definitions,
                    extractionFailureCode, CONTRACT_VERSION,
                    coverage, capped, evidence.size(), actionCoverage, List.copyOf(sharedRules.values()));
            repository.save(complete);
            log("dev_rulebook_action_extraction stage=finalized documentId={} extractionVersion={} scopeVersion={} queryCount={} evidenceCount={} definitionCount={} definitionIds={} expectedActionCount={} completedActionCount={} insufficientActionCount={} failedActionCount={} extractionStatus={} failureCode={} capped={} elapsedMs={} outcome={}",
                    documentId, extractionVersion, scopeVersion, coverage.size(), evidence.size(), definitions.size(),
                    definitions.stream().map(RulebookActionDefinition::id).toList(),
                    actionCoverage.size(), actionCoverage.stream().filter(item -> "COMPLETED".equals(item.status())).count(),
                    actionCoverage.stream().filter(item -> "INSUFFICIENT_EVIDENCE".equals(item.status())).count(),
                    actionCoverage.stream().filter(item -> "FAILED".equals(item.status())).count(),
                    failedExtraction ? "FAILED" : "COMPLETED", extractionFailureCode, capped,
                    (System.nanoTime() - started) / 1_000_000, failedExtraction ? "partial" : "completed");
            return complete;
        } catch (RuntimeException failure) {
            if (failure instanceof TargetedInputBudgetExceededException && existing != null
                    && "COMPLETED".equals(existing.status()) && !existing.definitions().isEmpty()) {
                repository.save(new RulebookActionDefinitionExtraction(documentId, extractionVersion, "FAILED", pages.size(),
                        0, 0, 0, existing.definitions(), "targeted_input_budget_exceeded", existing.contractVersion(),
                        existing.queryCoverage(), existing.evidenceLimitReached(), existing.candidateEvidenceCount(),
                        existing.actionCoverage(), existing.sharedRules()));
                log("dev_rulebook_action_extraction stage=targeted_budget_failure_preserved_prior documentId={} extractionVersion={} preservedContractVersion={} preservedDefinitionCount={} failureClass={} outcome=failed",
                        documentId, extractionVersion, existing.contractVersion(), existing.definitions().size(), failure.getClass().getSimpleName());
            } else {
            repository.save(new RulebookActionDefinitionExtraction(documentId, extractionVersion, "FAILED", pages.size(),
                    0, 0, 0, List.of(), failure.getClass().getSimpleName(), CONTRACT_VERSION,
                    coverage, capped, collectedEvidenceCount));
            }
            log("dev_rulebook_action_extraction stage=failed documentId={} extractionVersion={} scopeVersion={} queryCount={} failureClass={} elapsedMs={} recoveryAction=retry_dense_query_collection outcome=failed",
                    documentId, extractionVersion, scopeVersion, QUERIES.size(), failure.getClass().getSimpleName(),
                    (System.nanoTime() - started) / 1_000_000);
            throw failure;
        }
    }

    private List<RulebookActionDefinitionResult> splitOutputLimitBatch(UUID documentId, String extractionVersion,
            List<RulebookActionAssignment> assignments, List<RulebookActionEvidence> evidence,
            List<RulebookActionQueryCoverage> coverage, UUID actor, int depth) {
        if (assignments.size() == 1) {
            var candidate = assignments.getFirst().candidate();
            log("dev_rulebook_action_extraction stage=assigned_output_limit_singleton documentId={} extractionVersion={} splitDepth={} expectedActionCount=1 actualActionCount=1 evidenceCount={} inputChars={} failureCode=OUTPUT_LIMIT recoveryAction=retain_failed_status outcome=failed",
                    documentId, extractionVersion, depth, evidence.size(), evidence.stream().mapToInt(item -> item.text().length()).sum());
            return List.of(new RulebookActionDefinitionResult(candidate.id(), "FAILED", null, "OUTPUT_LIMIT"));
        }
        int middle = assignments.size() / 2;
        List<RulebookActionAssignment> left = assignments.subList(0, middle);
        List<RulebookActionAssignment> right = assignments.subList(middle, assignments.size());
        log("dev_rulebook_action_extraction stage=assigned_output_limit_split documentId={} extractionVersion={} splitDepth={} splitReason=OUTPUT_LIMIT expectedActionCount={} leftActionCount={} rightActionCount={} evidenceCount={} inputChars={} recoveryAction=extract_bounded_children outcome=started",
                documentId, extractionVersion, depth, assignments.size(), left.size(), right.size(), evidence.size(),
                evidence.stream().mapToInt(item -> item.text().length()).sum());
        List<RulebookActionDefinitionResult> results = new ArrayList<>();
        for (List<RulebookActionAssignment> child : List.of(left, right)) {
            Set<String> neededIds = child.stream().flatMap(item -> item.evidenceIds().stream()).collect(java.util.stream.Collectors.toSet());
            List<RulebookActionEvidence> childEvidence = evidence.stream().filter(item -> neededIds.contains(item.id())).toList();
            Set<String> actionIds = child.stream().map(item -> "action:" + item.candidate().id()).collect(java.util.stream.Collectors.toSet());
            List<RulebookActionQueryCoverage> childCoverage = coverage.stream()
                    .filter(item -> !item.queryId().startsWith("action:") || actionIds.contains(item.queryId())).toList();
            log("dev_rulebook_action_extraction stage=assigned_output_limit_child documentId={} extractionVersion={} splitDepth={} splitReason=OUTPUT_LIMIT expectedActionCount={} evidenceCount={} inputChars={} recoveryAction=retry_child_once outcome=started",
                    documentId, extractionVersion, depth, child.size(), childEvidence.size(),
                    childEvidence.stream().mapToInt(item -> item.text().length()).sum());
            try {
                List<RulebookActionDefinitionResult> childResults = extractor.extractAssignedBatch(documentId, extractionVersion,
                        child, childEvidence, childCoverage, actor);
                Map<String, RulebookActionDefinitionResult> byId = childResults.stream().collect(java.util.stream.Collectors.toMap(
                        RulebookActionDefinitionResult::candidateId, item -> item));
                if (byId.size() != child.size() || !byId.keySet().equals(child.stream().map(item -> item.candidate().id())
                        .collect(java.util.stream.Collectors.toSet()))) throw new IllegalArgumentException("split result IDs do not match child assignments");
                Map<String, RulebookActionEvidence> evidenceById = childEvidence.stream().collect(java.util.stream.Collectors.toMap(
                        RulebookActionEvidence::id, item -> item));
                for (RulebookActionAssignment assignment : child) {
                    RulebookActionDefinitionResult result = byId.get(assignment.candidate().id());
                    if (result.definition() != null) {
                        if (!assignment.candidate().name().equals(result.definition().name())
                                || !assignment.candidate().actionKind().equals(result.definition().actionKind()))
                            throw new IllegalArgumentException("split result identity does not match its candidate");
                        validateEvidence(assignment.evidenceIds().stream().map(evidenceById::get).filter(Objects::nonNull).toList(),
                                List.of(result.definition()));
                    }
                }
                results.addAll(child.stream().map(item -> byId.get(item.candidate().id())).toList());
            } catch (RulebookActionOutputLimitException outputLimit) {
                results.addAll(splitOutputLimitBatch(documentId, extractionVersion, child, childEvidence, childCoverage, actor, depth + 1));
            } catch (RuntimeException childFailure) {
                results.addAll(child.stream().map(item -> new RulebookActionDefinitionResult(item.candidate().id(), "FAILED", null,
                        childFailure.getClass().getSimpleName())).toList());
            }
        }
        log("dev_rulebook_action_extraction stage=assigned_output_limit_split documentId={} extractionVersion={} splitDepth={} splitReason=OUTPUT_LIMIT expectedActionCount={} actualResultCount={} completedCount={} insufficientCount={} failedCount={} outcome=completed",
                documentId, extractionVersion, depth, assignments.size(), results.size(),
                results.stream().filter(item -> "COMPLETED".equals(item.status())).count(),
                results.stream().filter(item -> "INSUFFICIENT_EVIDENCE".equals(item.status())).count(),
                results.stream().filter(item -> "FAILED".equals(item.status())).count());
        return List.copyOf(results);
    }

    private List<RulebookActionDefinitionResult> retrieveNeededFields(UUID documentId, String extractionVersion, long scopeVersion,
            List<RulebookActionAssignment> assignments, List<RulebookActionDefinitionResult> initial,
            Map<String, RulebookActionEvidence> evidenceById, Map<String, List<String>> assignmentEvidenceIds,
            Map<String, RulebookActionSharedRule> reusableRules, UUID actor) {
        Map<String, RulebookActionDefinitionResult> current = initial.stream().collect(java.util.stream.Collectors.toMap(
                RulebookActionDefinitionResult::candidateId, item -> item, (first, ignored) -> first, LinkedHashMap::new));
        List<TargetedFollowup> pendingFollowups = new ArrayList<>();
        for (RulebookActionAssignment original : assignments) {
            RulebookActionDefinitionResult first = current.get(original.candidate().id());
            if (first == null || "FAILED".equals(first.status())) continue;
            RulebookActionDefinition known = first.definition();
            Map<String, Object> knownFields = known == null ? Map.of() : known.fields();
            List<String> missing = new ArrayList<>(first.neededFields().stream()
                    .filter(field -> knownFields.get(field) == null).distinct().toList());
            boolean recoverMissingDefinition = "INSUFFICIENT_EVIDENCE".equals(first.status()) && known == null && missing.isEmpty();
            if (missing.isEmpty() && !recoverMissingDefinition) continue;
            List<String> initiallyMissing = List.copyOf(missing);
            List<RulebookActionSharedRule> relevantRules = reusableRules.values().stream()
                    .filter(rule -> rule.fields().keySet().stream().anyMatch(initiallyMissing::contains)).limit(8).toList();
            if (!relevantRules.isEmpty()) {
                List<String> ownEvidenceIds = assignmentEvidenceIds.getOrDefault(original.candidate().id(), original.evidenceIds());
                List<RulebookActionEvidence> ownEvidence = ownEvidenceIds.stream().map(evidenceById::get).filter(Objects::nonNull).toList();
                RulebookActionAssignment reuse = new RulebookActionAssignment(original.candidate(), ownEvidenceIds,
                        fingerprint(original.candidate().name() + ":shared-rule-reuse:" + missing), 0,
                        known == null ? Map.of() : known.fields(), missing, relevantRules);
                long reuseStarted = System.nanoTime();
                log("dev_rulebook_action_extraction stage=shared_rule_reuse_started documentId={} actionId={} expectedRuleCount={} requestedFields={} ownEvidenceCount={} outcome=started",
                        documentId, original.candidate().id(), relevantRules.size(), missing, ownEvidence.size());
                List<RulebookActionDefinitionResult> reuseResults = extractor.extractAssignedBatch(documentId, extractionVersion,
                        List.of(reuse), ownEvidence,
                        List.of(new RulebookActionQueryCoverage("action:" + original.candidate().id(), reuse.queryFingerprint(), 0)), actor);
                if (reuseResults.size() != 1 || !original.candidate().id().equals(reuseResults.getFirst().candidateId())
                        || "FAILED".equals(reuseResults.getFirst().status()))
                    throw new IllegalArgumentException("shared-rule applicability result does not match its assigned action");
                RulebookActionDefinitionResult reuseResult = reuseResults.getFirst();
                reuseResult.sharedRules().forEach(rule -> reusableRules.putIfAbsent(rule.id(), rule));
                if (reuseResult.definition() != null) {
                    if (!original.candidate().id().equals(reuseResult.definition().id())
                            || !original.candidate().name().equals(reuseResult.definition().name())
                            || !original.candidate().actionKind().equals(reuseResult.definition().actionKind()))
                        throw new IllegalArgumentException("shared-rule applicability definition identity does not match its assigned action");
                    validateEvidence(ownEvidence, List.of(reuseResult.definition()));
                    Set<String> allowedRuleIds = relevantRules.stream().map(RulebookActionSharedRule::id).collect(java.util.stream.Collectors.toSet());
                    if (!allowedRuleIds.containsAll(reuseResult.definition().sharedRuleRefs()))
                        throw new IllegalArgumentException("shared-rule reuse returned a reference outside the supplied rule allow-list");
                    Map<String, Object> allowedFields = new LinkedHashMap<>();
                    Set<String> requestedReuseFields = Set.copyOf(missing);
                    relevantRules.stream().filter(rule -> reuseResult.definition().sharedRuleRefs().contains(rule.id()))
                            .forEach(rule -> rule.fields().forEach((field, value) -> {
                                if (requestedReuseFields.contains(field) && value != null) allowedFields.putIfAbsent(field, value);
                            }));
                    if (known == null) {
                        Map<String, Object> fields = new LinkedHashMap<>(allowedFields);
                        known = new RulebookActionDefinition(reuseResult.definition().id(), reuseResult.definition().name(),
                                reuseResult.definition().actionKind(), reuseResult.definition().sourceLocator(),
                                reuseResult.definition().sourceChunkId(), reuseResult.definition().sourceEvidence(), fields,
                                reuseResult.definition().unsupportedFields(), reuseResult.definition().sharedRuleRefs());
                    } else {
                        Map<String, Object> fields = new LinkedHashMap<>(known.fields());
                        allowedFields.forEach(fields::putIfAbsent);
                        List<String> refs = new ArrayList<>(known.sharedRuleRefs());
                        reuseResult.definition().sharedRuleRefs().forEach(id -> { if (!refs.contains(id)) refs.add(id); });
                        known = new RulebookActionDefinition(known.id(), known.name(), known.actionKind(), known.sourceLocator(),
                                known.sourceChunkId(), known.sourceEvidence(), fields, known.unsupportedFields(), refs);
                    }
                    Map<String, Object> resolvedFields = known.fields();
                    missing = new ArrayList<>(missing.stream().filter(field -> resolvedFields.get(field) == null).toList());
                    current.put(original.candidate().id(), new RulebookActionDefinitionResult(original.candidate().id(), "COMPLETED", known,
                            null, first.neededFields(), reuseResult.sharedRules(), known.sharedRuleRefs()));
                }
                log("dev_rulebook_action_extraction stage=shared_rule_reuse documentId={} actionId={} expectedRuleCount={} actualRuleCount={} requestedFields={} remainingFields={} ownEvidenceCount={} elapsedMs={} outcome={}",
                        documentId, original.candidate().id(), relevantRules.size(), reuse.availableSharedRules().size(), first.neededFields(), missing,
                        ownEvidence.size(), (System.nanoTime() - reuseStarted) / 1_000_000,
                        reuseResult.definition() == null ? "partial" : missing.isEmpty() ? "completed" : "partial");
            }
            if (missing.isEmpty() && !recoverMissingDefinition) continue;
            String query = original.candidate().name() + (missing.isEmpty() ? "" : " " + missing.stream()
                    .map(RulebookActionDefinitionExtractionService::fieldMeaning).collect(java.util.stream.Collectors.joining(" ")));
            List<String> ownEvidenceIds = assignmentEvidenceIds.getOrDefault(original.candidate().id(), original.evidenceIds());
            long lookupStarted = System.nanoTime();
            List<String> unresolvedFields = List.copyOf(missing);
            List<RulebookActionSharedRule> followupRules = reusableRules.values().stream()
                    .filter(rule -> rule.fields().keySet().stream().anyMatch(unresolvedFields::contains)).limit(8).toList();
            RulebookActionAssignment retry = new RulebookActionAssignment(original.candidate(), ownEvidenceIds,
                    fingerprint(query), 0, known == null ? Map.of() : known.fields(), missing,
                    followupRules);
            EvidenceSearchRequest request = new EvidenceSearchRequest(new OwnerPlayerId(CATALOG_OWNER), null, null,
                    "rulebook-action-needed-fields", "action:" + original.candidate().id(),
                    List.of(new AuthorizedDocumentScope(new KnowledgeDocumentId(documentId), scopeVersion,
                            DocumentType.RULEBOOK, new OwnerPlayerId(CATALOG_OWNER))), List.of(), query,
                    TARGETED_CANDIDATES_PER_QUERY, 0, ownEvidenceIds.stream().map(UUID::fromString).toList(), minimumDenseSimilarity);
            pendingFollowups.add(new TargetedFollowup(retry, known, List.copyOf(missing), request, lookupStarted));
            log("dev_rulebook_action_extraction stage=targeted_lookup_started documentId={} actionId={} requestedFields={} seenEvidenceCount={} candidateLimit={} minimumDenseSimilarity={} queryFingerprint={} outcome=started",
                    documentId, original.candidate().id(), missing, ownEvidenceIds.size(), TARGETED_CANDIDATES_PER_QUERY,
                    minimumDenseSimilarity, fingerprint(query));
        }
        List<TargetedFollowup> readyFollowups = new ArrayList<>();
        for (int start = 0; start < pendingFollowups.size(); start += TARGETED_SEARCH_BATCH_SIZE) {
            List<TargetedFollowup> searchBatch = pendingFollowups.subList(start,
                    Math.min(start + TARGETED_SEARCH_BATCH_SIZE, pendingFollowups.size()));
            List<EvidenceSearchRequest> requests = searchBatch.stream().map(TargetedFollowup::request).toList();
            long searchStarted = System.nanoTime();
            log("dev_rulebook_action_extraction stage=targeted_lookup_batch_started documentId={} expectedActionIds={} expectedRequestCount={} outcome=started",
                    documentId, searchBatch.stream().map(item -> item.assignment().candidate().id()).toList(), requests.size());
            List<List<EvidenceCandidate>> results;
            try {
                results = denseSearch.searchBatch(requests);
            } catch (RuntimeException failure) {
                log("dev_rulebook_action_extraction stage=targeted_lookup_batch_failed documentId={} actionIds={} expectedRequestCount={} failureClass={} elapsedMs={} recoveryAction=retry_targeted_search_batch outcome=failed",
                        documentId, searchBatch.stream().map(item -> item.assignment().candidate().id()).toList(), requests.size(),
                        failure.getClass().getSimpleName(), (System.nanoTime() - searchStarted) / 1_000_000);
                throw failure;
            }
            if (results == null || results.size() != requests.size()) {
                log("dev_rulebook_action_extraction stage=targeted_lookup_batch_validation expectedActionIds={} expectedResultCount={} actualResultCount={} failureKind=result_count_mismatch recoveryAction=reject_search_batch outcome=failed",
                        searchBatch.stream().map(item -> item.assignment().candidate().id()).toList(), requests.size(), results == null ? -1 : results.size());
                throw new IllegalStateException("targeted dense RAG batch result count mismatch");
            }
            for (int index = 0; index < results.size(); index++) {
                TargetedFollowup task = searchBatch.get(index);
                List<EvidenceCandidate> found = results.get(index);
                if (found == null || found.size() > TARGETED_CANDIDATES_PER_QUERY) {
                    log("dev_rulebook_action_extraction stage=targeted_lookup_result_validation actionId={} expectedCandidateCount=0..{} actualCandidateCount={} failureKind=candidate_count_out_of_bounds recoveryAction=reject_search_result outcome=failed",
                            task.assignment().candidate().id(), TARGETED_CANDIDATES_PER_QUERY, found == null ? -1 : found.size());
                    throw new IllegalStateException("targeted dense RAG query returned an invalid result count");
                }
                List<String> ownIds = task.assignment().evidenceIds();
                LinkedHashMap<String, RulebookActionEvidence> added = new LinkedHashMap<>();
                for (EvidenceCandidate match : found) {
                    if (!match.documentId().value().equals(documentId) || match.extractionVersion() != scopeVersion
                            || match.documentType() != DocumentType.RULEBOOK) {
                        log("dev_rulebook_action_extraction stage=targeted_source_scope_validation actionId={} expectedDocumentId={} expectedScopeVersion={} expectedDocumentType=RULEBOOK actualDocumentId={} actualScopeVersion={} actualDocumentType={} failureKind=candidate_outside_published_scope recoveryAction=reject_search_result outcome=failed",
                                task.assignment().candidate().id(), documentId, scopeVersion, match.documentId().value(),
                                match.extractionVersion(), match.documentType());
                        throw new IllegalStateException("targeted dense RAG returned evidence outside the published source scope");
                    }
                    if (minimumDenseSimilarity != null && match.denseSimilarity() != null
                            && match.denseSimilarity() < minimumDenseSimilarity) continue;
                    String id = match.chunkId().value().toString();
                    if (ownIds.contains(id)) continue;
                    RulebookActionEvidence evidence = new RulebookActionEvidence(id, match.locator(), match.excerpt());
                    RulebookActionEvidence previous = evidenceById.putIfAbsent(id, evidence);
                    if (previous != null && (!previous.locator().equals(evidence.locator()) || !previous.text().equals(evidence.text()))) {
                        log("dev_rulebook_action_extraction stage=targeted_evidence_id_conflict actionId={} evidenceId={} expectedTextLength={} actualTextLength={} expectedLocatorFingerprint={} actualLocatorFingerprint={} failureKind=conflicting_chunk_identity recoveryAction=reject_search_result outcome=failed",
                                task.assignment().candidate().id(), id, previous.text().length(), evidence.text().length(),
                                fingerprint(previous.locator()), fingerprint(evidence.locator()));
                        throw new IllegalStateException("targeted dense RAG returned conflicting evidence for one chunk ID");
                    }
                    added.putIfAbsent(id, previous == null ? evidence : previous);
                }
                log("dev_rulebook_action_extraction stage=targeted_lookup_candidates documentId={} actionId={} expectedCandidateCount=0..{} actualCandidateCount={} actualNewEvidenceCount={} addedEvidenceIds={} returnedChars={} minimumDenseSimilarity={} elapsedMs={} outcome={}",
                        documentId, task.assignment().candidate().id(), TARGETED_CANDIDATES_PER_QUERY, found.size(), added.size(),
                        added.keySet(), added.values().stream().mapToInt(item -> item.text().length()).sum(), minimumDenseSimilarity,
                        (System.nanoTime() - task.lookupStarted()) / 1_000_000, added.isEmpty() ? "partial" : "completed");
                if (added.isEmpty()) continue;
                List<String> ids = new ArrayList<>(ownIds);
                ids.addAll(added.keySet());
                assignmentEvidenceIds.put(task.assignment().candidate().id(), List.copyOf(ids));
                RulebookActionAssignment retry = new RulebookActionAssignment(task.assignment().candidate(), ids,
                        task.assignment().queryFingerprint(), found.size(), task.known() == null ? Map.of() : task.known().fields(),
                        task.requestedFields(), task.assignment().availableSharedRules());
                List<RulebookActionEvidence> combined = ids.stream().map(evidenceById::get).toList();
                readyFollowups.add(new TargetedFollowup(retry, task.known(), task.requestedFields(), task.request(),
                        combined, added.size(), task.lookupStarted()));
            }
            log("dev_rulebook_action_extraction stage=targeted_lookup_batch_completed documentId={} expectedRequestCount={} actualResultCount={} actionIds={} collectedEvidenceCount={} elapsedMs={} outcome=completed",
                    documentId, requests.size(), results.size(), searchBatch.stream().map(item -> item.assignment().candidate().id()).toList(),
                    readyFollowups.size(), (System.nanoTime() - searchStarted) / 1_000_000);
        }
        for (List<TargetedFollowup> batch : targetedFollowupBatches(readyFollowups, documentId, extractionVersion, actor)) {
            List<RulebookActionAssignment> followupAssignments = batch.stream().map(TargetedFollowup::assignment).toList();
            LinkedHashMap<String, RulebookActionEvidence> union = new LinkedHashMap<>();
            batch.forEach(task -> task.evidence().forEach(item -> union.putIfAbsent(item.id(), item)));
            List<RulebookActionEvidence> followupEvidence = List.copyOf(union.values());
            List<RulebookActionQueryCoverage> followupCoverage = batch.stream().map(task -> new RulebookActionQueryCoverage(
                    "action:" + task.assignment().candidate().id(), task.assignment().queryFingerprint(), task.assignment().candidateCount())).toList();
            long batchStarted = System.nanoTime();
            long estimatedTokens = estimateTargetedBatch(batch, followupEvidence, documentId, extractionVersion, actor);
            log("dev_rulebook_action_extraction stage=targeted_batch_started documentId={} expectedActionIds={} expectedActionCount={} evidenceCount={} inputChars={} estimatedInputTokens={} totalInputTokenLimit={} externalTokenReserve={} usableInputTokenLimit={} aiRequestCount=1 outcome=started",
                    documentId, followupAssignments.stream().map(item -> item.candidate().id()).toList(), followupAssignments.size(), followupEvidence.size(),
                    followupEvidence.stream().mapToInt(item -> item.text().length()).sum(), estimatedTokens, targetedInputTokenLimit,
                    targetedExternalTokenReserve, effectiveTargetedInputTokenLimit());
            List<RulebookActionDefinitionResult> results;
            try {
                results = extractor.extractAssignedBatch(documentId, extractionVersion, followupAssignments, followupEvidence, followupCoverage, actor);
            } catch (RuntimeException failure) {
                log("dev_rulebook_action_extraction stage=targeted_batch_failed documentId={} expectedActionIds={} actionCount={} evidenceCount={} inputChars={} aiRequestCount=1 failureClass={} elapsedMs={} recoveryAction=retain_checkpoint_retry_batch outcome=failed",
                        documentId, followupAssignments.stream().map(item -> item.candidate().id()).toList(), followupAssignments.size(), followupEvidence.size(),
                        followupEvidence.stream().mapToInt(item -> item.text().length()).sum(), failure.getClass().getSimpleName(),
                        (System.nanoTime() - batchStarted) / 1_000_000);
                throw failure;
            }
            if (results == null) throw new IllegalArgumentException("targeted followup returned a null result list");
            Set<String> expectedIds = followupAssignments.stream().map(item -> item.candidate().id()).collect(java.util.stream.Collectors.toSet());
            Map<String, RulebookActionDefinitionResult> byId = new LinkedHashMap<>();
            for (RulebookActionDefinitionResult result : results) {
                if (!expectedIds.contains(result.candidateId()) || byId.putIfAbsent(result.candidateId(), result) != null) {
                    log("dev_rulebook_action_extraction stage=targeted_batch_id_validation expectedCandidateIds={} actualCandidateIds={} failureKind=unexpected_or_duplicate_id recoveryAction=reject_agent_result outcome=failed",
                            expectedIds, results.stream().map(RulebookActionDefinitionResult::candidateId).toList());
                    throw new IllegalArgumentException("targeted followup result IDs do not match assigned actions");
                }
            }
            if (!byId.keySet().equals(expectedIds)) {
                log("dev_rulebook_action_extraction stage=targeted_batch_id_validation expectedCandidateIds={} actualCandidateIds={} failureKind=missing_result_id recoveryAction=reject_agent_result outcome=failed",
                        expectedIds, byId.keySet());
                throw new IllegalArgumentException("targeted followup omitted one or more assigned action results");
            }
            for (TargetedFollowup task : batch) {
                RulebookActionCandidate candidate = task.assignment().candidate();
                RulebookActionDefinitionResult refined = byId.get(candidate.id());
                if (refined == null || refined.definition() == null || "FAILED".equals(refined.status())) {
                    log("dev_rulebook_action_extraction stage=targeted_followup_unusable documentId={} actionId={} expectedResultCount=1 actualResultCount={} requestedFields={} addedEvidenceCount={} recoveryAction=retain_initial_definition outcome=partial",
                            documentId, candidate.id(), refined == null ? 0 : 1, task.requestedFields(), task.addedEvidenceCount());
                    continue;
                }
                if (!candidate.id().equals(refined.definition().id()) || !candidate.name().equals(refined.definition().name())
                        || !candidate.actionKind().equals(refined.definition().actionKind()))
                    throw new IllegalArgumentException("targeted followup definition identity does not match its assigned action");
                validateEvidence(task.evidence(), List.of(refined.definition()));
                RulebookActionDefinition merged = task.known() == null ? refined.definition()
                        : mergeKnownFields(task.known(), refined.definition(), task.requestedFields());
                current.put(candidate.id(), new RulebookActionDefinitionResult(candidate.id(), "COMPLETED", merged,
                        null, refined.neededFields(), refined.sharedRules(), merged.sharedRuleRefs()));
                refined.sharedRules().forEach(rule -> reusableRules.putIfAbsent(rule.id(), rule));
                log("dev_rulebook_action_extraction stage=targeted_followup_completed documentId={} actionId={} requestedFields={} addedEvidenceCount={} mergedFieldCount={} elapsedMs={} outcome=completed",
                        documentId, candidate.id(), task.requestedFields(), task.addedEvidenceCount(), merged.fields().size(),
                        (System.nanoTime() - task.lookupStarted()) / 1_000_000);
            }
            log("dev_rulebook_action_extraction stage=targeted_batch_completed documentId={} expectedActionCount={} actualResultCount={} evidenceCount={} inputChars={} aiRequestCount=1 elapsedMs={} outcome=completed",
                    documentId, batch.size(), results.size(), followupEvidence.size(), followupEvidence.stream().mapToInt(item -> item.text().length()).sum(),
                    (System.nanoTime() - batchStarted) / 1_000_000);
        }
        return assignments.stream().map(item -> current.get(item.candidate().id())).toList();
    }

    private List<List<TargetedFollowup>> targetedFollowupBatches(List<TargetedFollowup> tasks, UUID documentId,
            String extractionVersion, UUID actor) {
        List<List<TargetedFollowup>> batches = new ArrayList<>();
        List<TargetedFollowup> current = new ArrayList<>();
        LinkedHashMap<String, RulebookActionEvidence> evidence = new LinkedHashMap<>();
        for (TargetedFollowup task : tasks) {
            LinkedHashMap<String, RulebookActionEvidence> combined = new LinkedHashMap<>(evidence);
            for (RulebookActionEvidence item : task.evidence()) {
                RulebookActionEvidence prior = combined.putIfAbsent(item.id(), item);
                if (prior != null && (!prior.locator().equals(item.locator()) || !prior.text().equals(item.text())))
                    throw new IllegalArgumentException("targeted evidence conflicts for one chunk ID");
            }
            List<TargetedFollowup> candidateTasks = new ArrayList<>(current);
            candidateTasks.add(task);
            long tokens = estimateTargetedBatch(candidateTasks, combined.values().stream().toList(), documentId, extractionVersion, actor);
            if (!current.isEmpty() && tokens > effectiveTargetedInputTokenLimit()) {
                long currentTokens = estimateTargetedBatch(current, evidence.values().stream().toList(), documentId, extractionVersion, actor);
                log("dev_rulebook_action_extraction stage=targeted_batch_split documentId={} currentActionIds={} nextActionId={} currentEstimatedTokens={} combinedEstimatedTokens={} usableInputTokenLimit={} currentEvidenceCount={} combinedEvidenceCount={} currentInputChars={} combinedInputChars={} recoveryAction=start_next_whole_action_batch outcome=partial",
                        documentId, current.stream().map(item -> item.assignment().candidate().id()).toList(),
                        task.assignment().candidate().id(), currentTokens, tokens, effectiveTargetedInputTokenLimit(), evidence.size(),
                        combined.size(), evidence.values().stream().mapToInt(item -> item.text().length()).sum(),
                        combined.values().stream().mapToInt(item -> item.text().length()).sum());
                batches.add(List.copyOf(current));
                current.clear();
                evidence.clear();
                combined = new LinkedHashMap<>();
                for (RulebookActionEvidence item : task.evidence()) combined.putIfAbsent(item.id(), item);
                tokens = estimateTargetedBatch(List.of(task), combined.values().stream().toList(), documentId, extractionVersion, actor);
            }
            if (tokens > effectiveTargetedInputTokenLimit()) {
                log("dev_rulebook_action_extraction stage=targeted_action_over_budget documentId={} actionId={} estimatedInputTokens={} totalInputTokenLimit={} externalTokenReserve={} usableInputTokenLimit={} evidenceCount={} inputChars={} failureKind=single_action_exceeds_budget recoveryAction=raise_configured_token_limit_or_reduce_source_scope outcome=failed",
                        documentId, task.assignment().candidate().id(), tokens, targetedInputTokenLimit, targetedExternalTokenReserve,
                        effectiveTargetedInputTokenLimit(), task.evidence().size(),
                        task.evidence().stream().mapToInt(item -> item.text().length()).sum());
                throw new TargetedInputBudgetExceededException("targeted action " + task.assignment().candidate().id()
                        + " requires " + tokens + " estimated input tokens; available limit is " + effectiveTargetedInputTokenLimit());
            }
            current.add(task);
            evidence.clear(); evidence.putAll(combined);
        }
        if (!current.isEmpty()) batches.add(List.copyOf(current));
        return List.copyOf(batches);
    }

    private long estimateTargetedBatch(List<TargetedFollowup> tasks, List<RulebookActionEvidence> evidence,
            UUID documentId, String extractionVersion, UUID actor) {
        List<RulebookActionAssignment> assignments = tasks.stream().map(TargetedFollowup::assignment).toList();
        List<RulebookActionQueryCoverage> coverage = tasks.stream().map(task -> new RulebookActionQueryCoverage(
                "action:" + task.assignment().candidate().id(), task.assignment().queryFingerprint(), task.assignment().candidateCount())).toList();
        long tokens = extractor.estimateAssignedBatchInputTokens(documentId, extractionVersion, assignments, evidence, coverage, actor);
        if (tokens < 0) throw new IllegalStateException("targeted extraction input token estimate must not be negative");
        return tokens;
    }

    private long effectiveTargetedInputTokenLimit() {
        return targetedInputTokenLimit - targetedExternalTokenReserve;
    }

    private static RulebookActionDefinition mergeKnownFields(RulebookActionDefinition known, RulebookActionDefinition refined,
            List<String> requestedFields) {
        Map<String, Object> fields = new LinkedHashMap<>(known.fields());
        requestedFields.forEach(key -> { if (refined.fields().get(key) != null) fields.put(key, refined.fields().get(key)); });
        List<String> unsupported = new ArrayList<>(refined.unsupportedFields());
        known.unsupportedFields().forEach(value -> { if (!unsupported.contains(value)) unsupported.add(value); });
        return new RulebookActionDefinition(known.id(), known.name(), known.actionKind(), known.sourceLocator(),
                known.sourceChunkId(), known.sourceEvidence(), fields, unsupported,
                java.util.stream.Stream.concat(known.sharedRuleRefs().stream(), refined.sharedRuleRefs().stream()).distinct().toList());
    }

    private static String fieldMeaning(String field) {
        return switch (field) {
            case "cost" -> "consumed action or resource cost";
            case "range", "normalRangeFeet", "longRangeFeet" -> "use distance range";
            case "targetMode" -> "target selection";
            case "effectShape", "lengthFeet", "widthFeet", "heightFeet", "radiusFeet" -> "effect area shape and dimensions";
            case "pathBlocking", "propagation" -> "effect blocking and path rules";
            case "attackSave" -> "attack or saving check";
            default -> field.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        };
    }

    private static List<List<RulebookActionEvidence>> evidenceBatches(List<RulebookActionEvidence> evidence) {
        List<List<RulebookActionEvidence>> batches = new ArrayList<>();
        List<RulebookActionEvidence> current = new ArrayList<>();
        int chars = 0;
        for (RulebookActionEvidence item : evidence) {
        if (item.text().length() > 6_000 || item.text().length() > MAX_DISCOVERY_EVIDENCE_CHARS)
                throw new IllegalArgumentException("retrieved source excerpt exceeds the typed extraction limit");
        if (!current.isEmpty() && (current.size() >= MAX_AGENT_EVIDENCE || chars + item.text().length() > MAX_DISCOVERY_EVIDENCE_CHARS)) {
                batches.add(List.copyOf(current));
                current.clear();
                chars = 0;
            }
            current.add(item);
            chars += item.text().length();
        }
        if (!current.isEmpty()) batches.add(List.copyOf(current));
        return List.copyOf(batches);
    }

    private static List<List<RulebookActionAssignment>> assignmentBatches(List<RulebookActionAssignment> assignments,
            Map<String, RulebookActionEvidence> evidenceById) {
        List<List<RulebookActionAssignment>> batches = new ArrayList<>();
        List<RulebookActionAssignment> current = new ArrayList<>();
        LinkedHashMap<String, RulebookActionEvidence> currentEvidence = new LinkedHashMap<>();
        for (RulebookActionAssignment assignment : assignments) {
            LinkedHashMap<String, RulebookActionEvidence> combined = new LinkedHashMap<>(currentEvidence);
            assignment.evidenceIds().forEach(id -> {
                RulebookActionEvidence item = evidenceById.get(id);
                if (item == null) throw new IllegalArgumentException("assigned action references missing retrieved evidence");
                combined.putIfAbsent(id, item);
            });
            int chars = combined.values().stream().mapToInt(item -> item.text().length()).sum();
            if (!current.isEmpty() && (current.size() >= 5 || combined.size() > MAX_AGENT_EVIDENCE || chars > MAX_INITIAL_ASSIGNED_EVIDENCE_CHARS)) {
                batches.add(List.copyOf(current));
                current.clear(); currentEvidence.clear(); combined.clear();
                assignment.evidenceIds().forEach(id -> combined.putIfAbsent(id, evidenceById.get(id)));
                chars = combined.values().stream().mapToInt(item -> item.text().length()).sum();
            }
            if (combined.size() > MAX_AGENT_EVIDENCE || chars > MAX_INITIAL_ASSIGNED_EVIDENCE_CHARS)
                throw new IllegalArgumentException("one assigned action exceeds the typed extraction input limit");
            current.add(assignment); currentEvidence.clear(); currentEvidence.putAll(combined);
        }
        if (!current.isEmpty()) batches.add(List.copyOf(current));
        return List.copyOf(batches);
    }

    private static AssignmentEvidenceMetrics assignmentEvidenceMetrics(RulebookActionAssignment assignment,
            Map<String, RulebookActionEvidence> evidenceById, Set<String> sharedEvidenceIds) {
        int evidenceCount = 0;
        int inputChars = 0;
        int sharedEvidenceCount = 0;
        int sharedInputChars = 0;
        for (String id : assignment.evidenceIds()) {
            RulebookActionEvidence evidence = evidenceById.get(id);
            if (evidence == null) throw new IllegalArgumentException("assigned action references missing retrieved evidence");
            evidenceCount++;
            inputChars += evidence.text().length();
            if (sharedEvidenceIds.contains(id)) {
                sharedEvidenceCount++;
                sharedInputChars += evidence.text().length();
            }
        }
        return new AssignmentEvidenceMetrics(evidenceCount, inputChars, sharedEvidenceCount, sharedInputChars,
                evidenceCount - sharedEvidenceCount, inputChars - sharedInputChars);
    }

    private static void validateEvidence(List<RulebookActionEvidence> evidence, List<RulebookActionDefinition> definitions) {
        Map<String, RulebookActionEvidence> allowed = evidence.stream().collect(java.util.stream.Collectors.toMap(
                RulebookActionEvidence::id, item -> item, (first, ignored) -> first, LinkedHashMap::new));
        for (RulebookActionDefinition definition : definitions) {
            RulebookActionEvidence cited = allowed.get(definition.sourceChunkId());
            if (cited == null) throw new IllegalArgumentException("definition cites evidence outside the retrieved source set");
            if (!cited.text().contains(definition.sourceEvidence()))
                throw new IllegalArgumentException("definition quote does not match its cited evidence");
            if (!cited.locator().equals(definition.sourceLocator()))
                throw new IllegalArgumentException("definition locator does not match its cited evidence");
        }
    }

    // shortcut: cached follow-up sources are not hydrated; add scoped chunk lookup if repeated retries make this costly.
    private static List<String> invalidCheckpointDefinitionIds(List<RulebookActionDefinitionResult> results,
            List<RulebookActionEvidence> assignedEvidence) {
        Map<String, RulebookActionEvidence> available = assignedEvidence.stream().collect(java.util.stream.Collectors.toMap(
                RulebookActionEvidence::id, item -> item, (first, ignored) -> first, LinkedHashMap::new));
        return results.stream().filter(result -> result.definition() != null)
                .filter(result -> {
                    RulebookActionDefinition definition = result.definition();
                    RulebookActionEvidence cited = available.get(definition.sourceChunkId());
                    return cited == null || !cited.text().contains(definition.sourceEvidence())
                            || !cited.locator().equals(definition.sourceLocator());
                }).map(RulebookActionDefinitionResult::candidateId).toList();
    }

    private static void validateCandidates(List<RulebookActionEvidence> evidence, List<RulebookActionCandidate> candidates) {
        Map<String, RulebookActionEvidence> allowed = evidence.stream().collect(java.util.stream.Collectors.toMap(
                RulebookActionEvidence::id, item -> item, (first, ignored) -> first, LinkedHashMap::new));
        for (RulebookActionCandidate candidate : candidates) {
            RulebookActionEvidence cited = allowed.get(candidate.sourceChunkId());
            if (cited == null || !cited.locator().equals(candidate.sourceLocator()) || !cited.text().contains(candidate.sourceEvidence()))
                throw new IllegalArgumentException("discovered action name does not match a retrieved source anchor");
        }
    }

    private static String fingerprint(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void log(String pattern, Object... args) {
        if (Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))))
            LOGGER.info(pattern, args);
    }

    private record Query(String id, String text, int candidateLimit) { }
    private record TargetedFollowup(RulebookActionAssignment assignment, RulebookActionDefinition known,
            List<String> requestedFields, EvidenceSearchRequest request, List<RulebookActionEvidence> evidence,
            int addedEvidenceCount, long lookupStarted) {
        private TargetedFollowup(RulebookActionAssignment assignment, RulebookActionDefinition known,
                List<String> requestedFields, EvidenceSearchRequest request, long lookupStarted) {
            this(assignment, known, requestedFields, request, List.of(), 0, lookupStarted);
        }
    }
    private record AssignmentEvidenceMetrics(int evidenceCount, int inputChars, int sharedEvidenceCount, int sharedInputChars,
            int candidateEvidenceCount, int candidateInputChars) { }
    private static final class TargetedInputBudgetExceededException extends IllegalArgumentException {
        private TargetedInputBudgetExceededException(String message) { super(message); }
    }
}
