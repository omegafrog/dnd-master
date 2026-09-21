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
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** PostgreSQL vector retrieval over only the caller-authorized, indexed published chunk scope. */
public final class PostgreSQLDenseEvidenceCandidateSearchAdapter implements DenseEvidenceCandidateSearchPort {
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

        String vector = vectorLiteral(embedQuery(request.query(), authorizedScope.getFirst()));
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(searchSql(authorizedScope.size()))) {
            int parameter = 1;
            for (AuthorizedDocumentScope item : authorizedScope) {
                statement.setObject(parameter++, item.documentId().value(), Types.OTHER);
                statement.setLong(parameter++, item.extractionVersion());
                statement.setString(parameter++, item.documentType().name());
            }
            statement.setObject(parameter++, request.ownerPlayerId().value(), Types.OTHER);
            statement.setArray(parameter++, connection.createArrayOf("text", request.activeLocators().toArray(String[]::new)));
            statement.setString(parameter++, vector);
            statement.setInt(parameter, request.denseLimit());
            try (ResultSet rows = statement.executeQuery()) {
                List<EvidenceCandidate> candidates = new ArrayList<>();
                int rank = 1;
                while (rows.next()) {
                    candidates.add(new EvidenceCandidate(
                            new KnowledgeDocumentId(rows.getObject("document_id", UUID.class)),
                            new ChunkId(rows.getObject("chunk_id", UUID.class)),
                            rows.getLong("extraction_version"),
                            DocumentType.valueOf(rows.getString("document_type")),
                            rows.getString("original_locator"),
                            rows.getString("content"),
                            new SourceProvenance(rows.getInt("page_number"), textArray(rows, "section_path"),
                                    doubleArray(rows, "bbox"), rows.getString("table_cell"), rows.getString("original_locator")),
                            rank++, null, 0d));
                }
                return List.copyOf(candidates);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not search scoped dense evidence candidates", exception);
        }
    }

    private float[] embedQuery(String query, AuthorizedDocumentScope scope) {
        RulebookChunk queryChunk = new RulebookChunk(scope.documentId().asRulebookId(), new ChunkId(UUID.randomUUID()), 0,
                new ExtractedContentRange(0, query.length()), query, null, null);
        return embeddingPort.embed(List.of(queryChunk), embeddingModel, embeddingDimension).getFirst().vector();
    }

    private static String searchSql(int scopeSize) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(scopeSize, "(?, ?, ?)"));
        return """
                WITH authorized_scope(document_id, extraction_version, document_type) AS (VALUES %s)
                SELECT c.document_id, c.chunk_id,
                       CASE WHEN c.extraction_version ~ '^[0-9]+$' THEN c.extraction_version::bigint
                            ELSE GREATEST(r.version, 1) END AS extraction_version,
                       r.document_type, c.original_locator, c.content, c.page_number, c.section_path, c.bbox, c.table_cell
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
                 WHERE c.owner_player_id = ?
                   AND c.document_length > 0
                   AND EXISTS (SELECT 1 FROM chunk_term_frequency frequency WHERE frequency.chunk_id = c.chunk_id)
                 ORDER BY CASE WHEN c.original_locator = ANY (?) THEN 0 ELSE 1 END,
                          c.embedding <=> CAST(? AS vector), c.chunk_id
                 LIMIT ?
                """.formatted(placeholders);
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
