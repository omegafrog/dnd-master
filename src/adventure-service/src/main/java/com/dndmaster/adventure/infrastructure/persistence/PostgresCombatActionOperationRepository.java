package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionOperationRepository;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatActionStep;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresCombatActionOperationRepository implements CombatActionOperationRepository {
    private final JdbcTemplate jdbc;

    public PostgresCombatActionOperationRepository(javax.sql.DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<CombatActionOperation> findByCommandId(UUID commandId) {
        var operations = jdbc.query("SELECT command_id, fingerprint, encounter_id, actor_id, status, reserved_movement, reserved_action, reserved_bonus_action, reserved_reaction, response_version, response_status, response_dice_total, response_judgment, response_violations, failure, dice_total FROM combat_action_operation WHERE command_id = ?",
                (rs, row) -> {
                    UUID id = rs.getObject(1, UUID.class);
                    String responseStatus = rs.getString(11);
                    CombatActionResponse response = responseStatus == null ? null : new CombatActionResponse(
                            rs.getObject(3, UUID.class), id, rs.getLong(10), responseStatus,
                            (Integer) rs.getObject(12), rs.getString(13), split(rs.getString(14)));
                    List<CombatActionStep> steps = jdbc.query("SELECT step_name, idempotency_key, status FROM combat_action_step WHERE command_id = ? ORDER BY step_name",
                            (stepRs, stepRow) -> new CombatActionStep(stepRs.getString(1), stepRs.getString(2), CombatActionStep.Status.valueOf(stepRs.getString(3))), id);
                    CombatActionOperation operation = CombatActionOperation.restore(id, rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                            new TurnResourceCost(rs.getInt(6), rs.getBoolean(7), rs.getBoolean(8), rs.getBoolean(9)), steps,
                            CombatActionOperation.Status.valueOf(rs.getString(5)), response, rs.getString(15));
                    Integer diceTotal = (Integer) rs.getObject(16);
                    if (diceTotal != null) operation.recordDiceTotal(diceTotal);
                    return operation;
                }, commandId);
        return operations.stream().findFirst();
    }

    @Override
    public void save(CombatActionOperation operation) {
        CombatActionResponse response = operation.response();
        jdbc.update("INSERT INTO combat_action_operation(command_id, encounter_id, actor_id, fingerprint, status, reserved_movement, reserved_action, reserved_bonus_action, reserved_reaction, response_version, response_status, response_dice_total, response_judgment, response_violations, failure, dice_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(command_id) DO UPDATE SET status = EXCLUDED.status, response_version = EXCLUDED.response_version, response_status = EXCLUDED.response_status, response_dice_total = EXCLUDED.response_dice_total, response_judgment = EXCLUDED.response_judgment, response_violations = EXCLUDED.response_violations, failure = EXCLUDED.failure, dice_total = EXCLUDED.dice_total",
                operation.commandId(), operation.encounterId(), operation.actorId(), operation.fingerprint(), operation.status().name(),
                operation.reservedCost().movement(), operation.reservedCost().action(), operation.reservedCost().bonusAction(), operation.reservedCost().reaction(),
                response == null ? null : response.encounterVersion(), response == null ? null : response.status(),
                response == null ? null : response.diceTotal(), response == null ? null : response.judgment(),
                response == null ? null : String.join("\u001f", response.violations()), operation.failure(), operation.diceTotal());
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

    private static List<String> split(String value) {
        return value == null || value.isEmpty() ? List.of() : List.of(value.split("\\u001f", -1));
    }
}
