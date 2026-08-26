package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.StorySourceEvidence;
import com.dndmaster.ruleknowledge.application.search.StorySourceScope;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchPort;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchQuery;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import java.sql.Connection;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class PgvectorStorySourceSearchRepository implements StorySourceSearchPort {
    private static final String SEARCH = """
            WITH ranked AS (
                SELECT c.document_id AS rulebook_id,
                       CASE WHEN c.extraction_version ~ '^[0-9]+$' THEN c.extraction_version::bigint
                            ELSE GREATEST(r.version, 1) END AS extraction_version,
                       c.original_locator AS locator, c.content, c.sequence,
                       c.page_number, c.bbox, c.table_cell, c.section_path,
                       0.75 * (1 - (c.embedding <=> CAST(? AS vector)))
                           + 0.25 * ts_rank_cd(to_tsvector('simple', c.content), plainto_tsquery('simple', ?))
                           + CASE WHEN c.content ~* '(DC|saving|check|attack|damage|roll|recharge)' THEN 0.20 ELSE 0 END AS score
                  FROM published_rag_chunk c
                   JOIN rulebook_registration r
                     ON r.rulebook_id = c.document_id
                    AND r.owner_player_id = c.owner_player_id
                    AND r.published_extraction_version = c.extraction_version
                   JOIN rag_extraction_version v
                     ON v.document_id = c.document_id
                    AND v.extraction_version = c.extraction_version
                    AND v.status = 'INDEXED'
                 WHERE c.owner_player_id = ?
                   AND r.document_type IN ('STORYBOOK', 'RULEBOOK')
                   AND c.document_id = ANY (?)
                   AND (? = FALSE OR c.original_locator = ANY (?))
            ), seeds AS (
                (SELECT rulebook_id, sequence FROM ranked ORDER BY score DESC, sequence LIMIT ?)
                UNION
                (SELECT rulebook_id, sequence FROM ranked
                  WHERE content ~* '(DC|saving|check|attack|damage|roll|recharge)'
                  ORDER BY rulebook_id, sequence
                  LIMIT ?)
            ), deduplicated AS (
                SELECT DISTINCT ON (candidate.rulebook_id, candidate.locator)
                       candidate.rulebook_id, candidate.extraction_version, candidate.locator,
                       candidate.content, candidate.score, candidate.sequence, candidate.page_number,
                       candidate.bbox, candidate.table_cell, candidate.section_path
                  FROM ranked candidate
                   JOIN seeds seed ON seed.rulebook_id = candidate.rulebook_id
                                 AND candidate.sequence BETWEEN seed.sequence - 1 AND seed.sequence + 1
                 ORDER BY candidate.rulebook_id, candidate.locator, candidate.score DESC
            )
                SELECT rulebook_id, extraction_version, locator, content, score, page_number, bbox, table_cell, section_path
              FROM deduplicated
             ORDER BY score DESC, sequence
             LIMIT ?
            """;

    private final DataSource dataSource;

    public PgvectorStorySourceSearchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public List<StorySourceEvidence> search(
            StorySourceSearchQuery query, float[] queryEmbedding, boolean activeContextOnly) {
        Objects.requireNonNull(query, "query must not be null");
        String vector = vectorLiteral(queryEmbedding);
        UUID[] documentIds = query.packageScope().stream()
                .map(scope -> scope.documentId().value())
                .toArray(UUID[]::new);
        String[] activeLocators = query.activeLocators().toArray(String[]::new);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, vector);
            statement.setString(2, query.situation());
            statement.setObject(3, query.owner().value(), Types.OTHER);
            statement.setArray(4, connection.createArrayOf("uuid", documentIds));
            statement.setBoolean(5, activeContextOnly);
            statement.setArray(6, connection.createArrayOf("text", activeLocators));
            int expandedLimit = Math.max(query.limit(), query.limit() * 3);
            statement.setInt(7, query.limit());
            statement.setInt(8, Math.max(query.limit() * 2, 12));
            statement.setInt(9, expandedLimit);
            try (ResultSet rows = statement.executeQuery()) {
                List<StorySourceEvidence> evidence = new ArrayList<>();
                while (rows.next()) {
                    evidence.add(new StorySourceEvidence(
                            new com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId(
                                    rows.getObject("rulebook_id", UUID.class)),
                            rows.getLong("extraction_version"),
                            rows.getString("locator"),
                            rows.getString("content"),
                            Math.max(0d, rows.getDouble("score")),
                            new SourceProvenance(rows.getInt("page_number"), textArray(rows, "section_path"),
                                    doubleArray(rows, "bbox"), rows.getString("table_cell"), rows.getString("locator"))));
                }
                return List.copyOf(evidence);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not search scoped story sources", exception);
        }
    }

    private static List<String> textArray(ResultSet rows, String column) throws SQLException {
        Array array = rows.getArray(column);
        if (array == null || array.getArray() == null) return List.of();
        Object[] values = (Object[]) array.getArray();
        return java.util.Arrays.stream(values).map(String::valueOf).toList();
    }

    private static List<Double> doubleArray(ResultSet rows, String column) throws SQLException {
        Array array = rows.getArray(column);
        if (array == null || array.getArray() == null) return List.of();
        Object[] values = (Object[]) array.getArray();
        return java.util.Arrays.stream(values).map(value -> ((Number) value).doubleValue()).toList();
    }

    private static String vectorLiteral(float[] values) {
        Objects.requireNonNull(values, "embedding must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (!Float.isFinite(values[index])) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
            if (index > 0) {
                result.append(',');
            }
            result.append(values[index]);
        }
        return result.append(']').toString();
    }
}
