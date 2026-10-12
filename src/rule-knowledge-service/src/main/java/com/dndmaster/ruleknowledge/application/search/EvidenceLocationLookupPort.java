package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.List;
import java.util.Map;

/** Retrieves published excerpts from the page identified by each exact source locator, without embedding a query. */
public interface EvidenceLocationLookupPort {
    Map<String, List<EvidenceCandidate>> findByLocators(OwnerPlayerId ownerPlayerId,
            List<AuthorizedDocumentScope> scope, List<String> locators, int limit);
}
