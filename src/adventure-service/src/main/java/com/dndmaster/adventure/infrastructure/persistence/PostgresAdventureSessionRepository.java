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
import javax.sql.DataSource;

public final class PostgresAdventureSessionRepository implements AdventureSessionRepository {
    private final DataSource dataSource;
    public PostgresAdventureSessionRepository(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }
    @Override public Optional<AdventureSession> findById(SessionId id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT * FROM adventure_session WHERE session_id=?")) {
            s.setObject(1, id.value()); try (ResultSet row = s.executeQuery()) { return row.next() ? Optional.of(map(c, row)) : Optional.empty(); }
        } catch (SQLException e) { throw new AdventurePersistenceException("could not load adventure session", e); }
    }
    @Override public void save(AdventureSession session, long expectedVersion) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit(); c.setAutoCommit(false);
            try {
                String sql = expectedVersion == 0 && session.version() == 0
                        ? "INSERT INTO adventure_session(session_id, owner_player_id, scenario_package_id, scenario_package_revision, character_limit, version) VALUES (?, ?, ?, ?, ?, ?)"
                        : "UPDATE adventure_session SET character_limit=?, version=? WHERE session_id=? AND version=?";
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    if (expectedVersion == 0 && session.version() == 0) { s.setObject(1, session.id().value()); s.setObject(2, session.ownerPlayerId().value()); s.setObject(3, session.scenarioPackageId()); s.setLong(4, session.scenarioPackageRevision()); s.setInt(5, session.characterLimit()); s.setLong(6, session.version()); }
                    else { s.setInt(1, session.characterLimit()); s.setLong(2, session.version()); s.setObject(3, session.id().value()); s.setLong(4, expectedVersion); }
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
    private static AdventureSession map(Connection c, ResultSet row) throws SQLException {
        SessionId id = new SessionId(row.getObject("session_id", UUID.class)); List<AdventurePartyMember> party = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM adventure_session_party_member WHERE session_id=? ORDER BY character_sheet_id")) {
            s.setObject(1, id.value()); try (ResultSet members = s.executeQuery()) { while (members.next()) party.add(new AdventurePartyMember(new CharacterSheetId(members.getObject("character_sheet_id", UUID.class)), ControlMode.valueOf(members.getString("control_mode")), members.getBoolean("name_mutable_after_start"), members.getBoolean("race_mutable_after_start"), members.getBoolean("class_mutable_after_start"), members.getBoolean("background_mutable_after_start"), members.getBoolean("abilities_mutable_after_start"), members.getBoolean("level_mutable_after_start"))); }
        }
        return AdventureSession.rehydrate(id, new OwnerPlayerId(row.getObject("owner_player_id", UUID.class)), row.getObject("scenario_package_id", UUID.class), row.getLong("scenario_package_revision"), row.getInt("character_limit"), party, row.getLong("version"));
    }
}
