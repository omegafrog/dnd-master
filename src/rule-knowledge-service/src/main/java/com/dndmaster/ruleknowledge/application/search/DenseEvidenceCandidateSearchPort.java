package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;

/** Retrieves dense candidates only from the caller-authorized published document scope. */
public interface DenseEvidenceCandidateSearchPort {
    List<EvidenceCandidate> search(
            OwnerPlayerId ownerPlayerId, List<AuthorizedDocumentScope> scope, String query, int limit);
}
