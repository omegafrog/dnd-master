package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.search.*;
import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

public final class PgvectorCharacterContextSearchRepository implements CharacterContextSearchPort {
    private static final String SEARCH = """
            SELECT c.document_id AS rulebook_id, r.document_type, r.version, c.original_locator AS locator, c.content,
                   1 - (c.embedding <=> CAST(? AS vector)) AS similarity
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
               AND r.document_type = ?
               AND c.document_id = ANY (?)
               ORDER BY CASE WHEN cardinality(?::text[]) > 0
                                  AND array_to_string(c.section_path, ' / ') ILIKE ANY (?::text[])
                             THEN 0 ELSE 1 END,
                      similarity DESC, c.sequence
            """;

    private final DataSource dataSource;

    public PgvectorCharacterContextSearchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public List<CharacterContextSearchHit> search(
            OwnerPlayerId owner, DocumentType type, List<CharacterContextDocumentScope> scope, float[] embedding) {
        return search(owner, type, scope, embedding, List.of(), List.of());
    }

    @Override public List<CharacterContextSearchHit> search(OwnerPlayerId owner, DocumentType type,
            List<CharacterContextDocumentScope> scope, float[] embedding, List<String> chapterHints, List<String> sectionHints) {
        return searchInternal(owner, type, scope, embedding, chapterHints, sectionHints == null ? List.of() : sectionHints);
    }

    @Override
    public List<CharacterContextSearchHit> search(
            OwnerPlayerId owner, DocumentType type, List<CharacterContextDocumentScope> scope,
            float[] embedding, List<String> chapterHints) {
        return searchInternal(owner, type, scope, embedding, chapterHints, List.of());
    }

    private List<CharacterContextSearchHit> searchInternal(OwnerPlayerId owner, DocumentType type,
            List<CharacterContextDocumentScope> scope, float[] embedding, List<String> chapterHints, List<String> sectionHints) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(type);
        if (scope == null || scope.isEmpty()) throw new IllegalArgumentException("scope must not be empty");
        String vector = vectorLiteral(embedding);
        UUID[] ids = scope.stream().map(s -> s.documentId().value()).toArray(UUID[]::new);
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, vector);
            statement.setObject(2, owner.value(), Types.OTHER);
            statement.setString(3, type.name());
            statement.setArray(4, connection.createArrayOf("uuid", ids));
            String[] sectionPatterns = sectionHints.stream().map(hint -> "%" + hint + "%").toArray(String[]::new);
            statement.setArray(5, connection.createArrayOf("text", sectionPatterns));
            statement.setArray(6, connection.createArrayOf("text", sectionPatterns));
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
