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
                new EncounterRow(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                        CombatEncounter.Status.valueOf(rs.getString(3)), rs.getInt(4), UUID.fromString(rs.getString(5)),
                        rs.getLong(6), rs.getLong(7)), adventureId);
        if (encounters.isEmpty()) return Optional.empty();
        var encounter = encounters.get(0);
        var participants = jdbc.query("SELECT participant_id, display_name, controller, initiative, public_condition, movement_remaining, action_available, bonus_action_available, reaction_available FROM combat_participant WHERE encounter_id = ? ORDER BY initiative DESC, participant_id", (rs, n) ->
                new CombatParticipant(UUID.fromString(rs.getString(1)), rs.getString(2), CombatParticipant.Controller.valueOf(rs.getString(3)), rs.getInt(4), rs.getString(5),
                        new TurnResources(rs.getInt(6), rs.getBoolean(7), rs.getBoolean(8), rs.getBoolean(9))), encounter.encounterId());
        return Optional.of(new CombatEncounter(encounter.encounterId(), encounter.adventureId(), encounter.status(), encounter.round(),
                encounter.currentParticipantId(), participants, encounter.version(), encounter.eventCursor()));
    }
    @Override public CombatEncounter save(CombatEncounter encounter) {
        jdbc.update("INSERT INTO combat_encounter(encounter_id, adventure_id, status, round, current_participant_id, version, event_cursor) VALUES (?, ?, ?, ?, ?, ?, ?)",
                encounter.encounterId(), encounter.adventureId(), encounter.status().name(), encounter.round(), encounter.currentParticipantId(), encounter.version(), encounter.eventCursor());
        for (var participant : encounter.participants()) {
            jdbc.update("INSERT INTO combat_participant(encounter_id, participant_id, display_name, controller, initiative, public_condition, movement_remaining, action_available, bonus_action_available, reaction_available) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    encounter.encounterId(), participant.participantId(), participant.displayName(), participant.controller().name(), participant.initiative(), participant.publicCondition(),
                    participant.resources().movement(), participant.resources().actionAvailable(), participant.resources().bonusActionAvailable(), participant.resources().reactionAvailable());
        }
        return encounter;
    }

    @Override public CombatEncounter save(CombatEncounter encounter, long expectedVersion) {
        int updated = jdbc.update("UPDATE combat_encounter SET status = ?, round = ?, current_participant_id = ?, version = ?, event_cursor = ? WHERE encounter_id = ? AND version = ?",
                encounter.status().name(), encounter.round(), encounter.currentParticipantId(), encounter.version(), encounter.eventCursor(), encounter.encounterId(), expectedVersion);
        if (updated != 1) throw new IllegalStateException("COMBAT_VERSION_CONFLICT");
        for (var participant : encounter.participants()) {
            jdbc.update("UPDATE combat_participant SET movement_remaining = ?, action_available = ?, bonus_action_available = ?, reaction_available = ? WHERE encounter_id = ? AND participant_id = ?",
                    participant.resources().movement(), participant.resources().actionAvailable(), participant.resources().bonusActionAvailable(), participant.resources().reactionAvailable(),
                    encounter.encounterId(), participant.participantId());
        }
        return encounter;
    }

    private record EncounterRow(UUID encounterId, UUID adventureId, CombatEncounter.Status status, int round,
                                UUID currentParticipantId, long version, long eventCursor) {}
}
