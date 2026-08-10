package com.dndmaster.ruleknowledge.application.evidence;

import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjection;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import com.dndmaster.ruleknowledge.domain.evidence.EvidenceUnit;

public interface EvidenceUnitRepository {
    void replace(RulebookId documentId, long extractionVersion, RuleEvidenceProjection projection);

    default void replace(RulebookId documentId, long extractionVersion, RuleEvidenceProjection projection,
            Map<UUID, float[]> embeddings) {
        replace(documentId, extractionVersion, projection);
    }

    RuleEvidenceProjection load(RulebookId documentId, long extractionVersion);

    default List<EvidenceUnit> search(RulebookId documentId, long extractionVersion, float[] embedding,
            String query, int limit) {
        return load(documentId, extractionVersion).units().stream()
                .filter(EvidenceUnit::canExposeToPlayer).limit(limit).toList();
    }
}
