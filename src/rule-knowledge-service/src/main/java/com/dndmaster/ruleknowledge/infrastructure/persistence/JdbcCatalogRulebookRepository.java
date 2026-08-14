package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRepository;
import com.dndmaster.ruleknowledge.application.catalog.CatalogRulebookRevision;
import com.dndmaster.ruleknowledge.domain.catalog.CatalogRevisionStatus;
import com.dndmaster.ruleknowledge.domain.catalog.RulebookEdition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public final class JdbcCatalogRulebookRepository implements CatalogRulebookRepository {
    private static final String SELECT = """
            SELECT catalog_revision_id, edition, display_name, rulebook_id, revision_number,
                   status, published, failure_reason, created_at, updated_at
              FROM rulebook_catalog_revision
            """;
    private final DataSource dataSource;

    public JdbcCatalogRulebookRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override public List<CatalogRulebookRevision> findPublished() { return find(" WHERE published = TRUE ORDER BY edition"); }
    @Override public List<CatalogRulebookRevision> findAll() { return find(" ORDER BY edition, revision_number DESC"); }
    @Override public void save(CatalogRulebookRevision revision) {
        String sql = """
                INSERT INTO rulebook_catalog_revision (catalog_revision_id, edition, display_name, rulebook_id, revision_number, status, published, failure_reason, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (catalog_revision_id) DO UPDATE SET
                    status = EXCLUDED.status, published = EXCLUDED.published,
                    failure_reason = EXCLUDED.failure_reason, updated_at = EXCLUDED.updated_at
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, revision.id()); statement.setString(2, revision.edition().name()); statement.setString(3, revision.displayName());
            statement.setObject(4, revision.rulebookId()); statement.setLong(5, revision.revisionNumber()); statement.setString(6, revision.status().name());
            statement.setBoolean(7, revision.published()); statement.setString(8, revision.failureReason());
            statement.setTimestamp(9, java.sql.Timestamp.from(revision.createdAt())); statement.setTimestamp(10, java.sql.Timestamp.from(revision.updatedAt()));
            statement.executeUpdate();
        } catch (SQLException exception) { throw new RuleVectorPersistenceException("could not save rulebook catalog revision", exception); }
    }

    @Override public void publish(CatalogRulebookRevision revision) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var unpublish = connection.prepareStatement("UPDATE rulebook_catalog_revision SET published = FALSE, updated_at = ? WHERE edition = ? AND published = TRUE")) {
                unpublish.setTimestamp(1, java.sql.Timestamp.from(revision.updatedAt()));
                unpublish.setString(2, revision.edition().name());
                unpublish.executeUpdate();
            }
            try (var publish = connection.prepareStatement("UPDATE rulebook_catalog_revision SET status = ?, published = TRUE, failure_reason = NULL, updated_at = ? WHERE catalog_revision_id = ?")) {
                publish.setString(1, revision.status().name());
                publish.setTimestamp(2, java.sql.Timestamp.from(revision.updatedAt()));
                publish.setObject(3, revision.id());
                if (publish.executeUpdate() != 1) throw new SQLException("catalog revision not found");
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not publish rulebook catalog revision", exception);
        }
    }

    private List<CatalogRulebookRevision> find(String suffix) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(SELECT + suffix); var rows = statement.executeQuery()) {
            List<CatalogRulebookRevision> result = new ArrayList<>();
            while (rows.next()) result.add(map(rows));
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new RuleVectorPersistenceException("could not load rulebook catalog", exception);
        }
    }

    private static CatalogRulebookRevision map(ResultSet row) throws SQLException {
        return new CatalogRulebookRevision(
                row.getObject("catalog_revision_id", java.util.UUID.class),
                RulebookEdition.valueOf(row.getString("edition")),
                row.getString("display_name"), row.getObject("rulebook_id", java.util.UUID.class),
                row.getLong("revision_number"), CatalogRevisionStatus.valueOf(row.getString("status")),
                row.getBoolean("published"), row.getString("failure_reason"),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
