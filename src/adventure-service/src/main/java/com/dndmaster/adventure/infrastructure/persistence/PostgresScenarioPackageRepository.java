package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresScenarioPackageRepository implements ScenarioPackageRepository {
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final DataSource dataSource;

    public PostgresScenarioPackageRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) {
        return find("input_fingerprint", fingerprint);
    }

    @Override
    public Optional<ScenarioPackage> findById(UUID packageId) {
        return find("package_id", packageId);
    }

    private Optional<ScenarioPackage> find(String column, Object value) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT package_id, bundle_id, bundle_revision, input_fingerprint, report_status, report_warnings, character_limit, character_limit_source_document_id, character_limit_source_extraction_version, character_limit_source_locator, character_limit_source_quote, character_creation_blueprint_json FROM scenario_package WHERE " + column + " = ?")) {
            statement.setObject(1, value);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                UUID packageId = row.getObject("package_id", UUID.class);
                return Optional.of(ScenarioPackage.rehydrate(
                        packageId,
                        new ScenarioBundleId(row.getObject("bundle_id", UUID.class)),
                        row.getLong("bundle_revision"),
                        row.getString("input_fingerprint"),
                        readDocuments(connection, packageId),
                        readUnits(connection, packageId),
                        new ScenarioCompilationReport(
                                ResolutionStatus.valueOf(row.getString("report_status")),
                                readArray(row.getArray("report_warnings"))),
                        readCharacterLimit(row), readBlueprint(row.getString("character_creation_blueprint_json"))));
            }
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not load scenario package", exception);
        }
    }

    @Override
    public void save(ScenarioPackage scenarioPackage) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertHeader(connection, scenarioPackage);
                insertDocuments(connection, scenarioPackage);
                insertUnits(connection, scenarioPackage);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception instanceof RuntimeException runtime
                        ? runtime : new ScenarioPackagePersistenceException("could not save scenario package", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not access scenario package storage", exception);
        }
    }

    @Override
    public void saveBlueprint(UUID packageId, CharacterCreationBlueprint blueprint) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE scenario_package SET character_creation_blueprint_json = ? WHERE package_id = ?")) {
            statement.setString(1, writeBlueprint(blueprint));
            statement.setObject(2, packageId);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("scenario package not found");
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not update character creation blueprint", exception);
        }
    }

    private static void insertHeader(Connection connection, ScenarioPackage packageVersion) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO scenario_package(package_id, bundle_id, bundle_revision, input_fingerprint, report_status, report_warnings, character_limit, character_limit_source_document_id, character_limit_source_extraction_version, character_limit_source_locator, character_limit_source_quote, character_creation_blueprint_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setObject(1, packageVersion.packageId());
            insert.setObject(2, packageVersion.bundleId().value());
            insert.setLong(3, packageVersion.bundleRevision());
            insert.setString(4, packageVersion.inputFingerprint());
            insert.setString(5, packageVersion.report().status().name());
            insert.setArray(6, connection.createArrayOf("text", packageVersion.report().warnings().toArray()));
            insert.setInt(7, packageVersion.characterLimit().maximumCharacters());
            var source = packageVersion.characterLimit().source().orElse(null);
            if (source == null) {
                insert.setNull(8, java.sql.Types.OTHER); insert.setNull(9, java.sql.Types.BIGINT); insert.setNull(10, java.sql.Types.VARCHAR);
            } else {
                insert.setObject(8, source.knowledgeDocumentId().value()); insert.setLong(9, source.extractionVersion()); insert.setString(10, source.locator());
            }
            insert.setString(11, packageVersion.characterLimit().sourceQuote());
            insert.setString(12, writeBlueprint(packageVersion.characterCreationBlueprint()));
            insert.executeUpdate();
        }
    }

    private static void insertDocuments(Connection connection, ScenarioPackage packageVersion) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO scenario_package_document(package_id, selection_order, knowledge_document_id, document_type, original_filename, document_role, knowledge_document_status, extraction_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int index = 0; index < packageVersion.documents().size(); index++) {
                ScenarioBundleDocumentSelection document = packageVersion.documents().get(index);
                insert.setObject(1, packageVersion.packageId()); insert.setInt(2, index);
                insert.setObject(3, document.knowledgeDocumentId().value()); insert.setString(4, document.documentType());
                insert.setString(5, document.originalFilename()); insert.setString(6, document.role().name());
                insert.setString(7, document.status().name()); insert.setLong(8, document.extractionVersion());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void insertUnits(Connection connection, ScenarioPackage packageVersion) throws SQLException {
        try (PreparedStatement unit = connection.prepareStatement(
                "INSERT INTO scenario_package_resolution_unit(package_id, unit_order, resolution_kind, ability_or_skill, dc, dice_expression, visibility, source_quote, provenance, detail_json, status, validation_messages) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                PreparedStatement ref = connection.prepareStatement(
                        "INSERT INTO scenario_package_resolution_source_ref(package_id, unit_order, ref_order, knowledge_document_id, extraction_version, locator) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int index = 0; index < packageVersion.units().size(); index++) {
                ScenarioResolutionUnit resolution = packageVersion.units().get(index);
                unit.setObject(1, packageVersion.packageId()); unit.setInt(2, index);
                if (resolution.kind() == null) unit.setNull(3, java.sql.Types.VARCHAR); else unit.setString(3, resolution.kind().name());
                unit.setString(4, resolution.abilityOrSkill());
                if (resolution.dc() == null) unit.setNull(5, java.sql.Types.INTEGER); else unit.setInt(5, resolution.dc());
                unit.setString(6, resolution.diceExpression()); unit.setString(7, resolution.visibility().name());
                unit.setString(8, resolution.sourceQuote()); unit.setString(9, resolution.provenance());
                unit.setString(10, writeDetail(resolution.detail()));
                unit.setString(11, resolution.status().name());
                unit.setArray(12, connection.createArrayOf("text", resolution.validationMessages().toArray()));
                unit.addBatch();
                for (int refIndex = 0; refIndex < resolution.sourceRefs().size(); refIndex++) {
                    ScenarioSourceReference source = resolution.sourceRefs().get(refIndex);
                    ref.setObject(1, packageVersion.packageId()); ref.setInt(2, index); ref.setInt(3, refIndex);
                    ref.setObject(4, source.knowledgeDocumentId().value()); ref.setLong(5, source.extractionVersion());
                    ref.setString(6, source.locator()); ref.addBatch();
                }
            }
            unit.executeBatch(); ref.executeBatch();
        }
    }

    private static List<ScenarioBundleDocumentSelection> readDocuments(Connection connection, UUID packageId) throws SQLException {
        List<ScenarioBundleDocumentSelection> documents = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT knowledge_document_id, document_type, original_filename, document_role, knowledge_document_status, extraction_version FROM scenario_package_document WHERE package_id = ? ORDER BY selection_order")) {
            statement.setObject(1, packageId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) documents.add(new ScenarioBundleDocumentSelection(
                        new KnowledgeDocumentId(rows.getObject("knowledge_document_id", UUID.class)),
                        ScenarioBundleDocumentRole.valueOf(rows.getString("document_role")),
                        KnowledgeDocumentStatus.valueOf(rows.getString("knowledge_document_status")),
                        rows.getString("original_filename"), rows.getString("document_type"), rows.getLong("extraction_version")));
            }
        }
        return documents;
    }

    private static List<ScenarioResolutionUnit> readUnits(Connection connection, UUID packageId) throws SQLException {
        List<ScenarioResolutionUnit> units = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT unit_order, resolution_kind, ability_or_skill, dc, dice_expression, visibility, source_quote, provenance, detail_json, status, validation_messages FROM scenario_package_resolution_unit WHERE package_id = ? ORDER BY unit_order")) {
            statement.setObject(1, packageId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int order = rows.getInt("unit_order");
                    String kind = rows.getString("resolution_kind");
                    units.add(new ScenarioResolutionUnit(
                            kind == null ? null : ResolutionKind.valueOf(kind), rows.getString("ability_or_skill"),
                            (Integer) rows.getObject("dc"), rows.getString("dice_expression"),
                            ResolutionVisibility.valueOf(rows.getString("visibility")), rows.getString("source_quote"),
                            readRefs(connection, packageId, order), rows.getString("provenance"),
                            readDetail(rows.getString("detail_json")),
                            ResolutionStatus.valueOf(rows.getString("status")), readArray(rows.getArray("validation_messages"))));
                }
            }
        }
        return units;
    }

    private static List<ScenarioSourceReference> readRefs(Connection connection, UUID packageId, int unitOrder) throws SQLException {
        List<ScenarioSourceReference> refs = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT knowledge_document_id, extraction_version, locator FROM scenario_package_resolution_source_ref WHERE package_id = ? AND unit_order = ? ORDER BY ref_order")) {
            statement.setObject(1, packageId); statement.setInt(2, unitOrder);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) refs.add(new ScenarioSourceReference(
                        new KnowledgeDocumentId(rows.getObject("knowledge_document_id", UUID.class)),
                        rows.getLong("extraction_version"), rows.getString("locator")));
            }
        }
        return refs;
    }

    private static List<String> readArray(java.sql.Array array) throws SQLException {
        if (array == null) return List.of();
        Object value = array.getArray();
        if (!(value instanceof Object[] values)) return List.of();
        return java.util.Arrays.stream(values).map(String::valueOf).toList();
    }

    private static CharacterLimit readCharacterLimit(ResultSet row) throws SQLException {
        UUID documentId = row.getObject("character_limit_source_document_id", UUID.class);
        if (documentId == null) return CharacterLimit.defaultLimit();
        return new CharacterLimit(row.getInt("character_limit"), new ScenarioSourceReference(
                new KnowledgeDocumentId(documentId), row.getLong("character_limit_source_extraction_version"),
                row.getString("character_limit_source_locator")), row.getString("character_limit_source_quote"));
    }

    private static String writeDetail(ScenarioResolutionDetail detail) {
        try {
            return JSON.writeValueAsString(detail == null ? ScenarioResolutionDetail.empty() : detail);
        } catch (JsonProcessingException exception) {
            throw new ScenarioPackagePersistenceException("could not serialize scenario resolution detail", exception);
        }
    }

    private static String writeBlueprint(CharacterCreationBlueprint blueprint) {
        if (blueprint == null) return null;
        try {
            return JSON.writeValueAsString(blueprint);
        } catch (JsonProcessingException exception) {
            throw new ScenarioPackagePersistenceException("could not serialize character creation blueprint", exception);
        }
    }

    private static CharacterCreationBlueprint readBlueprint(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return JSON.readValue(value, CharacterCreationBlueprint.class);
        } catch (JsonProcessingException exception) {
            throw new ScenarioPackagePersistenceException("could not read character creation blueprint", exception);
        }
    }

    private static ScenarioResolutionDetail readDetail(String value) {
        if (value == null || value.isBlank()) return ScenarioResolutionDetail.empty();
        try {
            return JSON.readValue(value, ScenarioResolutionDetail.class);
        } catch (JsonProcessingException exception) {
            throw new ScenarioPackagePersistenceException("could not read scenario resolution detail", exception);
        }
    }
}
