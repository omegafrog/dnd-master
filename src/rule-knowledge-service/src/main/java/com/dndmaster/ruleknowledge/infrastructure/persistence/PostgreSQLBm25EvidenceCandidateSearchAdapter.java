package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.application.search.AuthorizedDocumentScope;
import com.dndmaster.ruleknowledge.application.search.Bm25EvidenceCandidateSearchPort;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceSearchRequest;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
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
    public List<EvidenceCandidate> search(EvidenceSearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var authorizedScope = request.scope();
        List<String> terms = tokenize(request.query());
        if (terms.isEmpty()) return List.of();

        String sql = searchSql(authorizedScope.size());
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (AuthorizedDocumentScope item : authorizedScope) {
                statement.setObject(parameter++, item.documentId().value(), Types.OTHER);
                statement.setLong(parameter++, item.extractionVersion());
                statement.setString(parameter++, item.documentType().name());
                statement.setObject(parameter++, (item.documentOwner() != null ? item.documentOwner() : request.ownerPlayerId()).value(), Types.OTHER);
            }
            statement.setArray(parameter++, connection.createArrayOf("text", terms.toArray(String[]::new)));
            statement.setDouble(parameter++, K1);
            statement.setDouble(parameter++, K1);
            statement.setArray(parameter++, connection.createArrayOf("text", request.activeLocators().toArray(String[]::new)));
            statement.setInt(parameter, request.bm25Limit());
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
        String placeholders = String.join(", ", java.util.Collections.nCopies(scopeSize, "(?, ?, ?, ?)"));
        return """
                WITH authorized_scope(document_id, extraction_version, document_type, owner_player_id) AS (VALUES %s),
                query_terms AS (SELECT unnest(?) AS term),
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
                         AND scope.owner_player_id = c.owner_player_id
                     WHERE c.document_length > 0
                ), corpus AS (
                    SELECT COUNT(*)::double precision AS document_count,
                           COALESCE(AVG(document_length), 0)::double precision AS average_length
                      FROM scoped_chunks
                ), matching_terms AS (
                    SELECT query_term.term, COUNT(*)::double precision AS document_frequency
                      FROM query_terms query_term
                      JOIN chunk_term_frequency frequency ON frequency.term = query_term.term
                      JOIN scoped_chunks chunk ON chunk.chunk_id = frequency.chunk_id
                     GROUP BY query_term.term
                ), scored AS (
                    SELECT chunk.chunk_id, SUM(
                        LN(1 + ((corpus.document_count - matching_terms.document_frequency + .5)
                            / (matching_terms.document_frequency + .5)))
                        * (frequency.term_frequency * (? + 1))
                        / (frequency.term_frequency + ? * (1 - %f + %f * chunk.document_length / NULLIF(corpus.average_length, 0)))
                    ) AS score
                      FROM matching_terms
                      JOIN chunk_term_frequency frequency ON frequency.term = matching_terms.term
                      JOIN scoped_chunks chunk ON chunk.chunk_id = frequency.chunk_id
                     CROSS JOIN corpus
                     GROUP BY chunk.chunk_id
                )
                SELECT chunk.document_id, chunk.chunk_id,
                       chunk.numeric_extraction_version AS extraction_version, chunk.document_type,
                       chunk.original_locator, chunk.content, chunk.page_number, chunk.section_path,
                       chunk.bbox, chunk.table_cell
                  FROM scoped_chunks chunk
                  JOIN scored ON scored.chunk_id = chunk.chunk_id
                 ORDER BY CASE WHEN chunk.original_locator = ANY (?) THEN 0 ELSE 1 END,
                          scored.score DESC, chunk.chunk_id
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
