package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.evidence.EvidenceUnitRepository;
import com.dndmaster.ruleknowledge.domain.evidence.EvidenceEdge;
import com.dndmaster.ruleknowledge.domain.evidence.EvidenceEdgeType;
import com.dndmaster.ruleknowledge.domain.evidence.EvidenceKind;
import com.dndmaster.ruleknowledge.domain.evidence.EvidenceUnit;
import com.dndmaster.ruleknowledge.domain.evidence.EvidenceVisibility;
import com.dndmaster.ruleknowledge.domain.evidence.RuleEvidenceProjection;
import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.domain.rulebook.SourceSpan;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresEvidenceUnitRepository implements EvidenceUnitRepository {
    private final DataSource dataSource;

    public PostgresEvidenceUnitRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public void replace(RulebookId documentId, long extractionVersion, RuleEvidenceProjection projection) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(projection, "projection must not be null");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureExtractionSnapshot(connection, documentId, extractionVersion, projection);
                delete(connection, documentId, extractionVersion);
                for (EvidenceUnit unit : projection.units()) insertUnit(connection, unit);
                for (EvidenceEdge edge : projection.edges()) {
                    EvidenceUnit source = projection.unit(edge.from());
                    UUID edgeId = insertEdge(connection, edge, source.documentId(), source.extractionVersion());
                    for (SourceSpan span : edge.sourceSpans()) insertEdgeSpanLink(connection, edgeId, source.documentId(), source.extractionVersion(), span);
                }
                for (EvidenceUnit unit : projection.units()) {
                    for (SourceSpan span : unit.sourceSpans()) insertSpanLink(connection, unit.id(), unit.documentId(), unit.extractionVersion(), span);
                    insertSearchIndex(connection, unit);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw new RuleVectorPersistenceException("could not replace rule evidence", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not access rule evidence storage", exception);
        }
    }

    @Override
    public RuleEvidenceProjection load(RulebookId documentId, long extractionVersion) {
        try (Connection connection = dataSource.getConnection()) {
            Map<UUID, EvidenceUnit> units = new HashMap<>();
            String unitSql = "SELECT evidence_id, kind, content, visibility FROM rule_evidence_unit WHERE document_id = ? AND extraction_version = ?";
            try (PreparedStatement statement = connection.prepareStatement(unitSql)) {
                statement.setObject(1, documentId.value());
                statement.setLong(2, extractionVersion);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        UUID id = rows.getObject("evidence_id", UUID.class);
                        units.put(id, new EvidenceUnit(id, documentId, extractionVersion,
                                EvidenceKind.valueOf(rows.getString("kind")), rows.getString("content"),
                                EvidenceVisibility.valueOf(rows.getString("visibility")),
                                loadSpans(connection, id, documentId, extractionVersion)));
                    }
                }
            }
            List<EvidenceEdge> edges = new ArrayList<>();
            String edgeSql = "SELECT edge_id, from_evidence_id, to_evidence_id, edge_type FROM rule_evidence_edge WHERE document_id = ? AND extraction_version = ?";
            try (PreparedStatement statement = connection.prepareStatement(edgeSql)) {
                statement.setObject(1, documentId.value());
                statement.setLong(2, extractionVersion);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        UUID from = rows.getObject("from_evidence_id", UUID.class);
                        UUID edgeId = rows.getObject("edge_id", UUID.class);
                        edges.add(new EvidenceEdge(from,
                                rows.getObject("to_evidence_id", UUID.class),
                                EvidenceEdgeType.valueOf(rows.getString("edge_type")),
                                loadEdgeSpans(connection, edgeId, documentId, extractionVersion)));
                    }
                }
            }
            return new RuleEvidenceProjection(List.copyOf(units.values()), edges);
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not load rule evidence", exception);
        }
    }

    private static void delete(Connection connection, RulebookId documentId, long version) throws SQLException {
        for (String table : List.of("rule_evidence_edge_source_span", "rule_evidence_source_span",
                "rule_evidence_edge", "rule_evidence_search_index", "rule_evidence_unit")) {
            String sql = switch (table) {
                case "rule_evidence_edge_source_span", "rule_evidence_source_span" ->
                        "DELETE FROM " + table + " WHERE document_id = ? AND extraction_version = ?";
                case "rule_evidence_edge", "rule_evidence_unit" ->
                        "DELETE FROM " + table + " WHERE document_id = ? AND extraction_version = ?";
                default -> "DELETE FROM " + table + " WHERE document_id = ? AND extraction_version = ?";
            };
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, documentId.value());
                statement.setLong(2, version);
                statement.executeUpdate();
            }
        }
    }

    private static void insertUnit(Connection c, EvidenceUnit unit) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO rule_evidence_unit(evidence_id, document_id, extraction_version, kind, content, visibility) VALUES (?, ?, ?, ?, ?, ?)")) {
            s.setObject(1, unit.id()); s.setObject(2, unit.documentId().value()); s.setLong(3, unit.extractionVersion());
            s.setString(4, unit.kind().name()); s.setString(5, unit.content()); s.setString(6, unit.visibility().name()); s.executeUpdate();
        }
    }

    private static void ensureExtractionSnapshot(Connection c, RulebookId documentId, long version,
            RuleEvidenceProjection projection) throws SQLException {
        String registration = "SELECT owner_player_id, document_type, original_filename, format, file_size, content_hash FROM rulebook_registration WHERE rulebook_id = ?";
        try (PreparedStatement s = c.prepareStatement(registration)) {
            s.setObject(1, documentId.value());
            try (ResultSet row = s.executeQuery()) {
                if (!row.next()) throw new SQLException("rulebook registration not found: " + documentId.value());
                try (PreparedStatement document = c.prepareStatement("INSERT INTO knowledge_document(document_id, owner_player_id, document_type, original_filename, format, file_size, content_hash, current_published_version, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED') ON CONFLICT (document_id) DO NOTHING")) {
                    document.setObject(1, documentId.value()); document.setObject(2, row.getObject("owner_player_id"));
                    document.setString(3, row.getString("document_type")); document.setString(4, row.getString("original_filename"));
                    document.setString(5, row.getString("format")); document.setLong(6, row.getLong("file_size"));
                    document.setString(7, row.getString("content_hash")); document.setLong(8, version); document.executeUpdate();
                }
            }
        }
        try (PreparedStatement versionInsert = c.prepareStatement("INSERT INTO extraction_version(document_id, version, content_hash, status, published_at) SELECT ?, ?, content_hash, 'PUBLISHED', now() FROM knowledge_document WHERE document_id = ? ON CONFLICT (document_id, version) DO NOTHING")) {
            versionInsert.setObject(1, documentId.value()); versionInsert.setLong(2, version); versionInsert.setObject(3, documentId.value()); versionInsert.executeUpdate();
        }
        try (PreparedStatement update = c.prepareStatement("UPDATE knowledge_document SET current_published_version = ?, status = 'PUBLISHED' WHERE document_id = ?")) {
            update.setLong(1, version); update.setObject(2, documentId.value()); update.executeUpdate();
        }
        for (EvidenceUnit unit : projection.units()) for (SourceSpan span : unit.sourceSpans()) ensureSourceSpan(c, documentId, version, span);
        for (EvidenceEdge edge : projection.edges()) for (SourceSpan span : edge.sourceSpans()) ensureSourceSpan(c, documentId, version, span);
    }

    private static void ensureSourceSpan(Connection c, RulebookId documentId, long version, SourceSpan span) throws SQLException {
        try (PreparedStatement find = c.prepareStatement("SELECT 1 FROM extraction_source_span WHERE document_id = ? AND version = ? AND locator = ?")) {
            find.setObject(1, documentId.value()); find.setLong(2, version); find.setString(3, span.locator());
            try (ResultSet rows = find.executeQuery()) { if (rows.next()) return; }
        }
        String insert = "INSERT INTO extraction_source_span(document_id, version, page_number, left_coord, top_coord, right_coord, bottom_coord, reading_order, line_number, start_inclusive, end_exclusive, text, locator) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(insert)) {
            s.setObject(1, documentId.value()); s.setLong(2, version);
            if (span.pageNumber() == null) s.setNull(3, Types.INTEGER); else s.setInt(3, span.pageNumber());
            if (span.bounds() == null) { for (int i = 4; i <= 7; i++) s.setNull(i, Types.DOUBLE); }
            else { s.setDouble(4, span.bounds().left()); s.setDouble(5, span.bounds().top()); s.setDouble(6, span.bounds().right()); s.setDouble(7, span.bounds().bottom()); }
            s.setInt(8, span.readingOrder()); s.setInt(9, span.lineNumber()); s.setInt(10, span.startInclusive());
            s.setInt(11, span.endExclusive()); s.setString(12, span.text()); s.setString(13, span.locator()); s.executeUpdate();
        }
    }

    private static UUID insertEdge(Connection c, EvidenceEdge edge, RulebookId documentId, long version) throws SQLException {
        UUID edgeId = UUID.randomUUID();
        try (PreparedStatement s = c.prepareStatement("INSERT INTO rule_evidence_edge(edge_id, from_evidence_id, to_evidence_id, document_id, extraction_version, edge_type) VALUES (?, ?, ?, ?, ?, ?)")) {
            s.setObject(1, edgeId); s.setObject(2, edge.from()); s.setObject(3, edge.to());
            s.setObject(4, documentId.value()); s.setLong(5, version); s.setString(6, edge.type().name()); s.executeUpdate();
        }
        return edgeId;
    }

    private static void insertSpanLink(Connection c, UUID evidenceId, RulebookId documentId, long version, SourceSpan span) throws SQLException {
        String sql = "INSERT INTO rule_evidence_source_span(evidence_id, document_id, extraction_version, span_id) "
                + "SELECT ?, document_id, version, span_id FROM extraction_source_span WHERE document_id = ? AND version = ? AND locator = ? LIMIT 1";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, evidenceId); s.setObject(2, documentId.value()); s.setLong(3, version); s.setString(4, span.locator());
            if (s.executeUpdate() != 1) throw new SQLException("source span not found: " + span.locator());
        }
    }

    private static void insertSearchIndex(Connection c, EvidenceUnit unit) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO rule_evidence_search_index(evidence_id, document_id, extraction_version, content) VALUES (?, ?, ?, ?)")) {
            s.setObject(1, unit.id()); s.setObject(2, unit.documentId().value()); s.setLong(3, unit.extractionVersion()); s.setString(4, unit.content()); s.executeUpdate();
        }
    }

    private static void insertEdgeSpanLink(Connection c, UUID edgeId, RulebookId documentId, long version, SourceSpan span) throws SQLException {
        String sql = "INSERT INTO rule_evidence_edge_source_span(edge_id, document_id, extraction_version, span_id) "
                + "SELECT ?, document_id, version, span_id FROM extraction_source_span WHERE document_id = ? AND version = ? AND locator = ? LIMIT 1";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, edgeId); s.setObject(2, documentId.value()); s.setLong(3, version); s.setString(4, span.locator());
            if (s.executeUpdate() != 1) throw new SQLException("edge source span not found: " + span.locator());
        }
    }

    private static List<SourceSpan> loadSpans(Connection c, UUID evidenceId, RulebookId documentId, long version) throws SQLException {
        List<SourceSpan> spans = new ArrayList<>();
        String sql = "SELECT s.line_number, s.start_inclusive, s.end_exclusive, s.text, s.locator, s.page_number, s.left_coord, s.top_coord, s.right_coord, s.bottom_coord, s.reading_order FROM rule_evidence_source_span l JOIN extraction_source_span s ON s.document_id = l.document_id AND s.version = l.extraction_version AND s.span_id = l.span_id WHERE l.evidence_id = ? AND l.document_id = ? AND l.extraction_version = ? ORDER BY s.reading_order";
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setObject(1, evidenceId); statement.setObject(2, documentId.value()); statement.setLong(3, version);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    BoundingBox bounds = rows.getObject("left_coord") == null ? null : new BoundingBox(
                            rows.getDouble("left_coord"), rows.getDouble("top_coord"), rows.getDouble("right_coord"), rows.getDouble("bottom_coord"));
                    spans.add(new SourceSpan(rows.getInt("line_number"), rows.getInt("start_inclusive"), rows.getInt("end_exclusive"),
                            rows.getString("text"), rows.getString("locator"), (Integer) rows.getObject("page_number"), bounds, rows.getInt("reading_order")));
                }
            }
        }
        return List.copyOf(spans);
    }

    private static List<SourceSpan> loadEdgeSpans(Connection c, UUID edgeId, RulebookId documentId, long version) throws SQLException {
        List<SourceSpan> spans = new ArrayList<>();
        String sql = "SELECT s.line_number, s.start_inclusive, s.end_exclusive, s.text, s.locator, s.page_number, s.left_coord, s.top_coord, s.right_coord, s.bottom_coord, s.reading_order FROM rule_evidence_edge_source_span l JOIN extraction_source_span s ON s.document_id = l.document_id AND s.version = l.extraction_version AND s.span_id = l.span_id WHERE l.edge_id = ? AND l.document_id = ? AND l.extraction_version = ? ORDER BY s.reading_order";
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setObject(1, edgeId); statement.setObject(2, documentId.value()); statement.setLong(3, version);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    BoundingBox bounds = rows.getObject("left_coord") == null ? null : new BoundingBox(
                            rows.getDouble("left_coord"), rows.getDouble("top_coord"), rows.getDouble("right_coord"), rows.getDouble("bottom_coord"));
                    spans.add(new SourceSpan(rows.getInt("line_number"), rows.getInt("start_inclusive"), rows.getInt("end_exclusive"),
                            rows.getString("text"), rows.getString("locator"), (Integer) rows.getObject("page_number"), bounds, rows.getInt("reading_order")));
                }
            }
        }
        if (spans.isEmpty()) throw new RuleVectorPersistenceException("evidence edge has no source span: " + edgeId, null);
        return List.copyOf(spans);
    }
}
