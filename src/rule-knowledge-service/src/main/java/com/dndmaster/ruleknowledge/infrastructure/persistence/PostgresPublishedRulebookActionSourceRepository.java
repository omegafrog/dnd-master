package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.definition.PublishedRulebookActionSource;
import com.dndmaster.ruleknowledge.application.definition.PublishedRulebookActionSourceRepository;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Reads only the currently published indexed extraction, so admin reruns never reopen the PDF. */
public final class PostgresPublishedRulebookActionSourceRepository implements PublishedRulebookActionSourceRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    public PostgresPublishedRulebookActionSourceRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource; this.mapper = mapper;
    }

    @Override public Optional<PublishedRulebookActionSource> findCurrent(UUID documentId) {
        try (var c = dataSource.getConnection(); var version = c.prepareStatement("SELECT r.published_extraction_version, r.preprocessing_pages, r.version FROM rulebook_registration r JOIN rag_extraction_version v ON v.document_id=r.rulebook_id AND v.extraction_version=r.published_extraction_version AND v.status='INDEXED' WHERE r.rulebook_id=?")) {
            version.setObject(1, documentId);
            try (var rows = version.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                String extractionVersion = rows.getString(1);
                List<PreprocessingPageState> pages = mapper.readValue(rows.getString(2), mapper.getTypeFactory()
                        .constructCollectionType(List.class, PreprocessingPageState.class));
                return Optional.of(new PublishedRulebookActionSource(documentId, extractionVersion, rows.getLong(3), pages));
            }
        } catch (SQLException e) { throw new RuntimeException("could not load published rulebook action source", e); }
        catch (java.io.IOException e) { throw new IllegalStateException("could not read published rulebook page manifest", e); }
    }
}
