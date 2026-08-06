package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.AdventureClockRepository;
import com.dndmaster.adventure.domain.runtime.clock.AdventureClock;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresAdventureClockRepository implements AdventureClockRepository {
    private final DataSource dataSource;
    public PostgresAdventureClockRepository(DataSource dataSource) { this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource); }
    public Optional<AdventureClock> findBySessionId(UUID sessionId) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT clock_version, turns_elapsed, seconds_elapsed, last_cause_turn_id FROM adventure_clock WHERE session_id=?")) {
            s.setObject(1, sessionId);
            try (var rows = s.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(AdventureClock.rehydrate(sessionId, rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getObject(4, UUID.class)));
            }
        } catch (SQLException e) { throw new AdventurePersistenceException("could not load adventure clock", e); }
    }
    public void save(AdventureClock clock, long expectedVersion) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("INSERT INTO adventure_clock(session_id,clock_version,turns_elapsed,seconds_elapsed,last_cause_turn_id,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(session_id) DO UPDATE SET clock_version=EXCLUDED.clock_version,turns_elapsed=EXCLUDED.turns_elapsed,seconds_elapsed=EXCLUDED.seconds_elapsed,last_cause_turn_id=EXCLUDED.last_cause_turn_id,updated_at=CURRENT_TIMESTAMP WHERE adventure_clock.clock_version=?")) {
            s.setObject(1, clock.sessionId()); s.setLong(2, clock.version()); s.setLong(3, clock.turnsElapsed()); s.setLong(4, clock.secondsElapsed()); s.setObject(5, clock.lastCauseTurnId()); s.setLong(6, expectedVersion);
            if (s.executeUpdate() != 1) throw new OptimisticAdventureLockException();
        } catch (SQLException e) { throw new AdventurePersistenceException("could not save adventure clock", e); }
    }
}
