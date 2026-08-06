package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.RuleEvidenceSearchPort;
import com.dndmaster.ruleknowledge.application.search.QueryIntent;
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

public final class PgvectorRuleEvidenceSearchRepository implements RuleEvidenceSearchPort, com.dndmaster.ruleknowledge.application.search.RuleEvidenceKeywordSearchPort {
    private static final String KEYWORD_SEARCH = """
            SELECT c.rulebook_id, c.chunk_id, c.locator, c.content,
                   1 - LEAST(1, GREATEST(0, ts_rank_cd(to_tsvector('simple', c.content), plainto_tsquery('simple', ?)))) AS distance,
                   c.chapter, c.section
              FROM rulebook_vector_chunk c
              JOIN rulebook_vector_index i ON i.index_id = c.index_id AND i.status = 'READY'
             WHERE c.owner_player_id = ? AND c.rulebook_id = ANY (?)
             ORDER BY ts_rank_cd(to_tsvector('simple', c.content), plainto_tsquery('simple', ?)) DESC, c.sequence
             LIMIT ?
            """;
    private static final String SEARCH = """
            SELECT c.rulebook_id, c.chunk_id, c.locator, c.content,
                   c.embedding <=> CAST(? AS vector) AS distance,
                   c.chapter, c.section
              FROM rulebook_vector_chunk c
              JOIN rulebook_vector_index i ON i.index_id = c.index_id AND i.status = 'READY'
             WHERE c.owner_player_id = ?
               AND c.rulebook_id = ANY (?)
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

    @Override
    public List<RuleSearchHit> searchKeyword(OwnerPlayerId owner, Collection<RulebookId> rulebooks, String query, int limit) {
        List<RulebookId> selected = List.copyOf(Objects.requireNonNull(rulebooks));
        if (selected.isEmpty() || query == null || query.isBlank() || limit <= 0) throw new IllegalArgumentException("invalid keyword search");
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(KEYWORD_SEARCH)) {
            statement.setString(1, query); statement.setObject(2, owner.value(), Types.OTHER);
            statement.setArray(3, connection.createArrayOf("uuid", selected.stream().map(RulebookId::value).toArray(UUID[]::new)));
            statement.setString(4, query); statement.setInt(5, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<RuleSearchHit> hits = new ArrayList<>();
                while (rows.next()) hits.add(new RuleSearchHit(new RulebookId(rows.getObject("rulebook_id", UUID.class)),
                        new ChunkId(rows.getObject("chunk_id", UUID.class)), rows.getString("locator"), rows.getString("content"),
                        rows.getDouble("distance"), rows.getString("chapter"), rows.getString("section")));
                return List.copyOf(hits);
            }
        } catch (SQLException exception) { throw new RuleVectorPersistenceException("could not search rulebook keywords", exception); }
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
