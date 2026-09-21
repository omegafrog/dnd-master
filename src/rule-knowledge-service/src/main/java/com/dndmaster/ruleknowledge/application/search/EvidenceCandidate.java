package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.Objects;

/** A scoped chunk candidate with source information preserved for later fusion. */
public record EvidenceCandidate(
        KnowledgeDocumentId documentId,
        ChunkId chunkId,
        long extractionVersion,
        DocumentType documentType,
        String locator,
        String excerpt,
        SourceProvenance provenance,
        Integer denseRank,
        Integer bm25Rank,
        double rrfScore) {
    public EvidenceCandidate {
        Objects.requireNonNull(documentId, "document id must not be null");
        Objects.requireNonNull(chunkId, "chunk id must not be null");
        Objects.requireNonNull(documentType, "document type must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (locator == null || locator.isBlank()) throw new IllegalArgumentException("locator must not be blank");
        if (excerpt == null || excerpt.isBlank()) throw new IllegalArgumentException("excerpt must not be blank");
        if (denseRank != null && denseRank <= 0) throw new IllegalArgumentException("dense rank must be positive");
        if (bm25Rank != null && bm25Rank <= 0) throw new IllegalArgumentException("BM25 rank must be positive");
        if (!Double.isFinite(rrfScore) || rrfScore < 0) throw new IllegalArgumentException("RRF score must be finite and non-negative");
    }
}
