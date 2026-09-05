package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.domain.combat.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PostgresCombatEncounterRepository implements CombatEncounterRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    public PostgresCombatEncounterRepository(javax.sql.DataSource dataSource) { this(dataSource, new ObjectMapper()); }
    public PostgresCombatEncounterRepository(javax.sql.DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource); this.objectMapper = objectMapper;
    }
    @Override public Optional<CombatEncounter> findActive(UUID adventureId) {
        return load("adventure_id = ? AND status IN ('PREPARING','ACTIVE','REACTION_PENDING')", adventureId);
    }

    @Override public Optional<CombatEncounter> findByEncounterId(UUID encounterId) {
        return load("encounter_id = ?", encounterId);
    }

    @Override public Optional<CombatEncounter> findLatestEndedByAdventure(UUID adventureId) {
        var ids = jdbc.query("SELECT encounter_id FROM combat_encounter WHERE adventure_id = ? AND status = 'ENDED' ORDER BY version DESC LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class), adventureId);
        return ids.isEmpty() ? Optional.empty() : load("encounter_id = ?", ids.getFirst());
    }

    private Optional<CombatEncounter> load(String predicate, UUID id) {
        var encounters = jdbc.query("SELECT encounter_id, adventure_id, status, round, current_participant_id, version, event_cursor FROM combat_encounter WHERE " + predicate, (rs, n) ->
                new EncounterRow(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                        CombatEncounter.Status.valueOf(rs.getString(3)), rs.getInt(4), UUID.fromString(rs.getString(5)),
                        rs.getLong(6), rs.getLong(7)), id);
        if (encounters.isEmpty()) return Optional.empty();
        var encounter = encounters.get(0);
        var participants = jdbc.query("SELECT participant_id, display_name, controller, initiative, public_condition, movement_remaining, action_available, bonus_action_available, reaction_available FROM combat_participant WHERE encounter_id = ? ORDER BY initiative DESC, participant_id", (rs, n) ->
                new CombatParticipant(UUID.fromString(rs.getString(1)), rs.getString(2), CombatParticipant.Controller.valueOf(rs.getString(3)), rs.getInt(4), rs.getString(5),
                        new TurnResources(rs.getInt(6), rs.getBoolean(7), rs.getBoolean(8), rs.getBoolean(9))), encounter.encounterId());
        var positions = jdbc.query("SELECT subject_id, target_id, range_band, cover FROM combat_narrative_position WHERE encounter_id = ? ORDER BY subject_id, target_id", (rs, n) ->
                new NarrativeCombatPosition(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)), encounter.encounterId());
        var pending = loadPendingReaction(encounter.encounterId());
        return Optional.of(new CombatEncounter(encounter.encounterId(), encounter.adventureId(), encounter.status(), encounter.round(),
                encounter.currentParticipantId(), participants, encounter.version(), encounter.eventCursor(), positions, pending));
    }
    @Override public CombatEncounter save(CombatEncounter encounter) {
        jdbc.update("INSERT INTO combat_encounter(encounter_id, adventure_id, status, round, current_participant_id, version, event_cursor, pending_reaction_id, pending_reaction_trigger, pending_reaction_actor_id, pending_reaction_operation_id, pending_reaction_resume_step, pending_reaction_options) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                encounter.encounterId(), encounter.adventureId(), encounter.status().name(), encounter.round(), encounter.currentParticipantId(), encounter.version(), encounter.eventCursor(),
                pendingId(encounter), pendingTrigger(encounter), pendingActor(encounter), pendingOperation(encounter), pendingResumeStep(encounter), pendingOptions(encounter));
        for (var participant : encounter.participants()) {
            jdbc.update("INSERT INTO combat_participant(encounter_id, participant_id, display_name, controller, initiative, public_condition, movement_remaining, action_available, bonus_action_available, reaction_available) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    encounter.encounterId(), participant.participantId(), participant.displayName(), participant.controller().name(), participant.initiative(), participant.publicCondition(),
                    participant.resources().movement(), participant.resources().actionAvailable(), participant.resources().bonusActionAvailable(), participant.resources().reactionAvailable());
        }
        saveNarrativePositions(encounter);
        return encounter;
    }

    @Override public CombatEncounter save(CombatEncounter encounter, long expectedVersion) {
        int updated = jdbc.update("UPDATE combat_encounter SET status = ?, round = ?, current_participant_id = ?, version = ?, event_cursor = ?, pending_reaction_id = ?, pending_reaction_trigger = ?, pending_reaction_actor_id = ?, pending_reaction_operation_id = ?, pending_reaction_resume_step = ?, pending_reaction_options = ?::jsonb WHERE encounter_id = ? AND version = ?",
                encounter.status().name(), encounter.round(), encounter.currentParticipantId(), encounter.version(), encounter.eventCursor(),
                pendingId(encounter), pendingTrigger(encounter), pendingActor(encounter), pendingOperation(encounter), pendingResumeStep(encounter), pendingOptions(encounter),
                encounter.encounterId(), expectedVersion);
        if (updated != 1) throw new IllegalStateException("COMBAT_VERSION_CONFLICT");
        for (var participant : encounter.participants()) {
            jdbc.update("UPDATE combat_participant SET movement_remaining = ?, action_available = ?, bonus_action_available = ?, reaction_available = ? WHERE encounter_id = ? AND participant_id = ?",
                    participant.resources().movement(), participant.resources().actionAvailable(), participant.resources().bonusActionAvailable(), participant.resources().reactionAvailable(),
                    encounter.encounterId(), participant.participantId());
        }
        jdbc.update("DELETE FROM combat_narrative_position WHERE encounter_id = ?", encounter.encounterId());
        saveNarrativePositions(encounter);
        return encounter;
    }

    private void saveNarrativePositions(CombatEncounter encounter) {
        for (var position : encounter.narrativePositions()) {
            jdbc.update("INSERT INTO combat_narrative_position(encounter_id, subject_id, target_id, range_band, cover) VALUES (?, ?, ?, ?, ?)",
                    encounter.encounterId(), position.subjectId(), position.targetId(), position.rangeBand(), position.cover());
        }
    }

    private ReactionInterrupt loadPendingReaction(UUID encounterId) {
        try {
            var rows = jdbc.query("SELECT pending_reaction_id, pending_reaction_trigger, pending_reaction_actor_id, pending_reaction_operation_id, pending_reaction_resume_step, pending_reaction_options::text FROM combat_encounter WHERE encounter_id = ?",
                    (rs, row) -> new PendingRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                            rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6)), encounterId);
            if (rows.isEmpty() || rows.get(0).reactionId() == null) return null;
            var row = rows.get(0);
            List<ReactionOption> options;
            try { options = objectMapper.readValue(row.options(), new TypeReference<>() {}); }
            catch (Exception exception) { throw new IllegalStateException("invalid persisted reaction options", exception); }
            return new ReactionInterrupt(row.reactionId(), row.trigger(), row.actorId(), row.operationId(), row.resumeStep(), options);
        } catch (org.springframework.dao.DataAccessException missingLegacyColumn) {
            return null;
        }
    }

    private String pendingOptions(CombatEncounter encounter) {
        if (encounter.pendingReaction() == null) return "[]";
        try { return objectMapper.writeValueAsString(encounter.pendingReaction().options()); }
        catch (Exception exception) { throw new IllegalStateException("could not persist reaction options", exception); }
    }
    private static UUID pendingId(CombatEncounter e) { return e.pendingReaction() == null ? null : e.pendingReaction().reactionId(); }
    private static String pendingTrigger(CombatEncounter e) { return e.pendingReaction() == null ? null : e.pendingReaction().trigger(); }
    private static UUID pendingActor(CombatEncounter e) { return e.pendingReaction() == null ? null : e.pendingReaction().eligibleActorId(); }
    private static UUID pendingOperation(CombatEncounter e) { return e.pendingReaction() == null ? null : e.pendingReaction().suspendedOperationId(); }
    private static String pendingResumeStep(CombatEncounter e) { return e.pendingReaction() == null ? null : e.pendingReaction().resumeStep(); }

    private record EncounterRow(UUID encounterId, UUID adventureId, CombatEncounter.Status status, int round,
                                UUID currentParticipantId, long version, long eventCursor) {}
    private record PendingRow(UUID reactionId, String trigger, UUID actorId, UUID operationId, String resumeStep, String options) {}
}
