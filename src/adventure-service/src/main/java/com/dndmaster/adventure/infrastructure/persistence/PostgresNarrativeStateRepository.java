package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.NarrativeStateRepository;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresNarrativeStateRepository implements NarrativeStateRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    public PostgresNarrativeStateRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource);
        this.objectMapper = objectMapper;
    }
    @Override public Optional<NarrativeState> findBySessionId(UUID sessionId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT state_json FROM adventure_narrative_state WHERE session_id = ?")) {
            s.setObject(1, sessionId); try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(read(r.getString(1))) : Optional.empty(); }
        } catch (SQLException e) { throw new RuntimeException("could not load narrative state", e); }
    }
    @Override public void save(UUID sessionId, NarrativeState state) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO adventure_narrative_state(session_id, state_version, state_json) VALUES (?, ?, ?::jsonb)
                ON CONFLICT (session_id) DO UPDATE SET state_version = EXCLUDED.state_version, state_json = EXCLUDED.state_json
                WHERE adventure_narrative_state.state_version < EXCLUDED.state_version
                """)) {
            s.setObject(1, sessionId); s.setLong(2, state.version()); s.setString(3, write(state));
            if (s.executeUpdate() != 1) throw new IllegalStateException("state version conflict");
        } catch (SQLException e) { throw new RuntimeException("could not save narrative state", e); }
    }
    private NarrativeState read(String json) throws SQLException { try { return objectMapper.readValue(json, NarrativeState.class); } catch (Exception e) { throw new SQLException(e); } }
    private String write(NarrativeState state) throws SQLException { try { return objectMapper.writeValueAsString(state); } catch (Exception e) { throw new SQLException(e); } }
}
