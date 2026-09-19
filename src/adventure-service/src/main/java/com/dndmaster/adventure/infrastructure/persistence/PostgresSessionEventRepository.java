package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresSessionEventRepository implements SessionEventRepository {
    private final DataSource dataSource;

    public PostgresSessionEventRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override public void append(SessionEvent event) {
        try (Connection connection = transaction()) {
            if (findById(connection, event.eventId()) != null) {
                SessionEvent existing = findById(connection, event.eventId());
                if (!existing.sessionId().equals(event.sessionId()) || !existing.type().equals(event.type())
                        || !existing.payload().equals(event.payload())) {
                    throw new com.dndmaster.adventure.application.runtime.SessionEventIdentityConflictException(
                            "session event identity conflict");
                }
                connection.commit();
                return;
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO adventure_session_event_outbox(event_id, session_id, version, event_type, payload)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, event.eventId()); statement.setObject(2, event.sessionId());
                statement.setLong(3, event.version()); statement.setString(4, event.type());
                statement.setString(5, event.payload()); statement.executeUpdate();
            }
            advanceCounter(connection, event.sessionId(), event.version() + 1);
            connection.commit();
        } catch (SQLException e) { throw new RuntimeException("could not append session event", e); }
    }

    @Override public SessionEvent appendNext(UUID sessionId, UUID eventId, String type, String payload) {
        try (Connection connection = transaction()) {
            SessionEvent existing = findById(connection, eventId);
            if (existing != null) {
                if (!existing.sessionId().equals(sessionId) || !existing.type().equals(type)
                        || !existing.payload().equals(payload)) {
                    throw new com.dndmaster.adventure.application.runtime.SessionEventIdentityConflictException(
                            "session event identity conflict");
                }
                connection.commit();
                return existing;
            }
            long version;
            try (var statement = connection.prepareStatement("""
                    INSERT INTO adventure_session_event_version_counter(session_id, next_version)
                    VALUES (?, 1)
                    ON CONFLICT (session_id) DO UPDATE
                    SET next_version = adventure_session_event_version_counter.next_version + 1
                    RETURNING next_version - 1
                    """)) {
                statement.setObject(1, sessionId);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("session event version was not allocated");
                    version = rows.getLong(1);
                }
            }
            SessionEvent event = new SessionEvent(sessionId, eventId, version, type, payload);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO adventure_session_event_outbox(event_id, session_id, version, event_type, payload)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, event.eventId()); statement.setObject(2, event.sessionId());
                statement.setLong(3, event.version()); statement.setString(4, event.type());
                statement.setString(5, event.payload()); statement.executeUpdate();
            }
            connection.commit();
            return event;
        } catch (SQLException e) { throw new RuntimeException("could not append session event", e); }
    }

    @Override public List<SessionEvent> after(UUID sessionId, long version) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
                SELECT event_id, version, event_type, payload FROM adventure_session_event_outbox
                WHERE session_id = ? AND version > ? ORDER BY version, event_id
                """)) {
            statement.setObject(1, sessionId); statement.setLong(2, version);
            try (var rows = statement.executeQuery()) {
                List<SessionEvent> events = new ArrayList<>();
                while (rows.next()) events.add(new SessionEvent(sessionId, (UUID) rows.getObject(1), rows.getLong(2),
                        rows.getString(3), rows.getString(4)));
                return events;
            }
        } catch (SQLException e) { throw new RuntimeException("could not read session events", e); }
    }

    private Connection transaction() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private static SessionEvent findById(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT session_id, version, event_type, payload
                FROM adventure_session_event_outbox WHERE event_id = ?
                """)) {
            statement.setObject(1, eventId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new SessionEvent((UUID) rows.getObject(1), eventId, rows.getLong(2), rows.getString(3), rows.getString(4));
            }
        }
    }

    private static void advanceCounter(Connection connection, UUID sessionId, long nextVersion) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO adventure_session_event_version_counter(session_id, next_version) VALUES (?, ?)
                ON CONFLICT (session_id) DO UPDATE
                SET next_version = GREATEST(adventure_session_event_version_counter.next_version, EXCLUDED.next_version)
                """)) {
            statement.setObject(1, sessionId); statement.setLong(2, nextVersion); statement.executeUpdate();
        }
    }
}
