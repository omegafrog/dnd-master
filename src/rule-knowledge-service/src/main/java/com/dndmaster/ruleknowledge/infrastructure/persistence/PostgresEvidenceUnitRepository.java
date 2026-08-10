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
                        EvidenceUnit source = units.get(from);
                        if (source != null) edges.add(new EvidenceEdge(from,
                                rows.getObject("to_evidence_id", UUID.class),
                                EvidenceEdgeType.valueOf(rows.getString("edge_type")), source.sourceSpans()));
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
}
