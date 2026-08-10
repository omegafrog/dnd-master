package com.dndmaster.ruleknowledge.application.evidence;

import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjection;
import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjector;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalDocumentTree;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

public final class RuleEvidenceProjectionApplicationService {
    private final RuleEvidenceProjector projector;
    private final EvidenceUnitRepository repository;
    private final EmbeddingPort embeddingPort;
    private final String embeddingModel;
    private final int embeddingDimension;

    public RuleEvidenceProjectionApplicationService(RuleEvidenceProjector projector, EvidenceUnitRepository repository) {
        this(projector, repository, null, "", 0);
    }

    public RuleEvidenceProjectionApplicationService(RuleEvidenceProjector projector, EvidenceUnitRepository repository,
            EmbeddingPort embeddingPort, String embeddingModel, int embeddingDimension) {
        this.projector = Objects.requireNonNull(projector, "projector must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.embeddingPort = embeddingPort;
        this.embeddingModel = embeddingModel == null ? "" : embeddingModel;
        this.embeddingDimension = embeddingDimension;
    }

    public RuleEvidenceProjection projectAndStore(RulebookId documentId, long extractionVersion, DocumentNode root) {
        RuleEvidenceProjection projection = projector.project(documentId, extractionVersion, root);
        Map<UUID, float[]> embeddings = new HashMap<>();
        if (embeddingPort != null && embeddingDimension > 0) {
            for (var unit : projection.units()) {
                RulebookChunk chunk = new RulebookChunk(documentId, new ChunkId(unit.id()), 0,
                        new ExtractedContentRange(0, unit.content().length()), unit.content(), null, null);
                embeddings.put(unit.id(), embeddingPort.embed(List.of(chunk), embeddingModel, embeddingDimension).getFirst().vector());
            }
        }
        repository.replace(documentId, extractionVersion, projection, embeddings);
        return projection;
    }

    public RuleEvidenceProjection projectCanonicalAndStore(RulebookId documentId, long extractionVersion,
                                                            NormalizedDocument document, CanonicalDocumentTree tree) {
        RuleEvidenceProjection projection = projector.projectCanonical(documentId, extractionVersion, document, tree);
        repository.replace(documentId, extractionVersion, projection, Map.of());
        return projection;
    }
}
