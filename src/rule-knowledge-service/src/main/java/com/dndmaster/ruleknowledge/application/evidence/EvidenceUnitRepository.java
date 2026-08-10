package com.dndmaster.ruleknowledge.application.evidence;

import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjection;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;

public interface EvidenceUnitRepository {
    void replace(RulebookId documentId, long extractionVersion, RuleEvidenceProjection projection);

    RuleEvidenceProjection load(RulebookId documentId, long extractionVersion);
}
