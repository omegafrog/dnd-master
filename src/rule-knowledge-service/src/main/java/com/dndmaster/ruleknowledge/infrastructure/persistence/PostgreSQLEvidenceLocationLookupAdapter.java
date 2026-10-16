package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.dndmaster.ruleknowledge.application.search.AuthorizedDocumentScope;
import com.dndmaster.ruleknowledge.application.search.EvidenceCandidate;
import com.dndmaster.ruleknowledge.application.search.EvidenceLocationLookupPort;
import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

/** Retrieves bounded same-page excerpts from an exact source location in the published scope. */
public final class PostgreSQLEvidenceLocationLookupAdapter implements EvidenceLocationLookupPort {
    private final DataSource dataSource;

    public PostgreSQLEvidenceLocationLookupAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Map<String, List<EvidenceCandidate>> findByLocators(OwnerPlayerId ownerPlayerId,
            List<AuthorizedDocumentScope> scope, List<String> locators, int limit) {
        Objects.requireNonNull(ownerPlayerId);
        scope = List.copyOf(Objects.requireNonNull(scope));
        locators = List.copyOf(Objects.requireNonNull(locators));
        if (scope.isEmpty() || scope.stream().anyMatch(Objects::isNull)
                || locators.isEmpty() || locators.size() > 16
                || locators.stream().anyMatch(locator -> locator == null || locator.isBlank())
                || limit < 1 || limit > 64) throw new IllegalArgumentException("invalid exact evidence lookup");

        String authorizedValues = String.join(",", java.util.Collections.nCopies(scope.size(), "(?,?,?,?)"));
        String sql = "WITH authorized_scope(document_id,extraction_version,document_type,owner_player_id) AS (VALUES "
                + authorizedValues + "), requested_locator(locator) AS (SELECT unnest(?::text[])), source_page AS ("
                + "SELECT DISTINCT requested.locator,c.document_id,c.extraction_version,c.page_number,c.owner_player_id "
                + "FROM requested_locator requested JOIN published_rag_chunk c ON c.original_locator=requested.locator "
                + "JOIN rulebook_registration r ON r.rulebook_id=c.document_id AND r.owner_player_id=c.owner_player_id "
                + "AND r.published_extraction_version=c.extraction_version JOIN rag_extraction_version v ON v.document_id=c.document_id "
                + "AND v.extraction_version=c.extraction_version AND v.status='INDEXED' JOIN authorized_scope s "
                + "ON s.document_id=c.document_id AND s.extraction_version=CASE WHEN c.extraction_version ~ '^[0-9]+$' "
                + "THEN c.extraction_version::bigint ELSE GREATEST(r.version,1) END AND s.document_type=r.document_type "
                + "AND s.owner_player_id=c.owner_player_id), ranked AS ("
                + "SELECT source.locator AS requested_locator,c.document_id,c.chunk_id,CASE WHEN c.extraction_version ~ '^[0-9]+$' "
                + "THEN c.extraction_version::bigint ELSE GREATEST(r.version,1) END AS extraction_version,r.document_type,"
                + "c.original_locator,c.content,c.page_number,c.section_path,c.bbox,c.table_cell,c.source_spans,"
                + "row_number() OVER (PARTITION BY source.locator ORDER BY CASE WHEN c.original_locator=source.locator THEN 0 ELSE 1 END,"
                + "c.sequence,c.chunk_id) AS locator_rank FROM source_page source JOIN published_rag_chunk c "
                + "ON c.document_id=source.document_id AND c.extraction_version=source.extraction_version "
                + "AND c.page_number=source.page_number AND c.owner_player_id=source.owner_player_id "
                + "JOIN rulebook_registration r ON r.rulebook_id=c.document_id AND r.owner_player_id=c.owner_player_id "
                + "AND r.published_extraction_version=c.extraction_version JOIN rag_extraction_version v ON v.document_id=c.document_id "
                + "AND v.extraction_version=c.extraction_version AND v.status='INDEXED') "
                + "SELECT requested_locator,document_id,chunk_id,extraction_version,document_type,original_locator,content,"
                + "page_number,section_path,bbox,table_cell,source_spans FROM ranked WHERE locator_rank<=? "
                + "ORDER BY requested_locator,locator_rank";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (AuthorizedDocumentScope item : scope) {
                statement.setObject(parameter++, item.documentId().value(), Types.OTHER);
                statement.setLong(parameter++, item.extractionVersion());
                statement.setString(parameter++, item.documentType().name());
                statement.setObject(parameter++, (item.documentOwner() == null ? ownerPlayerId : item.documentOwner()).value(), Types.OTHER);
            }
            Array locatorArray = connection.createArrayOf("text", locators.toArray(String[]::new));
            statement.setArray(parameter++, locatorArray);
            statement.setInt(parameter, limit);
            Map<String, List<EvidenceCandidate>> found = new LinkedHashMap<>();
            locators.forEach(locator -> found.put(locator, new ArrayList<>()));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String requestedLocator = rows.getString("requested_locator");
                    List<EvidenceCandidate> excerpts = found.get(requestedLocator);
                    if (excerpts == null) throw new IllegalStateException("location lookup returned an unrequested locator");
                    excerpts.add(new EvidenceCandidate(new KnowledgeDocumentId(rows.getObject("document_id", java.util.UUID.class)),
                            new ChunkId(rows.getObject("chunk_id", java.util.UUID.class)), rows.getLong("extraction_version"),
                            DocumentType.valueOf(rows.getString("document_type")), rows.getString("original_locator"),
                            rows.getString("content"), new SourceProvenance(rows.getInt("page_number"), textArray(rows, "section_path"),
                                    doubleArray(rows, "bbox"), rows.getString("table_cell"), rows.getString("original_locator"),
                                    SourceProvenance.sourceSpansFromJson(rows.getString("source_spans"))),
                            excerpts.size() + 1, null));
                }
            }
            Map<String, List<EvidenceCandidate>> immutable = new LinkedHashMap<>();
            found.forEach((locator, excerpts) -> immutable.put(locator, List.copyOf(excerpts)));
            return Map.copyOf(immutable);
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not retrieve evidence by source page", exception);
        }
    }

    private static List<String> textArray(ResultSet rows, String name) throws SQLException {
        Array array = rows.getArray(name);
        return array == null ? List.of() : java.util.Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }

    private static List<Double> doubleArray(ResultSet rows, String name) throws SQLException {
        Array array = rows.getArray(name);
        if (array == null) return List.of();
        Object[] values = (Object[]) array.getArray();
        List<Double> result = new ArrayList<>(values.length);
        for (Object value : values) result.add(((Number) value).doubleValue());
        return List.copyOf(result);
    }
}
