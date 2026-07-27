package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.session.AdventureSessionStartOutboxRepository;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.sql.SQLException;
import java.util.UUID;
import java.util.List;
import javax.sql.DataSource;

public final class PostgresAdventureSessionStartOutboxRepository implements AdventureSessionStartOutboxRepository {
    private final DataSource dataSource;
    public PostgresAdventureSessionStartOutboxRepository(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }
    @Override public void prepare(SessionId sessionId, UUID requestId, UUID adventureId, UUID scenarioPackageId) {
        execute("INSERT INTO adventure_session_start_outbox(session_id, request_id, adventure_id, scenario_package_id, status) VALUES (?, ?, ?, ?, 'PREPARED') ON CONFLICT (session_id, request_id) DO UPDATE SET status='PREPARED'", sessionId.value(), requestId, adventureId, scenarioPackageId);
    }
    @Override public void commit(SessionId sessionId, UUID requestId) {
        execute("UPDATE adventure_session_start_outbox SET status='COMMITTED', completed_at=CURRENT_TIMESTAMP WHERE session_id=? AND request_id=?", sessionId.value(), requestId);
    }
    @Override public void requestCharacterSheetDeletion(SessionId sessionId, List<UUID> characterSheetIds) {
        try {
            execute("INSERT INTO adventure_session_character_sheet_deletion_outbox(session_id, character_sheet_ids_json, status) VALUES (?, ?::jsonb, 'PENDING')", sessionId.value(), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(characterSheetIds));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AdventurePersistenceException("could not serialize character sheet deletion event", exception);
        }
    }
    private void execute(String sql, Object... values) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        } catch (SQLException exception) { throw new AdventurePersistenceException("could not update adventure session start outbox", exception); }
    }
}
