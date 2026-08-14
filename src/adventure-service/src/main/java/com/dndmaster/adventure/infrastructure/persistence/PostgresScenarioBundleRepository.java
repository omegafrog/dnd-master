package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.RulebookEdition;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresScenarioBundleRepository implements ScenarioBundleRepository {
    private final DataSource dataSource;

    public PostgresScenarioBundleRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<ScenarioSourceBundle> findById(ScenarioBundleId bundleId) {
        String bundleSql = "SELECT bundle_id, owner_player_id, name, rulebook_edition, current_revision FROM scenario_source_bundle WHERE bundle_id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(bundleSql)) {
            statement.setObject(1, bundleId.value());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                UUID bundleUuid = rows.getObject("bundle_id", UUID.class);
                List<ScenarioSourceBundleRevision> revisions = readRevisions(connection, bundleUuid);
                if (revisions.isEmpty()) {
                    revisions = List.of(readRevision(connection, bundleUuid, rows.getLong("current_revision")));
                }
                return Optional.of(ScenarioSourceBundle.rehydrate(
                        new ScenarioBundleId(bundleUuid),
                        new OwnerPlayerId(rows.getObject("owner_player_id", UUID.class)),
                        rows.getString("name"),
                        RulebookEdition.valueOf(rows.getString("rulebook_edition")),
                        revisions));
            }
        } catch (SQLException exception) {
            throw new ScenarioBundlePersistenceException("could not load scenario bundle", exception);
        }
    }

    @Override
    public List<ScenarioSourceBundle> findByOwnerId(UUID ownerPlayerId) {
        List<UUID> bundleIds = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT bundle_id FROM scenario_source_bundle WHERE owner_player_id = ? ORDER BY bundle_id")) {
            statement.setObject(1, ownerPlayerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) bundleIds.add(rows.getObject("bundle_id", UUID.class));
            }
        } catch (SQLException exception) {
            throw new ScenarioBundlePersistenceException("could not list scenario bundles", exception);
        }
        return bundleIds.stream().map(id -> findById(new ScenarioBundleId(id)).orElseThrow()).toList();
    }

    @Override
    public void deleteById(ScenarioBundleId bundleId) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (hasActiveAdventureReferences(connection, bundleId)) {
                    throw new com.dndmaster.adventure.domain.scenario.ScenarioBundleDeletionConflictException();
                }
                execute(connection, "DELETE FROM scenario_compilation WHERE bundle_id = ?", bundleId.value());
                execute(connection, "DELETE FROM scenario_package WHERE bundle_id = ?", bundleId.value());
                execute(connection, "DELETE FROM scenario_source_bundle WHERE bundle_id = ?", bundleId.value());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception instanceof RuntimeException runtime ? runtime
                        : new ScenarioBundlePersistenceException("could not delete scenario bundle", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new ScenarioBundlePersistenceException("could not access scenario bundle storage", exception);
        }
    }

    @Override
    public boolean hasActiveAdventureReferences(ScenarioBundleId bundleId) {
        try (Connection connection = dataSource.getConnection()) {
            return hasActiveAdventureReferences(connection, bundleId);
        } catch (SQLException exception) {
            throw new ScenarioBundlePersistenceException("could not check scenario bundle references", exception);
        }
    }

    private static boolean hasActiveAdventureReferences(Connection connection, ScenarioBundleId bundleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM adventure_session s JOIN scenario_package p ON p.package_id = s.scenario_package_id "
                        + "WHERE p.bundle_id = ? AND s.status NOT IN ('COMPLETED', 'DELETED') LIMIT 1")) {
            statement.setObject(1, bundleId.value());
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static void execute(Connection connection, String sql, UUID bundleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, bundleId);
            statement.executeUpdate();
        }
    }

    @Override
    public void save(ScenarioSourceBundle bundle) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertBundle(connection, bundle);
                deleteRevisions(connection, bundle.id().value());
                for (ScenarioSourceBundleRevision revision : bundle.revisions()) {
                    insertRevision(connection, bundle.id().value(), revision);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception instanceof RuntimeException runtime ? runtime : new ScenarioBundlePersistenceException(
                        "could not save scenario bundle", exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new ScenarioBundlePersistenceException("could not access scenario bundle storage", exception);
        }
    }

    private static void upsertBundle(Connection connection, ScenarioSourceBundle bundle) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO scenario_source_bundle(bundle_id, owner_player_id, name, rulebook_edition, current_revision) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (bundle_id) DO UPDATE SET owner_player_id = EXCLUDED.owner_player_id, "
                        + "current_revision = EXCLUDED.current_revision")) {
            upsert.setObject(1, bundle.id().value());
            upsert.setObject(2, bundle.ownerPlayerId().value());
            upsert.setString(3, bundle.name());
            upsert.setString(4, bundle.rulebookEdition().name());
            upsert.setLong(5, bundle.currentRevision().revision());
            upsert.executeUpdate();
        }
    }

    private static void deleteRevisions(Connection connection, UUID bundleId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM scenario_source_bundle_revision WHERE bundle_id = ?")) {
            delete.setObject(1, bundleId);
            delete.executeUpdate();
        }
    }

    private static void insertRevision(Connection connection, UUID bundleId, ScenarioSourceBundleRevision revision)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO scenario_source_bundle_revision(bundle_id, revision_number) VALUES (?, ?)")) {
            insert.setObject(1, bundleId);
            insert.setLong(2, revision.revision());
            insert.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO scenario_source_bundle_revision_document(bundle_id, revision_number, selection_order, knowledge_document_id, document_type, original_filename, document_role, knowledge_document_status, extraction_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int index = 0; index < revision.documents().size(); index++) {
                ScenarioBundleDocumentSelection document = revision.documents().get(index);
                insert.setObject(1, bundleId);
                insert.setLong(2, revision.revision());
                insert.setInt(3, index);
                insert.setObject(4, document.knowledgeDocumentId().value());
                insert.setString(5, document.documentType());
                insert.setString(6, document.originalFilename());
                insert.setString(7, document.role().name());
                insert.setString(8, document.status().name());
                insert.setLong(9, document.extractionVersion());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static List<ScenarioSourceBundleRevision> readRevisions(Connection connection, UUID bundleId) throws SQLException {
        List<ScenarioSourceBundleRevision> revisions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision_number FROM scenario_source_bundle_revision WHERE bundle_id = ? ORDER BY revision_number")) {
            statement.setObject(1, bundleId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    revisions.add(readRevision(connection, bundleId, rows.getLong("revision_number")));
                }
            }
        }
        return revisions;
    }

    private static ScenarioSourceBundleRevision readRevision(Connection connection, UUID bundleId, long revisionNumber)
            throws SQLException {
        List<ScenarioBundleDocumentSelection> documents = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT knowledge_document_id, document_type, original_filename, document_role, knowledge_document_status, extraction_version FROM scenario_source_bundle_revision_document WHERE bundle_id = ? AND revision_number = ? ORDER BY selection_order")) {
            statement.setObject(1, bundleId);
            statement.setLong(2, revisionNumber);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    documents.add(new ScenarioBundleDocumentSelection(
                            new KnowledgeDocumentId(rows.getObject("knowledge_document_id", UUID.class)),
                            ScenarioBundleDocumentRole.valueOf(rows.getString("document_role")),
                            KnowledgeDocumentStatus.valueOf(rows.getString("knowledge_document_status")),
                            rows.getString("original_filename"),
                            rows.getString("document_type"),
                            rows.getLong("extraction_version")));
                }
            }
        }
        return new ScenarioSourceBundleRevision(revisionNumber, documents);
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
