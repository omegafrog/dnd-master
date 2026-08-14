package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;

public final class PostgresAdventureSessionRepository implements AdventureSessionRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public PostgresAdventureSessionRepository(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }
    @Override public Optional<AdventureSession> findById(SessionId id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT * FROM adventure_session WHERE session_id=?")) {
            s.setObject(1, id.value()); try (ResultSet row = s.executeQuery()) { return row.next() ? Optional.of(map(c, row)) : Optional.empty(); }
        } catch (SQLException e) { throw new AdventurePersistenceException("could not load adventure session", e); }
    }
    @Override public List<AdventureSession> findByScenarioPackageId(UUID scenarioPackageId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT session_id FROM adventure_session WHERE scenario_package_id=? ORDER BY session_id")) {
            s.setObject(1, scenarioPackageId); List<AdventureSession> sessions = new ArrayList<>();
            try (ResultSet rows = s.executeQuery()) { while (rows.next()) findById(new SessionId(rows.getObject("session_id", UUID.class))).ifPresent(sessions::add); }
            return sessions;
        } catch (SQLException e) { throw new AdventurePersistenceException("could not list adventure sessions", e); }
    }
    @Override public void save(AdventureSession session, long expectedVersion) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit(); c.setAutoCommit(false);
            try {
                String sql = expectedVersion == 0 && session.version() == 0
                        ? "INSERT INTO adventure_session(session_id, owner_player_id, scenario_package_id, scenario_package_revision, blueprint_id, blueprint_revision, character_edition, character_limit, runtime_scenario_id, runtime_rule_set_id, runtime_rulebook_ids_json, runtime_engine_id, runtime_tool_ids_json, runtime_initial_scene, status, started_adventure_id, start_request_id, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        : "UPDATE adventure_session SET blueprint_id=?, blueprint_revision=?, character_edition=?, character_limit=?, runtime_scenario_id=?, runtime_rule_set_id=?, runtime_rulebook_ids_json=?, runtime_engine_id=?, runtime_tool_ids_json=?, runtime_initial_scene=?, status=?, started_adventure_id=?, start_request_id=?, version=? WHERE session_id=? AND version=?";
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    if (expectedVersion == 0 && session.version() == 0) { s.setObject(1, session.id().value()); s.setObject(2, session.ownerPlayerId().value()); s.setObject(3, session.scenarioPackageId()); s.setLong(4, session.scenarioPackageRevision()); s.setObject(5, session.blueprintId()); s.setLong(6, session.blueprintRevision()); s.setString(7, session.characterEdition()); s.setInt(8, session.characterLimit()); bindConfiguration(s, 9, session.runtimeConfiguration()); bindStart(s, 15, session); s.setLong(18, session.version()); }
                    else { s.setObject(1, session.blueprintId()); s.setLong(2, session.blueprintRevision()); s.setString(3, session.characterEdition()); s.setInt(4, session.characterLimit()); bindConfiguration(s, 5, session.runtimeConfiguration()); bindStart(s, 11, session); s.setLong(14, session.version()); s.setObject(15, session.id().value()); s.setLong(16, expectedVersion); }
                    if (s.executeUpdate() != 1) throw new OptimisticAdventureLockException();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM adventure_session_party_member WHERE session_id=?")) { s.setObject(1, session.id().value()); s.executeUpdate(); }
                try (PreparedStatement s = c.prepareStatement("INSERT INTO adventure_session_party_member VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)") ) {
                    for (AdventurePartyMember m : session.party()) { s.setObject(1, session.id().value()); s.setObject(2, m.characterSheetId().value()); s.setString(3, m.controlMode().name()); s.setBoolean(4, m.nameMutableAfterStart()); s.setBoolean(5, m.raceMutableAfterStart()); s.setBoolean(6, m.characterClassMutableAfterStart()); s.setBoolean(7, m.backgroundMutableAfterStart()); s.setBoolean(8, m.startingAbilitiesMutableAfterStart()); s.setBoolean(9, m.levelMutableAfterStart()); s.addBatch(); }
                    s.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; } finally { c.setAutoCommit(autoCommit); }
        } catch (SQLException e) { throw new AdventurePersistenceException("could not save adventure session", e); }
    }
    private AdventureSession map(Connection c, ResultSet row) throws SQLException {
        SessionId id = new SessionId(row.getObject("session_id", UUID.class)); List<AdventurePartyMember> party = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM adventure_session_party_member WHERE session_id=? ORDER BY character_sheet_id")) {
            s.setObject(1, id.value()); try (ResultSet members = s.executeQuery()) { while (members.next()) party.add(new AdventurePartyMember(new CharacterSheetId(members.getObject("character_sheet_id", UUID.class)), ControlMode.valueOf(members.getString("control_mode")), members.getBoolean("name_mutable_after_start"), members.getBoolean("race_mutable_after_start"), members.getBoolean("class_mutable_after_start"), members.getBoolean("background_mutable_after_start"), members.getBoolean("abilities_mutable_after_start"), members.getBoolean("level_mutable_after_start"))); }
        }
        UUID adventureId = row.getObject("started_adventure_id", UUID.class);
        return AdventureSession.rehydrate(id, new OwnerPlayerId(row.getObject("owner_player_id", UUID.class)), row.getObject("scenario_package_id", UUID.class), row.getLong("scenario_package_revision"), row.getObject("blueprint_id", UUID.class), row.getLong("blueprint_revision"), row.getString("character_edition"), row.getInt("character_limit"), party, configuration(row), AdventureSession.Status.valueOf(row.getString("status")), adventureId == null ? null : new AdventureId(adventureId), row.getObject("start_request_id", UUID.class), row.getLong("version"));
    }
    private void bindConfiguration(PreparedStatement s, int offset, AdventureSessionRuntimeConfiguration c) throws SQLException {
        if (c == null) { for (int i = 0; i < 6; i++) s.setObject(offset + i, null); return; }
        s.setObject(offset, c.scenarioId().value()); s.setObject(offset + 1, c.ruleSetId().value());
        try { s.setString(offset + 2, objectMapper.writeValueAsString(c.rulebookIds())); s.setString(offset + 4, objectMapper.writeValueAsString(c.toolIds())); } catch (Exception e) { throw new SQLException("could not write runtime configuration", e); }
        s.setString(offset + 3, c.engineId()); s.setString(offset + 5, c.initialScene());
    }
    private AdventureSessionRuntimeConfiguration configuration(ResultSet row) throws SQLException {
        UUID scenarioId = row.getObject("runtime_scenario_id", UUID.class); if (scenarioId == null) return null;
        try { return new AdventureSessionRuntimeConfiguration(new ScenarioId(scenarioId), new RuleSetId(row.getObject("runtime_rule_set_id", UUID.class)), objectMapper.readValue(row.getString("runtime_rulebook_ids_json"), new TypeReference<List<UUID>>() {}), row.getString("runtime_engine_id"), objectMapper.readValue(row.getString("runtime_tool_ids_json"), new TypeReference<List<String>>() {}), row.getString("runtime_initial_scene")); } catch (Exception e) { throw new SQLException("could not read runtime configuration", e); }
    }
    private static void bindStart(PreparedStatement s, int offset, AdventureSession session) throws SQLException { s.setString(offset, session.status().name()); s.setObject(offset + 1, session.startedAdventureId() == null ? null : session.startedAdventureId().value()); s.setObject(offset + 2, session.startRequestId()); }
}
