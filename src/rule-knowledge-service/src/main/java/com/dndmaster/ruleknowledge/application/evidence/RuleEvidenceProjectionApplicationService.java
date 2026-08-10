package com.dndmaster.ruleknowledge.application.evidence;

import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjection;
import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjector;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;

public final class RuleEvidenceProjectionApplicationService {
    private final RuleEvidenceProjector projector;
    private final EvidenceUnitRepository repository;

    public RuleEvidenceProjectionApplicationService(RuleEvidenceProjector projector, EvidenceUnitRepository repository) {
        this.projector = Objects.requireNonNull(projector, "projector must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public RuleEvidenceProjection projectAndStore(RulebookId documentId, long extractionVersion, DocumentNode root) {
        RuleEvidenceProjection projection = projector.project(documentId, extractionVersion, root);
        repository.replace(documentId, extractionVersion, projection);
        return projection;
    }
}
