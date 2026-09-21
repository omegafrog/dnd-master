package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.application.search.AuthorizedDocumentScope;
import com.dndmaster.ruleknowledge.application.search.Bm25EvidenceCandidateSearchPort;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/** PostgreSQL BM25 retrieval over only the caller-authorized, published chunk scope. */
public final class PostgreSQLBm25EvidenceCandidateSearchAdapter implements Bm25EvidenceCandidateSearchPort {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_]+");
    private static final double K1 = 1.5d;
    private static final double B = .75d;
    private final DataSource dataSource;

    public PostgreSQLBm25EvidenceCandidateSearchAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public List<EvidenceCandidate> search(
            OwnerPlayerId ownerPlayerId, List<AuthorizedDocumentScope> scope, String query, int limit) {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        List<AuthorizedDocumentScope> authorizedScope = List.copyOf(Objects.requireNonNull(scope, "scope must not be null"));
        if (authorizedScope.isEmpty()) throw new IllegalArgumentException("scope must not be empty");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        if (limit < 1 || limit > 30) throw new IllegalArgumentException("limit must be between 1 and 30");
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) return List.of();

        String sql = searchSql(authorizedScope.size());
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (AuthorizedDocumentScope item : authorizedScope) {
                statement.setObject(parameter++, item.documentId().value(), Types.OTHER);
                statement.setLong(parameter++, item.extractionVersion());
                statement.setString(parameter++, item.documentType().name());
            }
            statement.setObject(parameter++, ownerPlayerId.value(), Types.OTHER);
            statement.setArray(parameter++, connection.createArrayOf("text", terms.toArray(String[]::new)));
            statement.setDouble(parameter++, K1);
            statement.setDouble(parameter++, K1);
            statement.setInt(parameter, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<EvidenceCandidate> candidates = new ArrayList<>();
                int rank = 1;
                while (rows.next()) {
                    candidates.add(new EvidenceCandidate(
                            new KnowledgeDocumentId(rows.getObject("document_id", java.util.UUID.class)),
                            new ChunkId(rows.getObject("chunk_id", java.util.UUID.class)),
                            rows.getLong("extraction_version"),
                            DocumentType.valueOf(rows.getString("document_type")),
                            rows.getString("original_locator"),
                            rows.getString("content"),
                            new SourceProvenance(rows.getInt("page_number"), textArray(rows, "section_path"),
                                    doubleArray(rows, "bbox"), rows.getString("table_cell"), rows.getString("original_locator")),
                            null, rank++, 0d));
                }
                return List.copyOf(candidates);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not search scoped BM25 evidence candidates", exception);
        }
    }

    private static String searchSql(int scopeSize) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(scopeSize, "(?, ?, ?)"));
        return """
                WITH authorized_scope(document_id, extraction_version, document_type) AS (VALUES %s),
                scoped_chunks AS (
                    SELECT c.*, r.document_type,
                           CASE WHEN c.extraction_version ~ '^[0-9]+$' THEN c.extraction_version::bigint
                                ELSE GREATEST(r.version, 1) END AS numeric_extraction_version
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
                ), corpus AS (
                    SELECT COUNT(*)::double precision AS document_count,
                           COALESCE(AVG(document_length), 0)::double precision AS average_length
                      FROM scoped_chunks
                ), matching_terms AS (
                    SELECT frequency.term, COUNT(DISTINCT frequency.chunk_id)::double precision AS document_frequency
                      FROM chunk_term_frequency frequency
                      JOIN scoped_chunks chunk ON chunk.chunk_id = frequency.chunk_id
                     WHERE frequency.term = ANY (?)
                     GROUP BY frequency.term
                ), scored AS (
                    SELECT chunk.*, SUM(
                        LN(1 + ((corpus.document_count - matching_terms.document_frequency + .5)
                            / (matching_terms.document_frequency + .5)))
                        * (frequency.term_frequency * (? + 1))
                        / (frequency.term_frequency + ? * (1 - %f + %f * chunk.document_length / NULLIF(corpus.average_length, 0)))
                    ) AS score
                      FROM scoped_chunks chunk
                      JOIN chunk_term_frequency frequency ON frequency.chunk_id = chunk.chunk_id
                      JOIN matching_terms ON matching_terms.term = frequency.term
                     CROSS JOIN corpus
                     GROUP BY chunk.document_id, chunk.owner_player_id, chunk.extraction_version, chunk.processor_chunk_id,
                              chunk.chunk_id, chunk.sequence, chunk.content, chunk.embedding_text, chunk.embedding,
                              chunk.embedding_model, chunk.embedding_dimension, chunk.section_path, chunk.page_number,
                              chunk.bbox, chunk.table_cell, chunk.original_locator, chunk.created_at, chunk.parent_key,
                              chunk.document_length, chunk.document_type, chunk.numeric_extraction_version
                )
                SELECT document_id, chunk_id, numeric_extraction_version AS extraction_version, document_type,
                       original_locator, content, page_number, section_path, bbox, table_cell
                  FROM scored
                 ORDER BY score DESC, chunk_id
                 LIMIT ?
                """.formatted(placeholders, B, B);
    }

    private static List<String> tokenize(String value) {
        var matcher = TOKEN.matcher(value.toLowerCase(Locale.ROOT));
        var terms = new LinkedHashSet<String>();
        while (matcher.find()) terms.add(matcher.group());
        return List.copyOf(terms);
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
}
