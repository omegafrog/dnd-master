package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceCandidateSearchPort;
import com.dndmaster.adventure.evidence.EvidenceCandidateSearchRequest;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionRequest;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Retrieves one mixed-source dense result set for the isolated lookup agent. */
public final class RuntimePlayerActionEvidenceAcquirer {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RuntimePlayerActionEvidenceAcquirer.class);
    private static final int CANDIDATES_PER_SOURCE = 5;
    private final EvidenceCandidateSearchPort candidateSearch;

    public RuntimePlayerActionEvidenceAcquirer(EvidenceCandidateSearchPort candidateSearch) {
        this.candidateSearch = Objects.requireNonNull(candidateSearch, "candidate search must not be null");
    }

    public List<RuntimeEvidence> acquire(EvidenceSearchScope scope, String action) {
        return acquire(scope, action, "");
    }

    public List<RuntimeEvidence> acquire(EvidenceSearchScope scope, String action, String currentSituation) {
        Objects.requireNonNull(scope, "evidence search scope must not be null");
        String query = turnRulesQuery(action, currentSituation, scope.stageKey());
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_player_action_evidence stage=request sessionId={} scenarioPackageId={} stageKey={} actionIntent={} queryFingerprint={} queryChars={} documentScope={} activeLocators={} knownEvidenceIds={} additionalSearchLimit=0",
                    scope.sessionId(), scope.scenarioPackageId(), scope.stageKey(),
                    scope.actionIntent(),
                    com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(query), query.length(),
                    scope.documents().stream().map(document -> document.type() + ":" + document.id() + ":v" + document.extractionVersion()).toList(),
                    scope.activeLocators(), scope.knownEvidenceIds());
        }
        var acquisition = new EvidenceAcquisitionRequest("PLAYER_ACTION", query, List.of(), scope, 0);
        List<EvidenceCandidate> candidates = candidateSearch.search(new EvidenceCandidateSearchRequest(acquisition, query, 0));
        if (candidates == null || candidates.size() > 30
                || candidates.stream().map(EvidenceCandidate::id).distinct().count() != candidates.size()) {
            throw new IllegalStateException("runtime evidence search returned invalid candidates");
        }
        Map<UUID, EvidenceSearchScope.Document> documents = scope.documents().stream()
                .collect(Collectors.toMap(EvidenceSearchScope.Document::id, document -> document));
        for (EvidenceCandidate candidate : candidates) {
            UUID documentId;
            try { documentId = UUID.fromString(candidate.documentId()); }
            catch (IllegalArgumentException invalidId) { throw new IllegalStateException("runtime evidence document id is invalid", invalidId); }
            EvidenceSearchScope.Document document = documents.get(documentId);
            if (document == null || !document.type().equals(candidate.documentType())) {
                throw new IllegalStateException("runtime evidence candidate is outside the confirmed document scope");
            }
        }
        var seenEvidenceIds = new HashSet<String>(scope.knownEvidenceIds());
        List<EvidenceCandidate> freshCandidates = candidates.stream()
                .filter(candidate -> seenEvidenceIds.add(immutableEvidenceId(candidate, documents)))
                .toList();
        List<RuntimeEvidence> selected = freshCandidates.stream()
                .collect(Collectors.groupingBy(EvidenceCandidate::documentType, java.util.LinkedHashMap::new, Collectors.toList()))
                .values().stream().flatMap(values -> values.stream().limit(CANDIDATES_PER_SOURCE))
                .map(candidate -> runtimeEvidence(candidate, documents))
                .toList();
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_player_action_evidence stage=selected sessionId={} scenarioPackageId={} candidateCount={} selectedCount={} selected={} excludedKnownCount={}",
                    scope.sessionId(), scope.scenarioPackageId(), candidates.size(), selected.size(),
                    selected.stream().map(evidence -> evidence.evidenceType() + ":" + evidence.knowledgeDocumentId().value()
                            + ":v" + evidence.extractionVersion() + ":" + evidence.locator()).toList(),
                    candidates.size() - freshCandidates.size());
        }
        return selected;
    }

    static String turnRulesQuery(String action, String situation, String stageKey) {
        StringBuilder query = new StringBuilder("플레이어 행동: ").append(required(action, "action"))
                .append("\n현재 장면: ").append(stageKey);
        if (situation != null && !situation.isBlank()) query.append("\n현재 상황: ").append(situation.trim());
        query.append("\n이 행동과 현재 상황에 관련된 이야기 정보와 기본 규칙 또는 추가 규칙을 함께 검색하세요. ")
                .append("능력 판정이나 기술 판정(예: 지각)이 필요한 행동인지 판단하는 데 관련된 규칙, ")
                .append("판정 조건·굴림 방식·난이도 결정 절차를 찾으세요. 판정이 필요하지 않을 수도 있습니다. ")
                .append("행동에 직접 관련된 이동·대화·전투 절차도 포함하고, 원문 근거를 반환하세요.");
        return query.toString();
    }

    private static String immutableEvidenceId(EvidenceCandidate candidate,
            Map<UUID, EvidenceSearchScope.Document> documents) {
        UUID documentId = UUID.fromString(candidate.documentId());
        EvidenceSearchScope.Document document = documents.get(documentId);
        return "IMMUTABLE_DOCUMENT:" + candidate.documentType() + ":" + documentId + ":"
                + document.extractionVersion() + ":" + candidate.locator();
    }

    private static RuntimeEvidence runtimeEvidence(EvidenceCandidate candidate,
            Map<UUID, EvidenceSearchScope.Document> documents) {
        UUID documentId = UUID.fromString(candidate.documentId());
        EvidenceSearchScope.Document document = documents.get(documentId);
        if (document == null || !document.type().equals(candidate.documentType())) {
            throw new IllegalArgumentException("selected evidence is outside the confirmed runtime document scope");
        }
        RuntimeEvidenceType type = RuntimeEvidenceType.valueOf(document.type());
        return new RuntimeEvidence(type, new KnowledgeDocumentId(documentId), document.extractionVersion(),
                candidate.locator(), candidate.excerpt());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
