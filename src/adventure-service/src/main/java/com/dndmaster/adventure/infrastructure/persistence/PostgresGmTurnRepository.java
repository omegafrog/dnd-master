package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;
import com.dndmaster.adventure.domain.runtime.GmInput;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresGmTurnRepository implements GmTurnRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public PostgresGmTurnRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource);
        this.mapper = mapper;
    }

    @Override public void lockAdventure(UUID adventureId) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))")) {
            s.setObject(1, adventureId); s.executeQuery();
        } catch (SQLException e) { throw new GmTurnPersistenceException("could not lock adventure turn", e); }
    }

    @Override
    public Optional<GmTurn> findByCommandId(UUID commandId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT * FROM adventure_gm_turn WHERE command_id = ?")) {
            s.setObject(1, commandId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(read(r)) : Optional.empty();
            }
        } catch (SQLException e) { throw new GmTurnPersistenceException("could not load GM turn", e); }
    }

    @Override
    public Optional<GmTurn> findByTurnId(UUID turnId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT * FROM adventure_gm_turn WHERE turn_id = ?")) {
            s.setObject(1, turnId);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(read(r)) : Optional.empty(); }
        } catch (SQLException e) { throw new GmTurnPersistenceException("could not load GM turn", e); }
    }

    @Override
    public Optional<GmTurn> findByTurnIdAndAdventureId(UUID turnId, UUID adventureId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT * FROM adventure_gm_turn WHERE turn_id = ? AND adventure_id = ?")) {
            s.setObject(1, turnId); s.setObject(2, adventureId);
            try (ResultSet r = s.executeQuery()) { return r.next() ? Optional.of(read(r)) : Optional.empty(); }
        } catch (SQLException e) { throw new GmTurnPersistenceException("could not load GM turn", e); }
    }

    @Override
    public void save(GmTurn turn, UUID adventureId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO adventure_gm_turn (turn_id, command_id, adventure_id, expected_session_version, input_type, input_json, fingerprint, status, failure, provider_metadata,
                    requested_endpoint_id, requested_provider, requested_model, requested_reasoning,
                    effective_endpoint_id, effective_endpoint_version, effective_provider, effective_model, effective_reasoning, attempt_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (command_id) DO UPDATE SET status=EXCLUDED.status, failure=EXCLUDED.failure, provider_metadata=EXCLUDED.provider_metadata,
                    requested_endpoint_id=EXCLUDED.requested_endpoint_id, requested_provider=EXCLUDED.requested_provider,
                    requested_model=EXCLUDED.requested_model, requested_reasoning=EXCLUDED.requested_reasoning,
                    effective_endpoint_id=EXCLUDED.effective_endpoint_id, effective_endpoint_version=EXCLUDED.effective_endpoint_version,
                    effective_provider=EXCLUDED.effective_provider, effective_model=EXCLUDED.effective_model,
                    effective_reasoning=EXCLUDED.effective_reasoning, attempt_count=EXCLUDED.attempt_count
                """)) {
            s.setObject(1, turn.turnId()); s.setObject(2, turn.commandId()); s.setObject(3, adventureId);
            s.setLong(4, turn.expectedSessionVersion()); s.setString(5, turn.input().type());
            s.setString(6, mapper.writeValueAsString(inputJson(turn.input()))); s.setString(7, turn.fingerprint());
            s.setString(8, turn.status().name()); s.setString(9, turn.failure()); s.setString(10, turn.providerMetadata());
            s.setObject(11, turn.requestedSelection().endpointId());
            s.setString(12, turn.requestedSelection().provider()); s.setString(13, turn.requestedSelection().model());
            s.setString(14, turn.requestedSelection().reasoning()); s.setObject(15, turn.effectiveSelection().endpointId());
            if (turn.effectiveSelection().endpointVersion() == null) s.setTimestamp(16, null);
            else s.setTimestamp(16, java.sql.Timestamp.from(turn.effectiveSelection().endpointVersion()));
            s.setString(17, turn.effectiveSelection().provider()); s.setString(18, turn.effectiveSelection().model());
            s.setString(19, turn.effectiveSelection().reasoning()); s.setInt(20, turn.attemptCount());
            s.executeUpdate();
        } catch (Exception e) { throw new GmTurnPersistenceException("could not save GM turn", e); }
    }

    private GmTurn read(ResultSet row) throws SQLException {
        try {
            JsonNode json = mapper.readTree(row.getString("input_json"));
            GmInput input = switch (row.getString("input_type")) {
                case "TEXT" -> new GmInput.TextInput(json.get("text").asText());
                case "MAP_ACTION" -> new GmInput.MapActionInput(UUID.fromString(json.get("mapId").asText()), json.get("mapVersion").asLong(), json.get("action").asText());
                case "META_QUESTION" -> new GmInput.MetaQuestionInput(json.get("question").asText());
                default -> throw new IllegalStateException("unsupported input type");
            };
            RequestedGmProviderSelection requested = row.getString("requested_provider") == null
                    ? RequestedGmProviderSelection.legacyUnknown()
                    : new RequestedGmProviderSelection((UUID) row.getObject("requested_endpoint_id"), row.getString("requested_provider"),
                    row.getString("requested_model"), row.getString("requested_reasoning"));
            EffectiveGmProviderSelection effective = row.getString("effective_provider") == null
                    ? EffectiveGmProviderSelection.legacyUnknown()
                    : new EffectiveGmProviderSelection((UUID) row.getObject("effective_endpoint_id"),
                    row.getTimestamp("effective_endpoint_version").toInstant(), row.getString("effective_provider"),
                    row.getString("effective_model"), row.getString("effective_reasoning"));
            int attempts = row.getInt("attempt_count");
            GmTurn turn = GmTurn.start((UUID) row.getObject("turn_id"), (UUID) row.getObject("command_id"), row.getLong("expected_session_version"), input, requested);
            return switch (row.getString("status")) {
                case "STARTED" -> turn;
                case "PROCESSING" -> turn.process();
                case "COMMITTED" -> turn.process().commit(row.getString("provider_metadata"), requested, effective, attempts < 1 ? 1 : attempts);
                case "FAILED" -> turn.process().fail(row.getString("failure"));
                case "FAILED_RETRYABLE" -> turn.process().failRetryable(row.getString("failure"));
                default -> throw new IllegalStateException("unsupported GM turn status");
            };
        } catch (Exception e) { throw new SQLException("could not read GM turn", e); }
    }

    private static Map<String, Object> inputJson(GmInput input) {
        if (input instanceof GmInput.TextInput text) return Map.of("text", text.text());
        if (input instanceof GmInput.MapActionInput map) return Map.of("mapId", map.mapId(), "mapVersion", map.mapVersion(), "action", map.action());
        return Map.of("question", ((GmInput.MetaQuestionInput) input).question());
    }
}
