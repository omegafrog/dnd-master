package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresCombatEncounterRepository implements CombatEncounterRepository {
    private final JdbcTemplate jdbc;
    public PostgresCombatEncounterRepository(javax.sql.DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }
    @Override public Optional<CombatEncounter> findActive(UUID adventureId) {
        var encounters = jdbc.query("SELECT encounter_id, adventure_id, status, round, current_participant_id, version, event_cursor FROM combat_encounter WHERE adventure_id = ? AND status IN ('PREPARING','ACTIVE')", (rs, n) ->
                new CombatEncounter(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                        CombatEncounter.Status.valueOf(rs.getString(3)), rs.getInt(4), UUID.fromString(rs.getString(5)),
                        List.of(), rs.getLong(6), rs.getLong(7)), adventureId);
        if (encounters.isEmpty()) return Optional.empty();
        var encounter = encounters.get(0);
        var participants = jdbc.query("SELECT participant_id, display_name, controller, initiative, public_condition FROM combat_participant WHERE encounter_id = ? ORDER BY initiative DESC, participant_id", (rs, n) ->
                new CombatParticipant(UUID.fromString(rs.getString(1)), rs.getString(2), CombatParticipant.Controller.valueOf(rs.getString(3)), rs.getInt(4), rs.getString(5)), encounter.encounterId());
        return Optional.of(new CombatEncounter(encounter.encounterId(), encounter.adventureId(), encounter.status(), encounter.round(),
                encounter.currentParticipantId(), participants, encounter.version(), encounter.eventCursor()));
    }
    @Override public CombatEncounter save(CombatEncounter encounter) {
        jdbc.update("INSERT INTO combat_encounter(encounter_id, adventure_id, status, round, current_participant_id, version, event_cursor) VALUES (?, ?, ?, ?, ?, ?, ?)",
                encounter.encounterId(), encounter.adventureId(), encounter.status().name(), encounter.round(), encounter.currentParticipantId(), encounter.version(), encounter.eventCursor());
        for (var participant : encounter.participants()) {
            jdbc.update("INSERT INTO combat_participant(encounter_id, participant_id, display_name, controller, initiative, public_condition) VALUES (?, ?, ?, ?, ?, ?)",
                    encounter.encounterId(), participant.participantId(), participant.displayName(), participant.controller().name(), participant.initiative(), participant.publicCondition());
        }
        return encounter;
    }
}
