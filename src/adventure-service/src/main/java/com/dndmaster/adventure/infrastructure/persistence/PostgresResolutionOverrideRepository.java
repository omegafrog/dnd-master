package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionOverrideRepository;
import com.dndmaster.adventure.domain.scenario.ResolutionOverride;
import com.dndmaster.adventure.domain.scenario.ResolutionOverrideRevision;
import com.dndmaster.adventure.domain.scenario.ResolutionOverrideStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresResolutionOverrideRepository implements ResolutionOverrideRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public PostgresResolutionOverrideRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public List<ResolutionOverride> findByBundleId(ScenarioBundleId bundleId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT override_id, bundle_id, owner_player_id, revision, author, reason, created_at, updated_at, status, anchor_fingerprint, document_fingerprint, content_fingerprint, quote_fingerprint, context_fingerprint, locator_fingerprint, unit_fingerprint, replacement_candidate_json, revision_history FROM scenario_resolution_override WHERE bundle_id = ? ORDER BY updated_at DESC")) {
            statement.setObject(1, bundleId.value());
            try (ResultSet rows = statement.executeQuery()) {
                List<ResolutionOverride> overrides = new ArrayList<>();
                while (rows.next()) {
                    overrides.add(read(rows));
                }
                return overrides;
            }
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not load resolution overrides", exception);
        }
    }

    @Override
    public void saveAll(List<ResolutionOverride> overrides) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (ResolutionOverride override : overrides) {
                    upsert(connection, override);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception instanceof RuntimeException runtime
                        ? runtime : new ScenarioPackagePersistenceException("could not save resolution overrides", exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not access resolution override storage", exception);
        }
    }

    private static void upsert(Connection connection, ResolutionOverride override) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scenario_resolution_override(override_id, bundle_id, owner_player_id, revision, author, reason, created_at, updated_at, status, anchor_fingerprint, document_fingerprint, content_fingerprint, quote_fingerprint, context_fingerprint, locator_fingerprint, unit_fingerprint, replacement_candidate_json, revision_history) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (override_id) DO UPDATE SET bundle_id = EXCLUDED.bundle_id, owner_player_id = EXCLUDED.owner_player_id, revision = EXCLUDED.revision, author = EXCLUDED.author, reason = EXCLUDED.reason, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at, status = EXCLUDED.status, anchor_fingerprint = EXCLUDED.anchor_fingerprint, document_fingerprint = EXCLUDED.document_fingerprint, content_fingerprint = EXCLUDED.content_fingerprint, quote_fingerprint = EXCLUDED.quote_fingerprint, context_fingerprint = EXCLUDED.context_fingerprint, locator_fingerprint = EXCLUDED.locator_fingerprint, unit_fingerprint = EXCLUDED.unit_fingerprint, replacement_candidate_json = EXCLUDED.replacement_candidate_json, revision_history = EXCLUDED.revision_history")) {
            statement.setObject(1, override.overrideId());
            statement.setObject(2, override.bundleId().value());
            statement.setObject(3, override.ownerPlayerId().value());
            statement.setLong(4, override.revision());
            statement.setString(5, override.author());
            statement.setString(6, override.reason());
            statement.setTimestamp(7, Timestamp.from(override.createdAt()));
            statement.setTimestamp(8, Timestamp.from(override.updatedAt()));
            statement.setString(9, override.status().name());
            statement.setString(10, override.anchorFingerprint());
            statement.setString(11, override.documentFingerprint());
            statement.setString(12, override.contentFingerprint());
            statement.setString(13, override.quoteFingerprint());
            statement.setString(14, override.contextFingerprint());
            statement.setString(15, override.locatorFingerprint());
            statement.setString(16, override.unitFingerprint());
            statement.setString(17, writeCandidate(override.replacementCandidate()));
            statement.setArray(18, connection.createArrayOf("text", override.revisions().stream().map(PostgresResolutionOverrideRepository::writeRevision).toArray()));
            statement.executeUpdate();
        }
    }

    private static ResolutionOverride read(ResultSet row) throws SQLException {
        return new ResolutionOverride(
                row.getObject("override_id", UUID.class),
                new ScenarioBundleId(row.getObject("bundle_id", UUID.class)),
                new com.dndmaster.adventure.domain.scenario.OwnerPlayerId(row.getObject("owner_player_id", UUID.class)),
                row.getLong("revision"),
                row.getString("author"),
                row.getString("reason"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                ResolutionOverrideStatus.valueOf(row.getString("status")),
                row.getString("anchor_fingerprint"),
                row.getString("document_fingerprint"),
                row.getString("content_fingerprint"),
                row.getString("quote_fingerprint"),
                row.getString("context_fingerprint"),
                row.getString("locator_fingerprint"),
                row.getString("unit_fingerprint"),
                readCandidate(row.getString("replacement_candidate_json")),
                readRevisions(row.getArray("revision_history")));
    }

    private static List<ResolutionOverrideRevision> readRevisions(java.sql.Array array) throws SQLException {
        if (array == null) return List.of();
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values) || values.length == 0) return List.of();
        List<ResolutionOverrideRevision> revisions = new ArrayList<>();
        for (Object value : values) {
            if (value == null) continue;
            String[] parts = String.valueOf(value).split("\\|", -1);
            revisions.add(new ResolutionOverrideRevision(
                    Long.parseLong(parts[0]),
                    parts[1],
                    parts[2],
                    Instant.parse(parts[3]),
                    Instant.parse(parts[4]),
                    ResolutionOverrideStatus.valueOf(parts[5])));
        }
        return revisions;
    }

    private static String writeRevision(ResolutionOverrideRevision revision) {
        return revision.revision() + "|" + revision.author() + "|" + revision.reason() + "|"
                + revision.createdAt() + "|" + revision.updatedAt() + "|" + revision.status();
    }

    private static String writeCandidate(ResolutionCandidate candidate) {
        try {
            return JSON.writeValueAsString(candidate);
        } catch (JsonProcessingException exception) {
            throw new ScenarioPackagePersistenceException("could not serialize resolution candidate", exception);
        }
    }

    private static ResolutionCandidate readCandidate(String json) {
        try {
            return JSON.readValue(json, ResolutionCandidate.class);
        } catch (JsonProcessingException exception) {
            throw new ScenarioPackagePersistenceException("could not deserialize resolution candidate", exception);
        }
    }
}
