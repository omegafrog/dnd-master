package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionApplicationService;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionRequest;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Converts the selected player-action evidence in a confirmed runtime scope to the existing runtime form. */
public final class RuntimePlayerActionEvidenceAcquirer {
    private final EvidenceAcquisitionApplicationService acquisitionService;

    public RuntimePlayerActionEvidenceAcquirer(EvidenceAcquisitionApplicationService acquisitionService) {
        this.acquisitionService = Objects.requireNonNull(acquisitionService, "evidence acquisition service must not be null");
    }

    public List<RuntimeEvidence> acquire(EvidenceSearchScope scope, String action) {
        return acquire(scope, action, "");
    }

    public List<RuntimeEvidence> acquire(EvidenceSearchScope scope, String action, String currentSituation) {
        Objects.requireNonNull(scope, "evidence search scope must not be null");
        String query = turnRulesQuery(action, currentSituation, scope.stageKey());
        var result = acquisitionService.acquire(new EvidenceAcquisitionRequest("PLAYER_ACTION", query, List.of(), scope, 1));
        Map<UUID, EvidenceSearchScope.Document> documents = new LinkedHashMap<>();
        scope.documents().forEach(document -> documents.put(document.id(), document));
        Map<UUID, EvidenceCandidate> candidates = new LinkedHashMap<>();
        result.candidates().forEach(candidate -> candidates.put(candidate.id(), candidate));
        return result.decision().selectedEvidenceIds().stream()
                .map(candidates::get)
                .filter(Objects::nonNull)
                .map(candidate -> runtimeEvidence(candidate, documents))
                .toList();
    }

    static String turnRulesQuery(String action, String situation, String stageKey) {
        StringBuilder query = new StringBuilder("플레이어 행동: ").append(required(action, "action"))
                .append("\n현재 장면: ").append(stageKey);
        if (situation != null && !situation.isBlank()) query.append("\n현재 상황: ").append(situation.trim());
        query.append("\n이 행동과 현재 상황을 해결하는 데 적용되는 기본 규칙 또는 추가 규칙을 검색하세요. ")
                .append("능력 판정이나 기술 판정(예: 지각)이 필요한 행동인지 판단하는 데 관련된 규칙, ")
                .append("판정 조건·굴림 방식·난이도 결정 절차를 찾으세요. 판정이 필요하지 않을 수도 있습니다. ")
                .append("행동에 직접 관련된 이동·대화·전투 절차도 포함하고, 원문 근거를 반환하세요.");
        return query.toString();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static RuntimeEvidence runtimeEvidence(EvidenceCandidate candidate,
                                                    Map<UUID, EvidenceSearchScope.Document> documents) {
        UUID documentId;
        try {
            documentId = UUID.fromString(candidate.documentId());
        } catch (IllegalArgumentException invalidId) {
            throw new IllegalArgumentException("selected evidence document id is invalid", invalidId);
        }
        EvidenceSearchScope.Document document = documents.get(documentId);
        if (document == null || !document.type().equals(candidate.documentType())) {
            throw new IllegalArgumentException("selected evidence is outside the confirmed runtime document scope");
        }
        RuntimeEvidenceType type = RuntimeEvidenceType.valueOf(document.type());
        return new RuntimeEvidence(type, new KnowledgeDocumentId(documentId), document.extractionVersion(),
                candidate.locator(), candidate.excerpt());
    }
}
