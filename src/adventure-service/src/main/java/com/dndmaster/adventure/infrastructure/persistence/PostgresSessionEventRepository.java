package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresSessionEventRepository implements SessionEventRepository {
    private final DataSource dataSource;
    public PostgresSessionEventRepository(DataSource dataSource) { this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource); }

    @Override public void append(SessionEvent event) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("""
                INSERT INTO adventure_session_event_outbox(event_id, session_id, version, event_type, payload)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """)) {
            s.setObject(1, event.eventId()); s.setObject(2, event.sessionId()); s.setLong(3, event.version());
            s.setString(4, event.type()); s.setString(5, event.payload()); s.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("could not append session event", e); }
    }

    @Override public List<SessionEvent> after(UUID sessionId, long version) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("""
                SELECT event_id, version, event_type, payload FROM adventure_session_event_outbox
                WHERE session_id = ? AND version > ? ORDER BY version, event_id
                """)) {
            s.setObject(1, sessionId); s.setLong(2, version);
            try (var rows = s.executeQuery()) {
                List<SessionEvent> events = new ArrayList<>();
                while (rows.next()) events.add(new SessionEvent(sessionId, (UUID) rows.getObject(1), rows.getLong(2), rows.getString(3), rows.getString(4)));
                return events;
            }
        } catch (SQLException e) { throw new RuntimeException("could not read session events", e); }
    }
}
