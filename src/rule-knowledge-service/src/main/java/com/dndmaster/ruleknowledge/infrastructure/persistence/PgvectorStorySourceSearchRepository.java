package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.StorySourceEvidence;
import com.dndmaster.ruleknowledge.application.search.StorySourceScope;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchPort;
import com.dndmaster.ruleknowledge.application.search.StorySourceSearchQuery;
import java.sql.Connection;
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
                SELECT c.rulebook_id, r.version, c.locator, c.content, c.sequence,
                       0.75 * (1 - (c.embedding <=> CAST(? AS vector)))
                           + 0.25 * ts_rank_cd(to_tsvector('simple', c.content), plainto_tsquery('simple', ?))
                           + CASE WHEN c.content ~* '(DC|saving|check|attack|damage|roll|recharge)' THEN 0.20 ELSE 0 END AS score
                  FROM rulebook_vector_chunk c
                   JOIN rulebook_vector_index i ON i.index_id = c.index_id AND i.status = 'READY'
                   JOIN rulebook_registration r ON r.rulebook_id = c.rulebook_id
                 WHERE c.owner_player_id = ?
                   AND r.owner_player_id = c.owner_player_id
                   AND r.document_type IN ('STORYBOOK', 'RULEBOOK')
                   AND EXISTS (
                       SELECT 1
                         FROM unnest(?::uuid[], ?::bigint[]) AS scope(document_id, extraction_version)
                        WHERE scope.document_id = c.rulebook_id
                          AND scope.extraction_version = r.version
                   )
                   AND (? = FALSE OR c.locator = ANY (?))
            ), seeds AS (
                (SELECT rulebook_id, sequence FROM ranked ORDER BY score DESC, sequence LIMIT ?)
                UNION
                (SELECT rulebook_id, sequence FROM ranked
                  WHERE content ~* '(DC|saving|check|attack|damage|roll|recharge)'
                  ORDER BY rulebook_id, sequence
                  LIMIT ?)
            ), deduplicated AS (
                SELECT DISTINCT ON (candidate.rulebook_id, candidate.locator)
                       candidate.rulebook_id, candidate.version, candidate.locator,
                       candidate.content, candidate.score, candidate.sequence
                  FROM ranked candidate
                   JOIN seeds seed ON seed.rulebook_id = candidate.rulebook_id
                                 AND candidate.sequence BETWEEN seed.sequence - 1 AND seed.sequence + 1
                 ORDER BY candidate.rulebook_id, candidate.locator, candidate.score DESC
            )
            SELECT rulebook_id, version, locator, content, score
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
        Long[] extractionVersions = query.packageScope().stream()
                .map(StorySourceScope::extractionVersion)
                .toArray(Long[]::new);
        String[] activeLocators = query.activeLocators().toArray(String[]::new);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, vector);
            statement.setString(2, query.situation());
            statement.setObject(3, query.owner().value(), Types.OTHER);
            statement.setArray(4, connection.createArrayOf("uuid", documentIds));
            statement.setArray(5, connection.createArrayOf("int8", extractionVersions));
            statement.setBoolean(6, activeContextOnly);
            statement.setArray(7, connection.createArrayOf("text", activeLocators));
            int expandedLimit = Math.max(query.limit(), query.limit() * 3);
            statement.setInt(8, query.limit());
            statement.setInt(9, Math.max(query.limit() * 2, 12));
            statement.setInt(10, expandedLimit);
            try (ResultSet rows = statement.executeQuery()) {
                List<StorySourceEvidence> evidence = new ArrayList<>();
                while (rows.next()) {
                    evidence.add(new StorySourceEvidence(
                            new com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId(
                                    rows.getObject("rulebook_id", UUID.class)),
                            rows.getLong("version"),
                            rows.getString("locator"),
                            rows.getString("content"),
                            Math.max(0d, rows.getDouble("score"))));
                }
                return List.copyOf(evidence);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not search scoped story sources", exception);
        }
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
