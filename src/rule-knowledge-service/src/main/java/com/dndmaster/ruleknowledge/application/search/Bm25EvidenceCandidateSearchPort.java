package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;

public interface Bm25EvidenceCandidateSearchPort {
    List<EvidenceCandidate> search(
            OwnerPlayerId ownerPlayerId, List<AuthorizedDocumentScope> scope, String query, int limit);
}
