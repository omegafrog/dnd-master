package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.session.AdventureSessionStartOutboxRepository;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.sql.SQLException;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
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
    @Override public Optional<AdventureSessionStartOutboxRepository.DeletionEvent> claimNextDeletion() {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var select = connection.prepareStatement("SELECT event_id, session_id, character_sheet_ids_json, attempts FROM adventure_session_character_sheet_deletion_outbox WHERE status IN ('PENDING','FAILED') OR (status = 'PROCESSING' AND COALESCE(processing_started_at, created_at) < CURRENT_TIMESTAMP - INTERVAL '5 minutes') ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1")) {
                try (var rows = select.executeQuery()) {
                    if (!rows.next()) { connection.commit(); return Optional.empty(); }
                    UUID eventId = rows.getObject("event_id", UUID.class);
                    try (var update = connection.prepareStatement("UPDATE adventure_session_character_sheet_deletion_outbox SET status='PROCESSING', attempts=attempts+1, processing_started_at=CURRENT_TIMESTAMP WHERE event_id=?")) { update.setObject(1, eventId); update.executeUpdate(); }
                    var ids = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rows.getString("character_sheet_ids_json"), new com.fasterxml.jackson.core.type.TypeReference<List<UUID>>() {});
                    connection.commit();
                    return Optional.of(new AdventureSessionStartOutboxRepository.DeletionEvent(eventId, rows.getObject("session_id", UUID.class), ids, rows.getInt("attempts") + 1));
                }
            } catch (Exception e) { connection.rollback(); throw new AdventurePersistenceException("could not claim character sheet deletion", e); }
        } catch (SQLException e) { throw new AdventurePersistenceException("could not claim character sheet deletion", e); }
    }
    @Override public void completeDeletion(UUID eventId) { execute("UPDATE adventure_session_character_sheet_deletion_outbox SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP, processing_started_at=NULL WHERE event_id=?", eventId); }
    @Override public void failDeletion(UUID eventId, String reason) { execute("UPDATE adventure_session_character_sheet_deletion_outbox SET status='FAILED', last_error=?, processing_started_at=NULL WHERE event_id=?", reason, eventId); }
    private void execute(String sql, Object... values) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        } catch (SQLException exception) { throw new AdventurePersistenceException("could not update adventure session start outbox", exception); }
    }
}
