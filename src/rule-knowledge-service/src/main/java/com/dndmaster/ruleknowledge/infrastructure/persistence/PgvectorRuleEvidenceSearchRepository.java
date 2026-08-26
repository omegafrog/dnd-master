package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchPort;
import com.dndmaster.ruleknowledge.application.search.QueryIntent;
import com.dndmaster.ruleknowledge.application.search.RuleSearchHit;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class PgvectorRuleEvidenceSearchRepository implements RuleEvidenceSearchPort {
    private static final String SEARCH = """
            SELECT c.document_id AS rulebook_id, c.chunk_id,
                   CASE WHEN c.extraction_version ~ '^[0-9]+$' THEN c.extraction_version::bigint
                        ELSE GREATEST(r.version, 1) END AS extraction_version,
                   c.original_locator AS locator, c.content,
                   c.embedding <=> CAST(? AS vector) AS distance,
                   array_to_string(c.section_path, ' / ') AS section,
                   CASE WHEN cardinality(c.section_path) > 0 THEN c.section_path[1] ELSE NULL END AS chapter,
                   c.page_number, c.bbox, c.table_cell, c.section_path
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
               AND c.document_id = ANY (?)
             ORDER BY c.embedding <=> CAST(? AS vector), c.sequence
             LIMIT ?
            """;

    private final DataSource dataSource;

    public PgvectorRuleEvidenceSearchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public List<RuleSearchHit> search(
            OwnerPlayerId ownerPlayerId,
            Collection<RulebookId> selectedRulebookIds,
            float[] queryEmbedding,
            QueryIntent queryIntent,
            int limit) {
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        Objects.requireNonNull(queryIntent, "queryIntent must not be null");
        List<RulebookId> selected = List.copyOf(
                Objects.requireNonNull(selectedRulebookIds, "selectedRulebookIds must not be null"));
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("at least one selected rulebook is required");
        }
        if (selected.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("selected rulebooks must not contain null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String vector = vectorLiteral(queryEmbedding);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, vector);
            statement.setObject(2, ownerPlayerId.value(), Types.OTHER);
            UUID[] ids = selected.stream().map(RulebookId::value).toArray(UUID[]::new);
            statement.setArray(3, connection.createArrayOf("uuid", ids));
            statement.setString(4, vector);
            statement.setInt(5, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<RuleSearchHit> hits = new ArrayList<>();
                while (rows.next()) {
                    hits.add(new RuleSearchHit(
                            new RulebookId(rows.getObject("rulebook_id", UUID.class)),
                            new ChunkId(rows.getObject("chunk_id", UUID.class)),
                            rows.getLong("extraction_version"),
                            rows.getString("locator"),
                            rows.getString("content"),
                            rows.getDouble("distance"),
                            rows.getString("chapter"),
                            rows.getString("section"),
                            new SourceProvenance(rows.getInt("page_number"), textArray(rows, "section_path"),
                                    doubleArray(rows, "bbox"), rows.getString("table_cell"), rows.getString("locator"))));
                }
                return List.copyOf(hits);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not search rulebook vectors", exception);
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
            float value = values[index];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
            if (index > 0) {
                result.append(',');
            }
            result.append(Float.toString(value));
        }
        return result.append(']').toString();
    }
}
