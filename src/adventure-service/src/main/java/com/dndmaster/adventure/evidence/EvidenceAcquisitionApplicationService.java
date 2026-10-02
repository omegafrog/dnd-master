package com.dndmaster.adventure.evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Owns retries, candidate accumulation, validation, and the two additional-search limit. */
public final class EvidenceAcquisitionApplicationService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(EvidenceAcquisitionApplicationService.class);
    private final EvidenceCandidateSearchPort search; private final EvidenceRerankerPort reranker; private final EvidenceSufficiencyJudgePort judge;
    public EvidenceAcquisitionApplicationService(EvidenceCandidateSearchPort search, EvidenceRerankerPort reranker, EvidenceSufficiencyJudgePort judge) { this.search=Objects.requireNonNull(search); this.reranker=Objects.requireNonNull(reranker); this.judge=Objects.requireNonNull(judge); }
    public EvidenceAcquisitionResult acquire(EvidenceAcquisitionRequest request) {
        LinkedHashMap<UUID,EvidenceCandidate> pool=new LinkedHashMap<>(); List<UUID> pinned=new ArrayList<>(request.pinnedEvidenceIds());
        String searchQuery = request.query();
        for (int additional = 0;; additional++) {
            String queryForSearch = searchQuery;
            int additionalSearches = additional;
            List<EvidenceCandidate> found = retry(() -> search.search(
                    new EvidenceCandidateSearchRequest(request, queryForSearch, additionalSearches)));
            validateSearchCandidates(found);
            LOGGER.info("evidence_search_decision session={} package={} stage={} policy={} query={} additionalSearch={} candidateCount={} candidates={}",
                    sessionId(request), packageId(request), stageKey(request), request.policyId(), queryForSearch,
                    additionalSearches, found.size(), candidateAudit(found));
            found.forEach(candidate -> pool.putIfAbsent(candidate.id(), candidate));
            List<EvidenceCandidate> reranked=rerank(request, List.copyOf(pool.values()), pinned);
            List<UUID> pinnedForJudge = List.copyOf(pinned);
            SufficiencyDecision decision=retry(() -> judge.judge(new EvidenceSufficiencyRequest(request.policyId(),request.query(),reranked,pinnedForJudge,additionalSearches,soloPlayerId(request))));
            validateDecision(decision, reranked, pinned);
            LOGGER.info("evidence_sufficiency_decision session={} package={} stage={} policy={} additionalSearch={} rerankedCandidates={} sufficient={} selected={} reasons={} missing={}",
                    sessionId(request), packageId(request), stageKey(request), request.policyId(), additionalSearches,
                    candidateAudit(reranked), decision.sufficient(), decision.selectedEvidenceIds(),
                    decision.selectionReasons(), decision.missing());
            if(decision.sufficient() || additional==2) return new EvidenceAcquisitionResult(reranked,decision,additional);
            pinned = new ArrayList<>(new LinkedHashSet<>(decision.selectedEvidenceIds()));
            searchQuery = decision.missing();
        }
    }
    private List<EvidenceCandidate> rerank(EvidenceAcquisitionRequest request,List<EvidenceCandidate> pool,List<UUID> pinned) {
        List<UUID> ids=retry(() -> reranker.rerank(new EvidenceRerankRequest(request.policyId(),request.query(),pool,soloPlayerId(request))));
        if(ids.size()>30 || ids.size()!=new LinkedHashSet<>(ids).size()) throw new EvidenceAcquisitionContractException("reranker returned invalid candidate identifiers");
        LinkedHashMap<UUID,EvidenceCandidate> available=new LinkedHashMap<>(); pool.forEach(c -> available.put(c.id(),c));
        if(!available.keySet().containsAll(ids) || !available.keySet().containsAll(pinned)) throw new EvidenceAcquisitionContractException("model returned candidate outside supplied scope");
        List<EvidenceCandidate> selected=new ArrayList<>(); for(UUID id:pinned) selected.add(available.get(id)); for(UUID id:ids) if(selected.size()<30&&!pinned.contains(id)) selected.add(available.get(id));
        LOGGER.info("evidence_reranker_decision session={} package={} stage={} policy={} query={} pool={} modelOrder={} pinned={} finalOrder={}",
                sessionId(request), packageId(request), stageKey(request), request.policyId(), request.query(),
                candidateAudit(pool), ids, pinned, candidateAudit(selected));
        return List.copyOf(selected);
    }
    private static List<String> candidateAudit(List<EvidenceCandidate> candidates) {
        return candidates.stream().map(candidate -> {
            String preview = "RULEBOOK".equals(candidate.documentType())
                    ? candidate.excerpt().replaceAll("\\s+", " ").substring(0, Math.min(220, candidate.excerpt().replaceAll("\\s+", " ").length()))
                    : "<숨김 자료 본문은 로그에서 제외>";
            return candidate.id() + "|" + candidate.documentType() + "|" + candidate.documentId() + "|"
                    + candidate.locator() + "|" + preview;
        }).toList();
    }
    private static String sessionId(EvidenceAcquisitionRequest request) {
        return request.searchScope() == null ? "none" : request.searchScope().sessionId().toString();
    }
    private static String packageId(EvidenceAcquisitionRequest request) {
        return request.searchScope() == null ? "none" : request.searchScope().scenarioPackageId().toString();
    }
    private static String stageKey(EvidenceAcquisitionRequest request) {
        return request.searchScope() == null ? "none" : request.searchScope().stageKey();
    }
    private static void validateDecision(SufficiencyDecision decision,List<EvidenceCandidate> candidates,List<UUID> pinned) {
        LinkedHashSet<UUID> allowed=new LinkedHashSet<>(); candidates.forEach(c->allowed.add(c.id())); if(!allowed.containsAll(decision.selectedEvidenceIds())||!decision.selectedEvidenceIds().containsAll(pinned)) throw new EvidenceAcquisitionContractException("judge selected evidence outside supplied candidates or omitted pinned evidence");
    }
    private static void validateSearchCandidates(List<EvidenceCandidate> candidates) {
        if (candidates == null || candidates.size() > 60
                || candidates.stream().map(EvidenceCandidate::id).distinct().count() != candidates.size()) {
            throw new EvidenceAcquisitionContractException("search returned invalid candidates");
        }
    }
    private static UUID soloPlayerId(EvidenceAcquisitionRequest request) {
        return request.searchScope() == null ? null : request.searchScope().ownerId();
    }
    private static <T>T retry(Supplier<T> call) { try{return call.get();} catch(EvidenceAcquisitionTransientException first){return call.get();} }
}
