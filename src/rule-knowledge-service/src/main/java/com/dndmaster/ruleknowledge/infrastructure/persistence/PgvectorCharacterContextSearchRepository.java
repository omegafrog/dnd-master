package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.*;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

public final class PgvectorCharacterContextSearchRepository implements CharacterContextSearchPort {
    private static final String SEARCH = """
            SELECT c.rulebook_id, r.document_type, r.version, c.locator, c.content,
                   1 - (c.embedding <=> CAST(? AS vector)) AS similarity
              FROM rulebook_vector_chunk c
              JOIN rulebook_vector_index i ON i.index_id = c.index_id AND i.status = 'READY'
              JOIN rulebook_registration r ON r.rulebook_id = c.rulebook_id
             WHERE c.owner_player_id = ?
               AND r.owner_player_id = c.owner_player_id
               AND r.document_type = ?
               AND EXISTS (
                   SELECT 1 FROM unnest(?::uuid[], ?::bigint[]) AS scope(document_id, extraction_version)
                    WHERE scope.document_id = c.rulebook_id AND scope.extraction_version = r.version)
             ORDER BY similarity DESC, c.sequence
            """;

    private final DataSource dataSource;

    public PgvectorCharacterContextSearchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public List<CharacterContextSearchHit> search(
            OwnerPlayerId owner, DocumentType type, List<CharacterContextDocumentScope> scope, float[] embedding) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(type);
        if (scope == null || scope.isEmpty()) throw new IllegalArgumentException("scope must not be empty");
        String vector = vectorLiteral(embedding);
        UUID[] ids = scope.stream().map(s -> s.documentId().value()).toArray(UUID[]::new);
        Long[] versions = scope.stream().map(CharacterContextDocumentScope::extractionVersion).toArray(Long[]::new);
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, vector);
            statement.setObject(2, owner.value(), Types.OTHER);
            statement.setString(3, type.name());
            statement.setArray(4, connection.createArrayOf("uuid", ids));
            statement.setArray(5, connection.createArrayOf("int8", versions));
            try (ResultSet rows = statement.executeQuery()) {
                List<CharacterContextSearchHit> hits = new ArrayList<>();
                while (rows.next()) hits.add(new CharacterContextSearchHit(
                        new KnowledgeDocumentId(rows.getObject("rulebook_id", UUID.class)), type,
                        rows.getLong("version"), rows.getString("locator"), rows.getString("content"),
                        Math.max(0d, Math.min(1d, rows.getDouble("similarity")))));
                return List.copyOf(hits);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not search character context", exception);
        }
    }

    private static String vectorLiteral(float[] values) {
        Objects.requireNonNull(values, "embedding must not be null");
        if (values.length == 0) throw new IllegalArgumentException("embedding must not be empty");
        StringJoiner result = new StringJoiner(",", "[", "]");
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("embedding values must be finite");
            result.add(Float.toString(value));
        }
        return result.toString();
    }
}
