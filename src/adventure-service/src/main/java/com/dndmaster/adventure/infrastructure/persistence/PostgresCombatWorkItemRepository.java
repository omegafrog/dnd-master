package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatWorkItem;
import com.dndmaster.adventure.application.combat.CombatWorkItemRepository;
import com.dndmaster.adventure.application.combat.AiTacticalInstructionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresCombatWorkItemRepository implements CombatWorkItemRepository {
    private static final String COLUMNS = "work_item_id, encounter_id, operation_id, expected_encounter_version, work_type, due_at, attempt_count, status, lease_token, lease_until, failure, tactical_instruction, tactical_constraints::text, command_json::text, completed_steps";
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgresCombatWorkItemRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    @Override public void enqueue(CombatWorkItem item) { save(item); }

    @Override public Optional<CombatWorkItem> claim(String workerId, Duration lease, Instant now) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var select = connection.prepareStatement("SELECT " + COLUMNS
                    + " FROM combat_work_item WHERE (status = 'PENDING' AND due_at <= ?)"
                    + " OR (status = 'CLAIMED' AND lease_until <= ?) ORDER BY due_at, work_item_id FOR UPDATE SKIP LOCKED LIMIT 1")) {
                select.setObject(1, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
                select.setObject(2, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) { connection.rollback(); return Optional.empty(); }
                    CombatWorkItem current = read(result);
                    UUID token = UUID.randomUUID();
                    try (var update = connection.prepareStatement("UPDATE combat_work_item SET status = 'CLAIMED', lease_token = ?, worker_id = ?, lease_until = ? WHERE work_item_id = ?")) {
                        update.setObject(1, token);
                        update.setString(2, workerId);
                        update.setObject(3, OffsetDateTime.ofInstant(now.plus(lease), ZoneOffset.UTC));
                        update.setObject(4, current.workItemId());
                        update.executeUpdate();
                    }
                    connection.commit();
                    return Optional.of(current.claimed(token, now.plus(lease)));
                }
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new CombatWorkItemPersistenceException("could not claim combat work item", exception);
        }
    }

    @Override public void save(CombatWorkItem item) {
        try {
            jdbc.update("INSERT INTO combat_work_item(" + COLUMNS.replace("::text", "")
                    + ", worker_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, NULL)"
                    + " ON CONFLICT(work_item_id) DO UPDATE SET operation_id = EXCLUDED.operation_id, expected_encounter_version = EXCLUDED.expected_encounter_version, work_type = EXCLUDED.work_type, due_at = EXCLUDED.due_at, attempt_count = EXCLUDED.attempt_count, status = EXCLUDED.status, lease_token = EXCLUDED.lease_token, lease_until = EXCLUDED.lease_until, failure = EXCLUDED.failure, tactical_instruction = EXCLUDED.tactical_instruction, tactical_constraints = EXCLUDED.tactical_constraints, command_json = EXCLUDED.command_json, completed_steps = EXCLUDED.completed_steps",
                    item.workItemId(), item.encounterId(), item.operationId(), item.expectedEncounterVersion(), item.workType().name(),
                    OffsetDateTime.ofInstant(item.dueAt(), ZoneOffset.UTC), item.attemptCount(), item.status().name(), item.leaseToken(),
                    item.leaseUntil() == null ? null : OffsetDateTime.ofInstant(item.leaseUntil(), ZoneOffset.UTC), item.failure(),
                    item.tacticalInstruction().instruction(), objectMapper.writeValueAsString(item.tacticalInstruction().constraints()),
                    item.command() == null ? null : objectMapper.writeValueAsString(item.command()), item.completedSteps());
        } catch (Exception exception) {
            throw new CombatWorkItemPersistenceException("could not save combat work item", exception);
        }
    }

    @Override public Optional<CombatWorkItem> findByOperationId(UUID operationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM combat_work_item WHERE operation_id = ?", (rs, row) -> read(rs), operationId)
                .stream().findFirst();
    }

    @Override public Optional<CombatWorkItem> findFailedByEncounterId(UUID encounterId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM combat_work_item WHERE encounter_id = ? AND status = 'FAILED' ORDER BY due_at LIMIT 1",
                (rs, row) -> read(rs), encounterId).stream().findFirst();
    }

    @Override public boolean hasPendingForEncounter(UUID encounterId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM combat_work_item WHERE encounter_id = ? AND status <> 'COMPLETED'",
                Integer.class, encounterId);
        return count != null && count > 0;
    }

    private CombatWorkItem read(ResultSet rs) throws SQLException {
        try {
            List<String> constraints = objectMapper.readValue(rs.getString("tactical_constraints"), new TypeReference<>() {});
            String commandJson = rs.getString("command_json");
            CombatActionCommand command = commandJson == null ? null : objectMapper.readValue(commandJson, CombatActionCommand.class);
            return restore(rs, constraints, command);
        } catch (Exception exception) {
            throw new SQLException("invalid persisted combat work item", exception);
        }
    }

    private static CombatWorkItem restore(ResultSet rs, List<String> constraints, CombatActionCommand command) throws SQLException {
        return new RestoredWorkItem(rs, constraints, command).value();
    }

    private record RestoredWorkItem(ResultSet rs, List<String> constraints, CombatActionCommand command) {
        CombatWorkItem value() {
            try {
                Object leaseUntil = rs.getObject("lease_until");
                return CombatWorkItem.restore(rs.getObject("work_item_id", UUID.class), rs.getObject("encounter_id", UUID.class),
                        rs.getObject("operation_id", UUID.class), rs.getLong("expected_encounter_version"),
                        CombatWorkItem.WorkType.valueOf(rs.getString("work_type")),
                        rs.getObject("due_at", OffsetDateTime.class).toInstant(), rs.getInt("attempt_count"),
                        CombatWorkItem.Status.valueOf(rs.getString("status")), rs.getObject("lease_token", UUID.class),
                        leaseUntil == null ? null : ((OffsetDateTime) leaseUntil).toInstant(), rs.getString("failure"),
                        new AiTacticalInstructionContext(rs.getString("tactical_instruction"), constraints), command,
                        rs.getInt("completed_steps"));
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        }
    }
}
