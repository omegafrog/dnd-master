package com.dndmaster.ruleknowledge.application.publication;

import com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding;
import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RagExtractionPublicationService {
    private static final int EMBEDDING_BATCH_SIZE = 32;
    private static final int MAX_CHUNK_CHARACTERS = 6000;
    private static final Logger LOGGER = Logger.getLogger(RagExtractionPublicationService.class.getName());
    private final RagExtractionPublicationRepository repository;
    private final EmbeddingPort embeddingPort;
    private final String embeddingModel;
    private final int embeddingDimension;

    public RagExtractionPublicationService(
            RagExtractionPublicationRepository repository,
            EmbeddingPort embeddingPort,
            String embeddingModel,
            int embeddingDimension) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embedding port must not be null");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embedding model must not be null");
        if (embeddingDimension <= 0) throw new IllegalArgumentException("embedding dimension must be positive");
        this.embeddingDimension = embeddingDimension;
    }

    public RagExtractionVersion publish(RagExtractionPublicationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        final RagExtractionPublicationRequest effectiveRequest = request.withEmbeddingModel(embeddingModel);
        log("dev_rag_extraction_publication stage=start pageCount=" + effectiveRequest.pages().size()
                + " chunkCount=" + effectiveRequest.chunks().size() + " outcome=started");
        repository.beginCandidate(effectiveRequest);
        try {
            validatePages(effectiveRequest);
            log("dev_rag_extraction_publication stage=page_validation pageCount=" + effectiveRequest.pages().size()
                    + " excludedFormPageCount=" + effectiveRequest.pages().stream()
                        .filter(page -> "EXCLUDED_FORM".equals(page.status())).count()
                    + " unresolvedPageCount=" + effectiveRequest.pages().stream()
                        .filter(page -> "NEEDS_REVIEW".equals(page.status())).count()
                    + " chunkCount=" + effectiveRequest.chunks().size() + " outcome=accepted");
            List<RulebookChunk> embeddingInputs = effectiveRequest.chunks().stream()
                    .map(chunk -> toRulebookChunk(effectiveRequest.documentId(), effectiveRequest.extractionVersion(), chunk))
                    .toList();
            List<ChunkEmbedding> embeddings = new ArrayList<>(embeddingInputs.size());
            for (int start = 0; start < embeddingInputs.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + EMBEDDING_BATCH_SIZE, embeddingInputs.size());
                embeddings.addAll(embeddingPort.embed(
                        embeddingInputs.subList(start, end), embeddingModel, embeddingDimension));
            }
            if (embeddings == null || embeddings.size() != effectiveRequest.chunks().size()) {
                throw new IllegalStateException("embedding result count does not match chunks");
            }
            Map<ChunkId, ChunkEmbedding> embeddingsByChunkId = embeddings.stream()
                    .collect(Collectors.toMap(ChunkEmbedding::chunkId, Function.identity(), (first, second) -> {
                        throw new IllegalStateException("duplicate embedding result chunk id");
                    }));
            Set<ChunkId> seen = new HashSet<>();
            List<EmbeddedPublishedRagChunk> publishedChunks = effectiveRequest.chunks().stream().map(chunk -> {
                ChunkId id = ChunkId.fromPublicationValue(
                        effectiveRequest.documentId(), effectiveRequest.extractionVersion(), chunk.processorChunkId());
                if (!seen.add(id)) throw new IllegalStateException("duplicate processor chunk id");
                ChunkEmbedding embedding = embeddingsByChunkId.get(id);
                if (embedding == null) {
                    throw new IllegalStateException("embedding result references unknown chunk");
                }
                if (embedding.vector().length != embeddingDimension) throw new IllegalStateException("embedding dimension mismatch");
                return new EmbeddedPublishedRagChunk(chunk, embedding.vector());
            }).toList();
            if (embeddingsByChunkId.size() != seen.size()) {
                throw new IllegalStateException("embedding result references unknown chunk");
            }
            repository.publish(effectiveRequest, publishedChunks);
            log("dev_rag_extraction_publication stage=complete pageCount=" + effectiveRequest.pages().size()
                    + " excludedFormPageCount=" + effectiveRequest.pages().stream()
                        .filter(page -> "EXCLUDED_FORM".equals(page.status())).count()
                    + " unresolvedPageCount=" + effectiveRequest.pages().stream()
                        .filter(page -> "NEEDS_REVIEW".equals(page.status())).count()
                    + " chunkCount=" + publishedChunks.size() + " outcome=published");
            return new RagExtractionVersion(effectiveRequest.documentId(), effectiveRequest.extractionVersion(), ExtractionPublicationStatus.INDEXED);
        } catch (PublicationBlockedException exception) {
            log("dev_rag_extraction_publication stage=page_validation failureCode=PUBLICATION_BLOCKED"
                    + " expectedAllowedChunkSourceStatuses=VALIDATED,USABLE_WITH_WARNINGS actualPageCount=" + effectiveRequest.pages().size()
                    + " recoveryAction=retain_source_and_exclude_unusable_pages outcome=blocked");
            repository.fail(effectiveRequest, ExtractionPublicationStatus.NEEDS_REVIEW, "PUBLICATION_BLOCKED");
            throw exception;
        } catch (RuntimeException exception) {
            log("dev_rag_extraction_publication stage=failed failureType=" + exception.getClass().getSimpleName()
                    + " recoveryAction=mark_candidate_failed outcome=failed");
            repository.fail(effectiveRequest, ExtractionPublicationStatus.FAILED, "VECTOR_PUBLICATION_FAILED");
            throw new PublicationFailedException(exception);
        }
    }

    private static void validatePages(RagExtractionPublicationRequest request) {
        Set<Integer> pageNumbers = new HashSet<>();
        Map<Integer, String> statusByPage = new java.util.HashMap<>();
        for (RagExtractionPage page : request.pages()) {
            if (!pageNumbers.add(page.pageNumber())) throw new PublicationBlockedException("duplicate extraction page");
            statusByPage.put(page.pageNumber(), page.status());
            if (!"VALIDATED".equals(page.status()) && !"USABLE_WITH_WARNINGS".equals(page.status())
                    && !"EXCLUDED_FORM".equals(page.status()) && !"NEEDS_REVIEW".equals(page.status()))
                throw new PublicationBlockedException("extraction version has an unsupported page status");
        }
        for (int page = 1; page <= pageNumbers.size(); page++) {
            if (!pageNumbers.contains(page)) throw new PublicationBlockedException("extraction page manifest is incomplete");
        }
        for (PublishedRagChunk chunk : request.chunks()) {
            if (chunk.content().codePointCount(0, chunk.content().length()) > MAX_CHUNK_CHARACTERS
                    || chunk.embeddingText().codePointCount(0, chunk.embeddingText().length()) > MAX_CHUNK_CHARACTERS) {
                throw new PublicationBlockedException("chunk exceeds maximum character length");
            }
            if (!pageNumbers.contains(chunk.provenance().pageNumber())) throw new PublicationBlockedException("chunk page is not in extraction manifest");
            if ("EXCLUDED_FORM".equals(statusByPage.get(chunk.provenance().pageNumber()))
                    || "NEEDS_REVIEW".equals(statusByPage.get(chunk.provenance().pageNumber()))
                    || chunk.provenance().sourceSpans().stream()
                        .anyMatch(span -> "EXCLUDED_FORM".equals(statusByPage.get(span.pageNumber()))
                                || "NEEDS_REVIEW".equals(statusByPage.get(span.pageNumber())))) {
                throw new PublicationBlockedException("chunk references a page without searchable text");
            }
        }
    }

    private static void log(String message) {
        if (Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")))) LOGGER.info(message);
    }

    private static RulebookChunk toRulebookChunk(com.dndmaster.ruleknowledge.domain.rulebook.RulebookId documentId,
            String extractionVersion, PublishedRagChunk chunk) {
        String section = String.join(" / ", chunk.provenance().sectionPath());
        return new RulebookChunk(
                documentId,
                ChunkId.fromPublicationValue(documentId, extractionVersion, chunk.processorChunkId()),
                chunk.sequence(),
                new ExtractedContentRange(0, chunk.embeddingText().length()),
                chunk.embeddingText(),
                chunk.provenance().sectionPath().isEmpty() ? null : chunk.provenance().sectionPath().getFirst(),
                section.isBlank() ? null : section);
    }

}
