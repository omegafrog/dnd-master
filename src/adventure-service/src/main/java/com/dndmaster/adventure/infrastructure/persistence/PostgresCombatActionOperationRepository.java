package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionOperationRepository;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatActionStep;
import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresCombatActionOperationRepository implements CombatActionOperationRepository {
    private final JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public PostgresCombatActionOperationRepository(javax.sql.DataSource dataSource) {
        this(dataSource, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    public PostgresCombatActionOperationRepository(javax.sql.DataSource dataSource,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    @Override
    public Optional<CombatActionOperation> findByCommandId(UUID commandId) {
        var operations = jdbc.query("SELECT command_id, fingerprint, encounter_id, actor_id, status, reserved_movement, reserved_action, reserved_bonus_action, reserved_reaction, response_version, response_status, response_dice_total, response_judgment, response_violations, failure, dice_total, response_movement_adjudication, movement_follow_up, response_narration FROM combat_action_operation WHERE command_id = ?",
                (rs, row) -> {
                    UUID id = rs.getObject(1, UUID.class);
                    String responseStatus = rs.getString(11);
                    com.dndmaster.adventure.application.runtime.CombatContinuationCommand adjudication = null;
                    String adjudicationJson = rs.getString(17);
                    if (adjudicationJson != null) {
                        try { adjudication = objectMapper.readValue(adjudicationJson,
                                com.dndmaster.adventure.application.runtime.CombatContinuationCommand.class); }
                        catch (java.io.IOException malformed) { throw new IllegalStateException("stored movement adjudication response is corrupt", malformed); }
                    }
                    CombatActionResponse response = responseStatus == null ? null : new CombatActionResponse(
                            rs.getObject(3, UUID.class), id, rs.getLong(10), responseStatus,
                            (Integer) rs.getObject(12), rs.getString(13), split(rs.getString(14)), rs.getString(19), adjudication);
                    List<CombatActionStep> steps = jdbc.query("SELECT step_name, idempotency_key, status FROM combat_action_step WHERE command_id = ? ORDER BY step_name",
                            (stepRs, stepRow) -> new CombatActionStep(stepRs.getString(1), stepRs.getString(2), CombatActionStep.Status.valueOf(stepRs.getString(3))), id);
                    MovementFollowUpCommand movementFollowUp = null;
                    String followUpJson = rs.getString(18);
                    if (followUpJson != null) {
                        try { movementFollowUp = objectMapper.readValue(followUpJson, MovementFollowUpCommand.class); }
                        catch (java.io.IOException malformed) { throw new IllegalStateException("stored movement follow-up is corrupt", malformed); }
                    }
                    CombatActionOperation operation = CombatActionOperation.restore(id, rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                            new TurnResourceCost(rs.getInt(6), rs.getBoolean(7), rs.getBoolean(8), rs.getBoolean(9)), steps,
                            CombatActionOperation.Status.valueOf(rs.getString(5)), response, rs.getString(15), movementFollowUp);
                    Integer diceTotal = (Integer) rs.getObject(16);
                    if (diceTotal != null) operation.recordDiceTotal(diceTotal);
                    return operation;
                }, commandId);
        return operations.stream().findFirst();
    }

    @Override
    public void save(CombatActionOperation operation) {
        CombatActionResponse response = operation.response();
        String adjudicationJson = null;
        String followUpJson = null;
        if (response != null && response.movementAdjudication() != null) {
            try { adjudicationJson = objectMapper.writeValueAsString(response.movementAdjudication()); }
            catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("movement adjudication serialization failed", failure); }
        }
        if (operation.movementFollowUp() != null) {
            try { followUpJson = objectMapper.writeValueAsString(operation.movementFollowUp()); }
            catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("movement follow-up serialization failed", failure); }
        }
        jdbc.update("INSERT INTO combat_action_operation(command_id, encounter_id, actor_id, fingerprint, status, reserved_movement, reserved_action, reserved_bonus_action, reserved_reaction, response_version, response_status, response_dice_total, response_judgment, response_violations, failure, dice_total, response_movement_adjudication, movement_follow_up, response_narration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(command_id) DO UPDATE SET status = EXCLUDED.status, response_version = EXCLUDED.response_version, response_status = EXCLUDED.response_status, response_dice_total = EXCLUDED.response_dice_total, response_judgment = EXCLUDED.response_judgment, response_violations = EXCLUDED.response_violations, failure = EXCLUDED.failure, dice_total = EXCLUDED.dice_total, response_movement_adjudication = COALESCE(EXCLUDED.response_movement_adjudication, combat_action_operation.response_movement_adjudication), movement_follow_up = COALESCE(EXCLUDED.movement_follow_up, combat_action_operation.movement_follow_up), response_narration = COALESCE(EXCLUDED.response_narration, combat_action_operation.response_narration)",
                operation.commandId(), operation.encounterId(), operation.actorId(), operation.fingerprint(), operation.status().name(),
                operation.reservedCost().movement(), operation.reservedCost().action(), operation.reservedCost().bonusAction(), operation.reservedCost().reaction(),
                response == null ? null : response.encounterVersion(), response == null ? null : response.status(),
                response == null ? null : response.diceTotal(), response == null ? null : response.judgment(),
                response == null ? null : String.join("\u001f", response.violations()), operation.failure(), operation.diceTotal(), adjudicationJson, followUpJson,
                response == null ? null : response.narration());
        for (CombatActionStep step : operation.steps()) {
            jdbc.update("INSERT INTO combat_action_step(command_id, step_name, idempotency_key, status) VALUES (?, ?, ?, ?) ON CONFLICT(command_id, step_name) DO UPDATE SET status = EXCLUDED.status",
                    operation.commandId(), step.name(), step.idempotencyKey(), step.status().name());
        }
    }

    @Override
    public boolean hasPendingForEncounter(UUID encounterId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM combat_action_operation WHERE encounter_id = ? AND status <> 'COMMITTED'",
                Integer.class, encounterId);
        return count != null && count > 0;
    }

    @Override
    public Optional<CombatActionOperation> findPendingMovementForEncounter(UUID encounterId) {
        UUID commandId = jdbc.query("SELECT command_id FROM combat_action_operation WHERE encounter_id = ? AND status = 'GM_INTERVENTION_PENDING'",
                (rs, row) -> rs.getObject(1, UUID.class), encounterId).stream().findFirst().orElse(null);
        return commandId == null ? Optional.empty() : findByCommandId(commandId);
    }

    private static List<String> split(String value) {
        return value == null || value.isEmpty() ? List.of() : List.of(value.split("\\u001f", -1));
    }
}
