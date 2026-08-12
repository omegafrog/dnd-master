package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.application.definition.GameSystemDefinitionRepository;
import com.dndmaster.ruleknowledge.domain.definition.GameSystemDefinitionRevision;
import com.dndmaster.ruleknowledge.domain.definition.GameSystemDefinitionStatus;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresGameSystemDefinitionRepository implements GameSystemDefinitionRepository {
    private final DataSource dataSource;
    public PostgresGameSystemDefinitionRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public Optional<GameSystemDefinitionRevision> findPublished(UUID rulebookId) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT definition_id,rulebook_id,definition_version,status,definition_json,published_at FROM game_system_definition_revision WHERE rulebook_id=? AND status='PUBLISHED' ORDER BY definition_version DESC LIMIT 1")) {
            s.setObject(1, rulebookId);
            try (var rows = s.executeQuery()) { return rows.next() ? Optional.of(read(rows)) : Optional.empty(); }
        } catch (SQLException e) { throw new RuntimeException("could not load game system definition", e); }
    }

    public Optional<GameSystemDefinitionRevision> findPublished(UUID rulebookId, long version) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT definition_id,rulebook_id,definition_version,status,definition_json,published_at FROM game_system_definition_revision WHERE rulebook_id=? AND definition_version=? AND status='PUBLISHED'")) {
            s.setObject(1, rulebookId); s.setLong(2, version);
            try (var rows = s.executeQuery()) { return rows.next() ? Optional.of(read(rows)) : Optional.empty(); }
        } catch (SQLException e) { throw new RuntimeException("could not load game system definition", e); }
    }

    public List<GameSystemDefinitionRevision> history(UUID rulebookId) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT definition_id,rulebook_id,definition_version,status,definition_json,published_at FROM game_system_definition_revision WHERE rulebook_id=? ORDER BY definition_version")) {
            s.setObject(1, rulebookId); var result = new ArrayList<GameSystemDefinitionRevision>();
            try (var rows = s.executeQuery()) { while (rows.next()) result.add(read(rows)); }
            return List.copyOf(result);
        } catch (SQLException e) { throw new RuntimeException("could not load game system definition history", e); }
    }

    public void save(GameSystemDefinitionRevision revision) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("INSERT INTO game_system_definition_revision(definition_id,rulebook_id,definition_version,status,definition_json,published_at) VALUES (?,?,?,?,?,?)")) {
            s.setObject(1, revision.definitionId()); s.setObject(2, revision.rulebookId()); s.setLong(3, revision.version());
            s.setString(4, revision.status().name()); s.setString(5, revision.definitionJson());
            s.setTimestamp(6, revision.publishedAt() == null ? null : java.sql.Timestamp.from(revision.publishedAt()));
            s.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("could not save game system definition", e); }
    }

    private static GameSystemDefinitionRevision read(java.sql.ResultSet row) throws SQLException {
        return new GameSystemDefinitionRevision(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getLong(3),
                GameSystemDefinitionStatus.valueOf(row.getString(4)), row.getString(5),
                row.getTimestamp(6) == null ? null : row.getTimestamp(6).toInstant());
    }
}
