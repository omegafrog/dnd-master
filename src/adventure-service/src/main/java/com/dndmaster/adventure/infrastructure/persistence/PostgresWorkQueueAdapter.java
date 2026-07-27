package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.scenario.compilation.WorkEnvelope;
import com.dndmaster.adventure.application.scenario.compilation.WorkQueuePort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresWorkQueueAdapter implements WorkQueuePort {
    private final DataSource dataSource;

    public PostgresWorkQueueAdapter(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }

    @Override
    public void enqueue(WorkEnvelope work) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO adventure_work_job(work_id, work_type, aggregate_id, input_version, idempotency_key, attempt, status) VALUES (?, ?, ?, ?, ?, ?, 'PENDING') ON CONFLICT (idempotency_key) DO NOTHING")) {
            statement.setObject(1, work.workId()); statement.setString(2, work.workType()); statement.setObject(3, work.aggregateId());
            statement.setLong(4, work.inputVersion()); statement.setString(5, work.idempotencyKey()); statement.setInt(6, work.attempt());
            statement.executeUpdate();
        } catch (SQLException exception) { throw new ScenarioPackagePersistenceException("could not enqueue work", exception); }
    }

    @Override
    public Optional<Delivery> claim(String workerId, Duration lease) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT work_id, work_type, aggregate_id, input_version, idempotency_key, attempt FROM adventure_work_job WHERE status = 'PENDING' OR (status = 'CLAIMED' AND lease_until < CURRENT_TIMESTAMP) ORDER BY work_id FOR UPDATE SKIP LOCKED LIMIT 1")) {
                try (ResultSet row = select.executeQuery()) {
                    if (!row.next()) { connection.rollback(); return Optional.empty(); }
                    UUID deliveryToken = UUID.randomUUID();
                    WorkEnvelope work = new WorkEnvelope(row.getObject("work_id", UUID.class), row.getString("work_type"),
                            row.getObject("aggregate_id", UUID.class), row.getLong("input_version"), row.getString("idempotency_key"), row.getInt("attempt"));
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE adventure_work_job SET status = 'CLAIMED', delivery_token = ?, worker_id = ?, lease_until = ? WHERE work_id = ?")) {
                        update.setObject(1, deliveryToken); update.setString(2, workerId);
                        update.setObject(3, OffsetDateTime.now(ZoneOffset.UTC).plus(lease)); update.setObject(4, work.workId()); update.executeUpdate();
                    }
                    connection.commit(); return Optional.of(new Delivery(work, deliveryToken, workerId));
                }
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new ScenarioPackagePersistenceException("could not claim work", exception); }
    }

    @Override public void acknowledge(Delivery delivery) { update(delivery, "ACKED", null); }

    @Override public void retry(Delivery delivery, String reason) { update(delivery, "PENDING", reason); }

    private void update(Delivery delivery, String status, String reason) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE adventure_work_job SET status = ?, attempt = attempt + CASE WHEN ? = 'PENDING' THEN 1 ELSE 0 END, failure_reason = ?, delivery_token = NULL, lease_until = NULL WHERE work_id = ? AND delivery_token = ?")) {
            statement.setString(1, status); statement.setString(2, status); statement.setString(3, reason);
            statement.setObject(4, delivery.work().workId()); statement.setObject(5, delivery.deliveryToken()); statement.executeUpdate();
        } catch (SQLException exception) { throw new ScenarioPackagePersistenceException("could not update work", exception); }
    }
}
