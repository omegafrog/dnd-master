package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.StoryPlanRevisionRepository;
import com.dndmaster.adventure.domain.runtime.plan.AdventureStoryPlanRevision;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresStoryPlanRevisionRepository implements StoryPlanRevisionRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    public PostgresStoryPlanRevisionRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource); this.mapper = mapper;
    }
    public Optional<AdventureStoryPlanRevision> current(UUID sessionId) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT r.revision_id,r.session_id,r.plan_version,r.predecessor_revision_id,r.cause_turn_id,r.stages_json FROM adventure_story_plan_current p JOIN adventure_story_plan_revision r ON r.revision_id=p.revision_id AND r.session_id=p.session_id WHERE p.session_id=?")) {
            s.setObject(1, sessionId); try (var rows = s.executeQuery()) { if (rows.next()) return Optional.of(read(rows)); }
            try (var legacy = c.prepareStatement("SELECT plan_id,plan_version,stages_json FROM adventure_story_plan WHERE session_id=?")) {
                legacy.setObject(1, sessionId);
                try (var rows = legacy.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    List<AdventureStoryPlanStage> oldStages = mapper.readValue(rows.getString(3), new TypeReference<List<AdventureStoryPlanStage>>() {});
                    var seeded = new AdventureStoryPlanRevision(rows.getObject(1, UUID.class), sessionId, rows.getLong(2), null, rows.getObject(1, UUID.class), oldStages.stream().map(stage -> stage.title() + ":" + stage.goal() + ":" + stage.conflict()).toList());
                    append(seeded);
                    return Optional.of(seeded);
                }
            }
        } catch (Exception e) { throw new AdventurePersistenceException("could not load current story plan revision", e); }
    }
    public List<AdventureStoryPlanRevision> history(UUID sessionId) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT revision_id,session_id,plan_version,predecessor_revision_id,cause_turn_id,stages_json FROM adventure_story_plan_revision WHERE session_id=? ORDER BY plan_version")) {
            s.setObject(1, sessionId); var result = new java.util.ArrayList<AdventureStoryPlanRevision>(); try (var rows = s.executeQuery()) { while (rows.next()) result.add(read(rows)); } return result;
        } catch (Exception e) { throw new AdventurePersistenceException("could not load story plan revision history", e); }
    }
    public void append(AdventureStoryPlanRevision revision) {
        try (var c = dataSource.getConnection()) {
            boolean externalTransaction = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
            boolean previous = c.getAutoCommit(); if (!externalTransaction) c.setAutoCommit(false);
            try (var insert = c.prepareStatement("INSERT INTO adventure_story_plan_revision(revision_id,session_id,plan_version,predecessor_revision_id,cause_turn_id,stages_json,created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
                insert.setObject(1, revision.revisionId()); insert.setObject(2, revision.sessionId()); insert.setLong(3, revision.version()); insert.setObject(4, revision.predecessorRevisionId()); insert.setObject(5, revision.causeTurnId()); insert.setString(6, mapper.writeValueAsString(revision.stages())); insert.executeUpdate();
            }
            try (var pointer = c.prepareStatement("INSERT INTO adventure_story_plan_current(session_id,revision_id,updated_at) VALUES (?,?,CURRENT_TIMESTAMP) ON CONFLICT(session_id) DO UPDATE SET revision_id=EXCLUDED.revision_id,updated_at=CURRENT_TIMESTAMP")) { pointer.setObject(1, revision.sessionId()); pointer.setObject(2, revision.revisionId()); pointer.executeUpdate(); }
            if (!externalTransaction) { c.commit(); c.setAutoCommit(previous); }
        } catch (Exception e) { throw new AdventurePersistenceException("could not append story plan revision", e); }
    }
    private AdventureStoryPlanRevision read(java.sql.ResultSet row) throws Exception {
        return new AdventureStoryPlanRevision(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getLong(3), row.getObject(4, UUID.class), row.getObject(5, UUID.class), mapper.readValue(row.getString(6), new TypeReference<List<String>>() {}));
    }
}
