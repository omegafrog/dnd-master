package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchPort;
import com.dndmaster.ruleknowledge.application.search.QueryIntent;
import com.dndmaster.ruleknowledge.application.search.RuleSearchHit;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class PgvectorRuleEvidenceSearchRepository implements RuleEvidenceSearchPort {
    private static final String SEARCH = """
            SELECT c.rulebook_id, r.document_type, c.chunk_id, c.locator, c.content,
                   c.embedding <=> CAST(? AS vector) AS distance,
                   c.chapter, c.section
              FROM rulebook_vector_chunk c
              JOIN rulebook_registration r
                ON r.rulebook_id = c.rulebook_id
               AND r.owner_player_id = c.owner_player_id
             WHERE c.owner_player_id = ?
               AND c.rulebook_id = ANY (?)
             ORDER BY CASE r.document_type
                          WHEN 'RULEBOOK' THEN ?
                          WHEN 'STORYBOOK' THEN ?
                          ELSE 0
                      END,
                      c.embedding <=> CAST(? AS vector),
                      c.sequence
             LIMIT ?
            """;

    private final DataSource dataSource;

    public PgvectorRuleEvidenceSearchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public List<RuleSearchHit> search(
            OwnerPlayerId ownerPlayerId,
            Collection<KnowledgeDocumentId> selectedKnowledgeDocumentIds,
            float[] queryEmbedding,
            QueryIntent queryIntent,
            int limit) {
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        Objects.requireNonNull(queryIntent, "queryIntent must not be null");
        List<KnowledgeDocumentId> selected = List.copyOf(
                Objects.requireNonNull(selectedKnowledgeDocumentIds, "selectedKnowledgeDocumentIds must not be null"));
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("at least one selected knowledge document is required");
        }
        if (selected.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("selected knowledge documents must not contain null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String vector = vectorLiteral(queryEmbedding);
        int rulePriority = EvidenceQueryRankingPolicy.priority(queryIntent, DocumentType.RULEBOOK);
        int storyPriority = EvidenceQueryRankingPolicy.priority(queryIntent, DocumentType.STORYBOOK);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, vector);
            statement.setObject(2, ownerPlayerId.value(), Types.OTHER);
            UUID[] ids = selected.stream().map(KnowledgeDocumentId::value).toArray(UUID[]::new);
            statement.setArray(3, connection.createArrayOf("uuid", ids));
            statement.setInt(4, rulePriority);
            statement.setInt(5, storyPriority);
            statement.setString(6, vector);
            statement.setInt(7, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<RuleSearchHit> hits = new ArrayList<>();
                while (rows.next()) {
                    hits.add(new RuleSearchHit(
                            new KnowledgeDocumentId(rows.getObject("rulebook_id", UUID.class)),
                            DocumentType.valueOf(rows.getString("document_type")),
                            new ChunkId(rows.getObject("chunk_id", UUID.class)),
                            rows.getString("locator"),
                            rows.getString("content"),
                            rows.getDouble("distance"),
                            rows.getString("chapter"),
                            rows.getString("section")));
                }
                return List.copyOf(hits);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not search rulebook vectors", exception);
        }
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
