package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchPort;
import com.dndmaster.ruleknowledge.application.search.RuleSearchHit;
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
            SELECT rulebook_id, chunk_id, locator, content,
                   embedding <=> CAST(? AS vector) AS distance,
                   chapter, section
              FROM rulebook_vector_chunk
             WHERE owner_player_id = ?
               AND rulebook_id = ANY (?)
             ORDER BY embedding <=> CAST(? AS vector), sequence
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
            int limit) {
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
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
