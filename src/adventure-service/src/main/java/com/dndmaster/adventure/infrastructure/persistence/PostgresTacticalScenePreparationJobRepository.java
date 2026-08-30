package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.TacticalScenePreparationJobRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresTacticalScenePreparationJobRepository implements TacticalScenePreparationJobRepository {
    private final DataSource dataSource;
    public PostgresTacticalScenePreparationJobRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override public Job createOrGet(UUID sessionId, UUID ownerId, int position, String stageName, boolean mapRequired) {
        String sql = "INSERT INTO tactical_scene_preparation_job(job_id,session_id,owner_player_id,stage_position,stage_name,status,progress,attempts,map_required,message,updated_at) VALUES (?,?,?,?,?,'QUEUED',0,0,?,?,?) ON CONFLICT (session_id,stage_position) DO NOTHING";
        try (var c = dataSource.getConnection(); var s = c.prepareStatement(sql)) {
            s.setObject(1, UUID.randomUUID()); s.setObject(2, sessionId); s.setObject(3, ownerId); s.setInt(4, position); s.setString(5, stageName); s.setBoolean(6, mapRequired); s.setString(7, "대기 중"); s.setTimestamp(8, Timestamp.from(Instant.now())); s.executeUpdate();
            return find(sessionId, position).orElseThrow();
        } catch (SQLException e) { throw new AdventurePersistenceException("could not create tactical preparation job", e); }
    }
    @Override public Optional<Job> find(UUID sessionId, int position) { return query("SELECT * FROM tactical_scene_preparation_job WHERE session_id=? AND stage_position=?", s -> { s.setObject(1, sessionId); s.setInt(2, position); }); }
    @Override public List<Job> findUnfinished() {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT * FROM tactical_scene_preparation_job WHERE status IN ('QUEUED','RUNNING')")) {
            try (var rows = s.executeQuery()) { List<Job> result = new ArrayList<>(); while (rows.next()) result.add(read(rows)); return result; }
        } catch (SQLException e) { throw new AdventurePersistenceException("could not load unfinished tactical preparation jobs", e); }
    }
    @Override public boolean claim(UUID jobId) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("UPDATE tactical_scene_preparation_job SET status='RUNNING', message='전술 장면 준비 중', updated_at=? WHERE job_id=? AND status='QUEUED'")) { s.setTimestamp(1, Timestamp.from(Instant.now())); s.setObject(2, jobId); return s.executeUpdate() == 1; }
        catch (SQLException e) { throw new AdventurePersistenceException("could not claim tactical preparation job", e); }
    }
    @Override public boolean claim(UUID jobId, UUID leaseToken, java.time.Duration lease) {
        String sql = "UPDATE tactical_scene_preparation_job SET status='RUNNING', lease_token=?, lease_until=?, message='전술 장면 준비 중', updated_at=? WHERE job_id=? AND (status='QUEUED' OR (status='RUNNING' AND lease_until < ?))";
        try (var c = dataSource.getConnection(); var s = c.prepareStatement(sql)) {
            Instant until = Instant.now().plus(lease);
            s.setObject(1, leaseToken); s.setTimestamp(2, Timestamp.from(until)); s.setTimestamp(3, Timestamp.from(Instant.now()));
            s.setObject(4, jobId); s.setTimestamp(5, Timestamp.from(Instant.now())); return s.executeUpdate() == 1;
        } catch (SQLException e) { throw new AdventurePersistenceException("could not claim tactical preparation lease", e); }
    }
    @Override public void recoverExpiredLeases(Instant now) {
        execute("UPDATE tactical_scene_preparation_job SET status='QUEUED', lease_token=NULL, lease_until=NULL, message='만료된 준비 작업을 복원했습니다.', updated_at=? WHERE status='RUNNING' AND lease_until < ?",
                s -> { s.setTimestamp(1, Timestamp.from(now)); s.setTimestamp(2, Timestamp.from(now)); });
    }
    @Override public void update(UUID jobId, Status status, int progress, int attempts, String message, String reason) { execute("UPDATE tactical_scene_preparation_job SET status=?,progress=?,attempts=?,message=?,failure_reason=?,updated_at=? WHERE job_id=?", s -> { s.setString(1, status.name()); s.setInt(2, progress); s.setInt(3, attempts); s.setString(4, message); s.setString(5, reason); s.setTimestamp(6, Timestamp.from(Instant.now())); s.setObject(7, jobId); }); }
    @Override public void resetForRetry(UUID jobId) { execute("UPDATE tactical_scene_preparation_job SET status='QUEUED',progress=0,attempts=0,message='대기 중',failure_reason=NULL,updated_at=? WHERE job_id=? AND status='FAILED_RETRYABLE'", s -> { s.setTimestamp(1, Timestamp.from(Instant.now())); s.setObject(2, jobId); }); }
    private Optional<Job> query(String sql, Binder binder) { try (var c = dataSource.getConnection(); var s = c.prepareStatement(sql)) { binder.bind(s); try (var rows = s.executeQuery()) { return rows.next() ? Optional.of(read(rows)) : Optional.empty(); } } catch (SQLException e) { throw new AdventurePersistenceException("could not load tactical preparation job", e); } }
    private void execute(String sql, Binder binder) { try (var c = dataSource.getConnection(); var s = c.prepareStatement(sql)) { binder.bind(s); s.executeUpdate(); } catch (SQLException e) { throw new AdventurePersistenceException("could not update tactical preparation job", e); } }
    private static Job read(ResultSet r) throws SQLException { return new Job(r.getObject("job_id", UUID.class), r.getObject("session_id", UUID.class), r.getObject("owner_player_id", UUID.class), r.getInt("stage_position"), r.getString("stage_name"), Status.valueOf(r.getString("status")), r.getInt("progress"), r.getInt("attempts"), r.getBoolean("map_required"), r.getString("message"), r.getString("failure_reason"), r.getTimestamp("updated_at").toInstant()); }
    @FunctionalInterface private interface Binder { void bind(java.sql.PreparedStatement statement) throws SQLException; }
}
