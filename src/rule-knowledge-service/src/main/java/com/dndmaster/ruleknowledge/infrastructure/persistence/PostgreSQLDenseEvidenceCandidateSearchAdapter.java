package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.indexing.EmbeddingPort;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.application.search.AuthorizedDocumentScope;
import com.dndmaster.ruleknowledge.application.search.DenseEvidenceCandidateSearchPort;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.index.ExtractedContentRange;
import com.dndmaster.ruleknowledge.domain.index.RulebookChunk;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** PostgreSQL vector retrieval over only the caller-authorized, indexed published chunk scope. */
public final class PostgreSQLDenseEvidenceCandidateSearchAdapter implements DenseEvidenceCandidateSearchPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgreSQLDenseEvidenceCandidateSearchAdapter.class);
    private static final int INDEXED_EMBEDDING_DIMENSION = 1024;
    private final DataSource dataSource;
    private final EmbeddingPort embeddingPort;
    private final String embeddingModel;
    private final int embeddingDimension;

    public PostgreSQLDenseEvidenceCandidateSearchAdapter(
            DataSource dataSource, EmbeddingPort embeddingPort, String embeddingModel, int embeddingDimension) {
        this.dataSource = Objects.requireNonNull(dataSource, "data source must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embedding port must not be null");
        if (embeddingModel == null || embeddingModel.isBlank()) throw new IllegalArgumentException("embedding model must not be blank");
        if (embeddingDimension < 1) throw new IllegalArgumentException("embedding dimension must be positive");
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
    }

    @Override
    public List<EvidenceCandidate> search(EvidenceSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var authorizedScope = request.scope();
        return search(request, vectorLiteral(embedQueries(List.of(request)).getFirst()));
    }

    @Override
    public List<List<EvidenceCandidate>> searchBatch(List<EvidenceSearchRequest> requests) {
        requests = List.copyOf(Objects.requireNonNull(requests, "requests must not be null"));
        if (requests.isEmpty()) return List.of();
        List<String> vectors = embedQueries(requests).stream().map(PostgreSQLDenseEvidenceCandidateSearchAdapter::vectorLiteral).toList();
        List<List<EvidenceCandidate>> results = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) results.add(search(requests.get(index), vectors.get(index)));
        return List.copyOf(results);
    }

    private List<EvidenceCandidate> search(EvidenceSearchRequest request, String vector) {
        Objects.requireNonNull(request, "request must not be null");
        var authorizedScope = request.scope();
        long startedAt = System.nanoTime();
        boolean indexedDimension = embeddingDimension == INDEXED_EMBEDDING_DIMENSION;
        if (diagnosticsEnabled()) {
            LOGGER.info("dev_dense_evidence_search stage=started scopeCount={} activeLocatorCount={} excludedChunkCount={} minimumSimilarity={} embeddingModel={} embeddingDimension={} indexPath={} iterativeScan={} limit={} outcome=started",
                    authorizedScope.size(), request.activeLocators().size(), request.excludedChunkIds().size(),
                    request.minimumDenseSimilarity(),
                    embeddingModel, embeddingDimension,
                    indexedDimension ? "hnsw_1024" : "unindexed_dimension_fallback",
                    indexedDimension ? "strict_bounded" : "not_applicable", request.denseLimit());
        }
        String sql = searchSql(authorizedScope.size(), indexedDimension, !request.activeLocators().isEmpty());
        if (diagnosticsEnabled()) LOGGER.info("dev_dense_evidence_search stage=query_ready placeholderCount={} outcome=started",
                sql.chars().filter(character -> character == '?').count());
        try (Connection connection = dataSource.getConnection()) {
            boolean ownsTransaction = indexedDimension && connection.getAutoCommit();
            if (ownsTransaction) connection.setAutoCommit(false);
            Throwable primaryFailure = null;
            try {
                if (indexedDimension && ownsTransaction) {
                    try (Statement setup = connection.createStatement()) {
                        setup.execute("SET LOCAL hnsw.iterative_scan = strict_order");
                    }
                }
                if (indexedDimension && !ownsTransaction && diagnosticsEnabled()) {
                    LOGGER.info("dev_dense_evidence_search stage=iterative_scan_skipped connectionAutoCommit=false recoveryAction=keep_existing_transaction_settings outcome=partial");
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (AuthorizedDocumentScope item : authorizedScope) {
                statement.setObject(parameter++, item.documentId().value(), Types.OTHER);
                statement.setLong(parameter++, item.extractionVersion());
                statement.setString(parameter++, item.documentType().name());
                statement.setObject(parameter++, (item.documentOwner() != null ? item.documentOwner() : request.ownerPlayerId()).value(), Types.OTHER);
            }
            if (request.activeLocators().isEmpty()) {
                statement.setString(parameter++, vector);
                statement.setString(parameter++, embeddingModel);
                if (!indexedDimension) statement.setInt(parameter++, embeddingDimension);
                statement.setArray(parameter++, connection.createArrayOf("uuid", request.excludedChunkIds().toArray(java.util.UUID[]::new)));
                statement.setString(parameter++, vector);
                statement.setInt(parameter, request.denseLimit());
            } else {
                Array excluded = connection.createArrayOf("uuid", request.excludedChunkIds().toArray(java.util.UUID[]::new));
                Array activeLocators = connection.createArrayOf("text", request.activeLocators().toArray(String[]::new));
                for (int branch = 0; branch < 2; branch++) {
                    statement.setString(parameter++, vector);
                    statement.setString(parameter++, embeddingModel);
                    if (!indexedDimension) statement.setInt(parameter++, embeddingDimension);
                    statement.setArray(parameter++, excluded);
                    statement.setArray(parameter++, activeLocators);
                    statement.setString(parameter++, vector);
                    statement.setInt(parameter++, request.denseLimit());
                }
                statement.setInt(parameter, request.denseLimit());
            }
                    try (ResultSet rows = statement.executeQuery()) {
                        List<EvidenceCandidate> candidates = new ArrayList<>();
                        int rank = 1;
                        while (rows.next()) {
                            double similarity = 1d - rows.getDouble("distance");
                            candidates.add(new EvidenceCandidate(
                                    new KnowledgeDocumentId(rows.getObject("document_id", UUID.class)),
                                    new ChunkId(rows.getObject("chunk_id", UUID.class)),
                                    rows.getLong("extraction_version"),
                                    DocumentType.valueOf(rows.getString("document_type")),
                                    rows.getString("original_locator"),
                                    rows.getString("content"),
                                    new SourceProvenance(rows.getInt("page_number"), textArray(rows, "section_path"),
                                            doubleArray(rows, "bbox"), rows.getString("table_cell"), rows.getString("original_locator"),
                                            SourceProvenance.sourceSpansFromJson(rows.getString("source_spans"))),
                                    rank++, similarity));
                        }
                        if (ownsTransaction) connection.commit();
                        if (diagnosticsEnabled()) {
                            LOGGER.info("dev_dense_evidence_search stage=completed expectedCandidateCount=0..{} actualCandidateCount={} minimumSimilarity={} embeddingModel={} embeddingDimension={} indexPath={} iterativeScan={} elapsedMs={} outcome=completed",
                                    request.denseLimit(), candidates.size(), request.minimumDenseSimilarity(), embeddingModel, embeddingDimension,
                                    indexedDimension ? "hnsw_1024" : "unindexed_dimension_fallback",
                                    indexedDimension && ownsTransaction ? "strict_bounded" : "not_applied",
                                    (System.nanoTime() - startedAt) / 1_000_000);
                        }
                        List<EvidenceCandidate> selected = candidates.stream()
                                .filter(candidate -> request.minimumDenseSimilarity() == null
                                        || candidate.denseSimilarity() >= request.minimumDenseSimilarity())
                                .toList();
                        if (diagnosticsEnabled() && request.minimumDenseSimilarity() != null) {
                            double[] scoreRange = candidates.stream().mapToDouble(candidate -> candidate.denseSimilarity()).toArray();
                            Double minimumReturnedScore = scoreRange.length == 0 ? null : java.util.Arrays.stream(scoreRange).min().orElseThrow();
                            Double maximumReturnedScore = scoreRange.length == 0 ? null : java.util.Arrays.stream(scoreRange).max().orElseThrow();
                            LOGGER.info("dev_dense_evidence_search stage=minimum_similarity_filter threshold={} expectedCandidateCount=0..{} inputCandidateCount={} returnedSimilarityMin={} returnedSimilarityMax={} acceptedCandidateCount={} filteredCandidateCount={} outcome=completed",
                                    request.minimumDenseSimilarity(), request.denseLimit(), candidates.size(), minimumReturnedScore,
                                    maximumReturnedScore, selected.size(), candidates.size() - selected.size());
                        }
                        return selected;
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                primaryFailure = exception;
                if (ownsTransaction) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                }
                throw exception;
            } finally {
                if (ownsTransaction) {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException resetFailure) {
                        if (primaryFailure != null) primaryFailure.addSuppressed(resetFailure);
                        else throw resetFailure;
                    }
                }
            }
        } catch (SQLException exception) {
            if (diagnosticsEnabled()) {
                LOGGER.info("dev_dense_evidence_search stage=failed scopeCount={} activeLocatorCount={} excludedChunkCount={} embeddingModel={} embeddingDimension={} failureClass={} sqlState={} elapsedMs={} recoveryAction=propagate_search_failure outcome=failed",
                        authorizedScope.size(), request.activeLocators().size(), request.excludedChunkIds().size(),
                        embeddingModel, embeddingDimension, exception.getClass().getSimpleName(), exception.getSQLState(),
                        (System.nanoTime() - startedAt) / 1_000_000);
            }
            throw new RuleVectorPersistenceException("could not search scoped dense evidence candidates", exception);
        }
    }

    private List<float[]> embedQueries(List<EvidenceSearchRequest> requests) {
        List<RulebookChunk> chunks = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            EvidenceSearchRequest request = requests.get(index);
            String query = request.query();
            chunks.add(new RulebookChunk(request.scope().getFirst().documentId().asRulebookId(),
                    new ChunkId(UUID.nameUUIDFromBytes(("preparation-query:" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                    index, new ExtractedContentRange(0, query.length()), query, null, null));
        }
        List<com.dndmaster.ruleknowledge.application.indexing.ChunkEmbedding> embeddings =
                embeddingPort.embed(chunks, embeddingModel, embeddingDimension);
        if (embeddings.size() != requests.size()) throw new IllegalStateException("query embedding count does not match request count");
        List<float[]> vectors = new ArrayList<>();
        for (int index = 0; index < embeddings.size(); index++) {
            if (!chunks.get(index).chunkId().equals(embeddings.get(index).chunkId())) {
                throw new IllegalStateException("query embedding order does not match request order");
            }
            vectors.add(embeddings.get(index).vector());
        }
        return List.copyOf(vectors);
    }

    private static String searchSql(int scopeSize, boolean indexedDimension, boolean prioritizeActiveLocators) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(scopeSize, "(?, ?, ?, ?)"));
        String embedding = indexedDimension ? "c.embedding::vector(1024)" : "c.embedding";
        String queryVector = indexedDimension ? "CAST(? AS vector(1024))" : "CAST(? AS vector)";
        String columns = """
                c.document_id, c.chunk_id,
                       CASE WHEN c.extraction_version ~ '^[0-9]+$' THEN c.extraction_version::bigint
                            ELSE GREATEST(r.version, 1) END AS extraction_version,
                       r.document_type, c.original_locator, c.content, c.page_number, c.section_path, c.bbox, c.table_cell, c.source_spans,
                       %s <=> %s AS distance
                """.formatted(embedding, queryVector);
        String joins = """
                  FROM published_rag_chunk c
                  JOIN rulebook_registration r ON r.rulebook_id = c.document_id
                     AND r.owner_player_id = c.owner_player_id
                     AND r.published_extraction_version = c.extraction_version
                  JOIN rag_extraction_version v ON v.document_id = c.document_id
                     AND v.extraction_version = c.extraction_version AND v.status = 'INDEXED'
                  JOIN authorized_scope scope ON scope.document_id = c.document_id
                     AND scope.extraction_version = CASE WHEN c.extraction_version ~ '^[0-9]+$'
                         THEN c.extraction_version::bigint ELSE GREATEST(r.version, 1) END
                     AND scope.document_type = r.document_type
                     AND scope.owner_player_id = c.owner_player_id
                 WHERE c.embedding_model = ? AND c.embedding_dimension = %s
                   AND NOT (c.chunk_id = ANY (?))
                """.formatted(indexedDimension ? Integer.toString(INDEXED_EMBEDDING_DIMENSION) : "?");
        if (!prioritizeActiveLocators) {
            return """
                    WITH authorized_scope(document_id, extraction_version, document_type, owner_player_id) AS (VALUES %s),
                    nearest_candidates AS MATERIALIZED (
                        SELECT %s
                        %s
                        ORDER BY %s <=> %s
                        LIMIT ?
                    )
                    SELECT document_id, chunk_id, extraction_version, document_type, original_locator,
                           content, page_number, section_path, bbox, table_cell, source_spans, distance
                      FROM nearest_candidates
                    ORDER BY distance, chunk_id
                    """.formatted(placeholders, columns, joins, embedding, queryVector);
        }
        return """
                WITH authorized_scope(document_id, extraction_version, document_type, owner_player_id) AS (VALUES %s),
                active_candidates AS MATERIALIZED (
                    SELECT %s, true AS active_locator
                    %s AND c.original_locator = ANY (?)
                    ORDER BY %s <=> %s
                    LIMIT ?
                ), other_candidates AS MATERIALIZED (
                    SELECT %s, false AS active_locator
                    %s AND NOT (c.original_locator = ANY (?))
                    ORDER BY %s <=> %s
                    LIMIT ?
                ), candidates AS (
                    SELECT * FROM active_candidates UNION ALL SELECT * FROM other_candidates
                )
                SELECT document_id, chunk_id, extraction_version, document_type, original_locator, content,
                       page_number, section_path, bbox, table_cell, source_spans, distance
                  FROM candidates
                 ORDER BY active_locator DESC, distance, chunk_id
                 LIMIT ?
                """.formatted(placeholders, columns, joins, embedding, queryVector,
                columns, joins, embedding, queryVector);
    }

    private static boolean diagnosticsEnabled() {
        return Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")));
    }

    private static List<String> textArray(ResultSet rows, String column) throws SQLException {
        Array array = rows.getArray(column);
        if (array == null || array.getArray() == null) return List.of();
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }

    private static List<Double> doubleArray(ResultSet rows, String column) throws SQLException {
        Array array = rows.getArray(column);
        if (array == null || array.getArray() == null) return List.of();
        return Arrays.stream((Object[]) array.getArray()).map(value -> ((Number) value).doubleValue()).toList();
    }

    private static String vectorLiteral(float[] values) {
        Objects.requireNonNull(values, "embedding must not be null");
        if (values.length == 0) throw new IllegalArgumentException("embedding must not be empty");
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (!Float.isFinite(values[index])) throw new IllegalArgumentException("embedding values must be finite");
            if (index > 0) result.append(',');
            result.append(Float.toString(values[index]));
        }
        return result.append(']').toString();
    }
}
