package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanHistoryEntry;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresAdventureStoryPlanRepository implements AdventureStoryPlanRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    public PostgresAdventureStoryPlanRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override public Optional<AdventureStoryPlan> findBySessionId(SessionId sessionId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT * FROM adventure_story_plan WHERE session_id=?")) {
            s.setObject(1, sessionId.value());
            try (ResultSet row = s.executeQuery()) { return row.next() ? Optional.of(read(row)) : Optional.empty(); }
        } catch (SQLException e) { throw new AdventurePersistenceException("could not load adventure story plan", e); }
    }

    @Override public void save(AdventureStoryPlan plan) { save(plan, null); }

    @Override public void save(AdventureStoryPlan plan, String requestedCause) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            String stages = mapper.writeValueAsString(plan.stages());
            UUID currentPlanId = null; Long currentVersion = null;
            try (PreparedStatement lock = c.prepareStatement("SELECT plan_id, plan_version FROM adventure_story_plan WHERE session_id=? FOR UPDATE")) {
                lock.setObject(1, plan.sessionId().value());
                try (ResultSet row = lock.executeQuery()) { if (row.next()) { currentPlanId = row.getObject("plan_id", UUID.class); currentVersion = row.getLong("plan_version"); } }
            }
            if (currentVersion != null) {
                if (plan.version() == currentVersion && plan.planId().equals(currentPlanId)) { c.commit(); return; }
                if (plan.version() != currentVersion + 1) throw new IllegalStateException("story plan version is not the next locked revision");
            } else if (plan.version() != 1) {
                throw new IllegalStateException("first story plan version must be 1");
            }
            try (PreparedStatement s = c.prepareStatement("INSERT INTO adventure_story_plan(plan_id, session_id, package_revision, party_revision, plan_version, status, ending_count, adventure_length, current_stage, stages_json, failure_reason, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (session_id) DO UPDATE SET plan_id=EXCLUDED.plan_id, package_revision=EXCLUDED.package_revision, party_revision=EXCLUDED.party_revision, plan_version=EXCLUDED.plan_version, status=EXCLUDED.status, ending_count=EXCLUDED.ending_count, adventure_length=EXCLUDED.adventure_length, current_stage=EXCLUDED.current_stage, stages_json=EXCLUDED.stages_json, failure_reason=EXCLUDED.failure_reason, updated_at=EXCLUDED.updated_at")) {
            s.setObject(1, plan.planId()); s.setObject(2, plan.sessionId().value()); s.setLong(3, plan.packageRevision()); s.setLong(4, plan.partyRevision()); s.setLong(5, plan.version()); s.setString(6, plan.status().name()); s.setInt(7, plan.configuration().endingCount()); s.setString(8, plan.configuration().adventureLength().name()); s.setInt(9, plan.currentStage()); s.setString(10, stages); s.setString(11, plan.failureReason()); s.setObject(12, java.sql.Timestamp.from(plan.updatedAt())); s.executeUpdate();
            }
            UUID historyId = UUID.randomUUID();
            UUID predecessor = null;
            try (PreparedStatement p = c.prepareStatement("SELECT history_id FROM adventure_story_plan_history WHERE session_id=? AND plan_version < ? ORDER BY plan_version DESC, history_id DESC LIMIT 1 FOR UPDATE")) {
                p.setObject(1, plan.sessionId().value()); p.setLong(2, plan.version());
                try (ResultSet row = p.executeQuery()) { if (row.next()) predecessor = row.getObject("history_id", UUID.class); }
            }
            try (PreparedStatement h = c.prepareStatement("INSERT INTO adventure_story_plan_history(history_id, plan_id, session_id, package_revision, party_revision, plan_version, status, ending_count, adventure_length, current_stage, stages_json, failure_reason, recorded_at, cause, predecessor_history_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") ) {
                h.setObject(1, historyId); h.setObject(2, plan.planId()); h.setObject(3, plan.sessionId().value()); h.setLong(4, plan.packageRevision()); h.setLong(5, plan.partyRevision()); h.setLong(6, plan.version()); h.setString(7, plan.status().name()); h.setInt(8, plan.configuration().endingCount()); h.setString(9, plan.configuration().adventureLength().name()); h.setInt(10, plan.currentStage()); h.setString(11, stages); h.setString(12, plan.failureReason()); h.setObject(13, java.sql.Timestamp.from(plan.updatedAt())); h.setString(14, requestedCause == null ? (predecessor == null ? "INITIAL" : "REVISION") : requestedCause); h.setObject(15, predecessor); h.executeUpdate();
            }
            c.commit();
        } catch (Exception e) { throw new AdventurePersistenceException("could not save adventure story plan", e); }
    }

    @Override public List<AdventureStoryPlan> readHistory(SessionId sessionId) {
        return readHistoryEntries(sessionId).stream().map(AdventureStoryPlanHistoryEntry::plan).toList();
    }

    @Override public List<AdventureStoryPlanHistoryEntry> readHistoryEntries(SessionId sessionId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT history_id, plan_id, session_id, package_revision, party_revision, plan_version, status, ending_count, adventure_length, current_stage, stages_json, failure_reason, recorded_at, cause, predecessor_history_id FROM adventure_story_plan_history WHERE session_id=? ORDER BY plan_version")) {
            s.setObject(1, sessionId.value()); List<AdventureStoryPlanHistoryEntry> result = new java.util.ArrayList<>();
            try (ResultSet rows = s.executeQuery()) { while (rows.next()) {
                AdventureStoryPlan plan = AdventureStoryPlan.rehydrate(rows.getObject("plan_id", UUID.class), new SessionId(rows.getObject("session_id", UUID.class)), rows.getLong("package_revision"), rows.getLong("party_revision"), rows.getLong("plan_version"), AdventureStoryPlanStatus.valueOf(rows.getString("status")), new AdventurePlanConfiguration(rows.getInt("ending_count"), AdventureLength.valueOf(rows.getString("adventure_length"))), mapper.readValue(rows.getString("stages_json"), new TypeReference<List<AdventureStoryPlanStage>>() {}), rows.getInt("current_stage"), rows.getString("failure_reason"), rows.getTimestamp("recorded_at").toInstant());
                result.add(new AdventureStoryPlanHistoryEntry(plan, rows.getObject("history_id", UUID.class), rows.getTimestamp("recorded_at").toInstant(), rows.getString("cause"), rows.getObject("predecessor_history_id", UUID.class)));
            }}
            return result;
        } catch (Exception e) { throw new AdventurePersistenceException("could not load story plan history", e); }
    }

    private AdventureStoryPlan read(ResultSet row) throws SQLException {
        try {
            UUID id = row.getObject("plan_id", UUID.class);
            return AdventureStoryPlan.rehydrate(id, new SessionId(row.getObject("session_id", UUID.class)),
                    row.getLong("package_revision"), row.getLong("party_revision"), row.getLong("plan_version"),
                    AdventureStoryPlanStatus.valueOf(row.getString("status")),
                    new AdventurePlanConfiguration(row.getInt("ending_count"), AdventureLength.valueOf(row.getString("adventure_length"))),
                    mapper.readValue(row.getString("stages_json"), new TypeReference<List<AdventureStoryPlanStage>>() {}),
                    row.getInt("current_stage"), row.getString("failure_reason"), row.getTimestamp("updated_at").toInstant());
        } catch (Exception e) { throw new SQLException("could not parse adventure story plan", e); }
    }
}
