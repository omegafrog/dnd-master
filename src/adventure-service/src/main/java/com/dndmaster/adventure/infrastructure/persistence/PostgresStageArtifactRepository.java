package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresStageArtifactRepository implements StageArtifactRepository {
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final DataSource dataSource;

    public PostgresStageArtifactRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public Optional<StageBackbone> findBackbone(UUID packageId, long revision) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT artifact_json FROM story_stage_backbone WHERE scenario_package_id=? AND backbone_revision=?")) {
            statement.setObject(1, packageId); statement.setLong(2, revision);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(read(row.getString(1), StageBackbone.class));
            }
        } catch (SQLException exception) {
            throw new StageArtifactPersistenceException("could not load stage backbone", exception);
        }
    }

    @Override
    public Optional<DetailedStage> findDetailedStage(UUID packageId, String stageId, long backboneRevision, long revision) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT artifact_json FROM story_detailed_stage WHERE scenario_package_id=? AND stage_id=? AND backbone_revision=? AND detailed_stage_revision=?")) {
            statement.setObject(1, packageId); statement.setString(2, stageId);
            statement.setLong(3, backboneRevision); statement.setLong(4, revision);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(read(row.getString(1), DetailedStage.class));
            }
        } catch (SQLException exception) {
            throw new StageArtifactPersistenceException("could not load detailed stage", exception);
        }
    }

    @Override
    public void saveBackbone(StageBackbone backbone, long expectedRevision) {
        if (backbone.revision() != expectedRevision + 1) throw new IllegalStateException("stage backbone revision is stale");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureNextRevision(connection, "story_stage_backbone", "backbone_revision", "scenario_package_id", backbone.scenarioPackageId(), expectedRevision);
                insert(connection, "INSERT INTO story_stage_backbone(scenario_package_id, backbone_revision, artifact_json) VALUES (?, ?, ?::jsonb)",
                        backbone.scenarioPackageId(), backbone.revision(), write(backbone));
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception instanceof RuntimeException runtime ? runtime : new StageArtifactPersistenceException("could not save stage backbone", exception);
            }
        } catch (SQLException exception) {
            throw new StageArtifactPersistenceException("could not access stage artifact storage", exception);
        }
    }

    @Override
    public void saveDetailedStage(DetailedStage stage, long expectedRevision) {
        if (stage.revision() != expectedRevision + 1) throw new IllegalStateException("detailed stage revision is stale");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureNextDetailedRevision(connection, stage, expectedRevision);
                insert(connection, "INSERT INTO story_detailed_stage(scenario_package_id, backbone_revision, stage_id, detailed_stage_revision, artifact_json) VALUES (?, ?, ?, ?, ?::jsonb)",
                        stage.scenarioPackageId(), stage.backboneRevision(), stage.stageId(), stage.revision(), write(stage));
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception instanceof RuntimeException runtime ? runtime : new StageArtifactPersistenceException("could not save detailed stage", exception);
            }
        } catch (SQLException exception) {
            throw new StageArtifactPersistenceException("could not access stage artifact storage", exception);
        }
    }

    @Override
    public void saveInitialArtifacts(StageBackbone backbone, DetailedStage stage) {
        if (backbone.revision() != 1 || stage.revision() != 1 || !backbone.scenarioPackageId().equals(stage.scenarioPackageId())
                || stage.backboneRevision() != backbone.revision()) {
            throw new IllegalArgumentException("initial artifacts must reference the same revision");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureNextRevision(connection, "story_stage_backbone", "backbone_revision", "scenario_package_id", backbone.scenarioPackageId(), 0);
                insert(connection, "INSERT INTO story_stage_backbone(scenario_package_id, backbone_revision, artifact_json) VALUES (?, ?, ?::jsonb)",
                        backbone.scenarioPackageId(), backbone.revision(), write(backbone));
                ensureNextDetailedRevision(connection, stage, 0);
                insert(connection, "INSERT INTO story_detailed_stage(scenario_package_id, backbone_revision, stage_id, detailed_stage_revision, artifact_json) VALUES (?, ?, ?, ?, ?::jsonb)",
                        stage.scenarioPackageId(), stage.backboneRevision(), stage.stageId(), stage.revision(), write(stage));
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception instanceof RuntimeException runtime ? runtime : new StageArtifactPersistenceException("could not save initial stage artifacts", exception);
            }
        } catch (SQLException exception) {
            throw new StageArtifactPersistenceException("could not access stage artifact storage", exception);
        }
    }

    private void ensureNextRevision(Connection c, String table, String column, String idColumn, UUID id, long expected) throws SQLException {
        long current = currentRevision(c, table, column, idColumn, id);
        if (current != expected) throw new IllegalStateException("stage backbone revision is stale");
    }
    private void ensureNextDetailedRevision(Connection c, DetailedStage stage, long expected) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT COALESCE(MAX(detailed_stage_revision), 0) FROM story_detailed_stage WHERE scenario_package_id=? AND stage_id=? AND backbone_revision=?")) {
            s.setObject(1, stage.scenarioPackageId()); s.setString(2, stage.stageId()); s.setLong(3, stage.backboneRevision());
            try (ResultSet r = s.executeQuery()) { r.next(); if (r.getLong(1) != expected) throw new IllegalStateException("detailed stage revision is stale"); }
        }
    }
    private long currentRevision(Connection c, String table, String column, String idColumn, UUID id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT COALESCE(MAX(" + column + "), 0) FROM " + table + " WHERE " + idColumn + "=?")) {
            s.setObject(1, id); try (ResultSet r = s.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    private static void insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) { for (int i=0; i<values.length; i++) s.setObject(i + 1, values[i]); s.executeUpdate(); }
    }
    private static String write(Object value) { try { return JSON.writeValueAsString(value); } catch (Exception e) { throw new StageArtifactPersistenceException("could not serialize stage artifact", e); } }
    private static <T> T read(String value, Class<T> type) { try { return JSON.readValue(value, type); } catch (Exception e) { throw new StageArtifactPersistenceException("could not deserialize stage artifact", e); } }
}
