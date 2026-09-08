package com.dndmaster.combatmap.infrastructure.persistence;

import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.MapId;
import java.sql.*;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** 공개 이미지 자료를 원본 이미지 버전과 관찰 버전으로 분리해 저장한다. */
public final class PostgresPublicMapImageArtifactStore implements PublicMapImageArtifactStore {
    private final DataSource dataSource;
    public PostgresPublicMapImageArtifactStore(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override public Optional<PublicMapImageArtifact> findByObservation(MapOwnerId owner, MapId mapId, String imageRevision, long observationVersion) {
        return query(owner, mapId, imageRevision, " AND observation_version=?", statement -> statement.setLong(4, observationVersion));
    }
    @Override public Optional<PublicMapImageArtifact> findLatest(MapOwnerId owner, MapId mapId, String imageRevision) {
        return query(owner, mapId, imageRevision, " ORDER BY public_area_revision DESC LIMIT 1", statement -> { });
    }
    @Override public Optional<PublicMapImageArtifact> findByPublicAreaRevision(MapOwnerId owner, MapId mapId, String imageRevision, long publicAreaRevision) {
        return query(owner, mapId, imageRevision, " AND public_area_revision=?", statement -> statement.setLong(4, publicAreaRevision));
    }
    @Override public PublicMapImageArtifact save(PublicMapImageArtifact artifact) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO combat_map_public_image_artifact(owner_player_id,map_id,image_revision,public_area_revision,observation_version,png,covered_cells) VALUES (?,?,?,?,?,?,?)")) {
            statement.setObject(1, artifact.owner().value()); statement.setObject(2, artifact.mapId().value()); statement.setString(3, artifact.imageRevision());
            statement.setLong(4, artifact.publicAreaRevision()); statement.setLong(5, artifact.observationVersion()); statement.setBytes(6, artifact.png()); statement.setString(7, encode(artifact.coveredCells())); statement.executeUpdate();
            return artifact;
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) return findByObservation(artifact.owner(), artifact.mapId(), artifact.imageRevision(), artifact.observationVersion())
                    .orElseThrow(() -> new CombatMapPersistenceException("public map image save conflicted", exception));
            throw new CombatMapPersistenceException("public map image save failed", exception);
        }
    }
    private Optional<PublicMapImageArtifact> query(MapOwnerId owner, MapId mapId, String imageRevision, String suffix, SqlBinder binder) {
        String sql = "SELECT public_area_revision,observation_version,png,covered_cells FROM combat_map_public_image_artifact WHERE owner_player_id=? AND map_id=? AND image_revision=?" + suffix;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, owner.value()); statement.setObject(2, mapId.value()); statement.setString(3, imageRevision); binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(new PublicMapImageArtifact(owner, mapId, imageRevision, rows.getLong(1), rows.getLong(2), rows.getBytes(3), decode(rows.getString(4)))) : Optional.empty(); }
        } catch (SQLException exception) { throw new CombatMapPersistenceException("public map image load failed", exception); }
    }
    @FunctionalInterface private interface SqlBinder { void bind(PreparedStatement statement) throws SQLException; }
    private static String encode(java.util.Set<com.dndmaster.combatmap.domain.GridPosition> cells) { return cells.stream().map(cell -> cell.x() + "," + cell.y()).sorted().collect(java.util.stream.Collectors.joining(";")); }
    private static java.util.Set<com.dndmaster.combatmap.domain.GridPosition> decode(String value) { if (value == null || value.isBlank()) return java.util.Set.of(); java.util.Set<com.dndmaster.combatmap.domain.GridPosition> result = new java.util.HashSet<>(); for (String cell : value.split(";")) { String[] point = cell.split(",", -1); if (point.length != 2) throw new IllegalArgumentException("public map image coverage is invalid"); result.add(new com.dndmaster.combatmap.domain.GridPosition(Integer.parseInt(point[0]), Integer.parseInt(point[1]))); } return result; }
}
