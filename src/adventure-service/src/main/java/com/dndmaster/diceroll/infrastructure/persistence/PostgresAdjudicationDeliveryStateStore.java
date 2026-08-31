package com.dndmaster.diceroll.infrastructure.persistence;

import com.dndmaster.diceroll.domain.RollId;
import com.dndmaster.diceroll.infrastructure.http.*;
import java.sql.*;
import javax.sql.DataSource;

public final class PostgresAdjudicationDeliveryStateStore implements AdjudicationDeliveryStateStore {
    private final DataSource dataSource;
    public PostgresAdjudicationDeliveryStateStore(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }

    @Override
    public DeliveryAttempt begin(String key, RollId rollId, String hash) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO adjudication_delivery(delivery_key, roll_id, payload_hash, status, attempts, version) VALUES (?, ?, ?, 'PENDING', 1, 0) ON CONFLICT DO NOTHING")) {
                    insert.setString(1, key); insert.setObject(2, rollId.value()); insert.setString(3, hash); insert.executeUpdate();
                }
                DeliveryAttempt result;
                try (PreparedStatement select = connection.prepareStatement("SELECT roll_id, payload_hash, status, version FROM adjudication_delivery WHERE delivery_key=? FOR UPDATE")) {
                    select.setString(1, key);
                    try (ResultSet row = select.executeQuery()) {
                        if (!row.next()) throw new SQLException("delivery state disappeared");
                        if (!row.getObject("roll_id").equals(rollId.value()) || !row.getString("payload_hash").equals(hash)) {
                            throw new IdempotencyConflictException();
                        }
                        DeliveryStatus status = DeliveryStatus.valueOf(row.getString("status"));
                        long version = row.getLong("version");
                        if (status == DeliveryStatus.COMPLETED || status == DeliveryStatus.PENDING && version > 0) {
                            result = new DeliveryAttempt(status, version, false);
                        } else if (status == DeliveryStatus.FAILED) {
                            try (PreparedStatement retry = connection.prepareStatement("UPDATE adjudication_delivery SET status='PENDING', attempts=attempts+1, failure_reason=NULL, version=version+1 WHERE delivery_key=? AND version=?")) {
                                retry.setString(1, key); retry.setLong(2, version); retry.executeUpdate();
                            }
                            result = new DeliveryAttempt(DeliveryStatus.PENDING, version + 1, true);
                        } else result = new DeliveryAttempt(DeliveryStatus.PENDING, version, true);
                    }
                }
                connection.commit(); return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            }
        } catch (IdempotencyConflictException exception) { throw exception;
        } catch (SQLException exception) { throw new DiceRollPersistenceException("could not begin adjudication delivery", exception); }
    }

    @Override public void markFailed(String key, long version, String reason) { transition(key, version, "FAILED", reason); }
    @Override public void markCompleted(String key, long version) { transition(key, version, "COMPLETED", null); }

    private void transition(String key, long version, String status, String reason) {
        String sql = "UPDATE adjudication_delivery SET status=?, failure_reason=?, version=version+1 WHERE delivery_key=? AND status='PENDING' AND version=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status); statement.setString(2, reason); statement.setString(3, key); statement.setLong(4, version);
            if (statement.executeUpdate() != 1) throw new OptimisticDeliveryLockException();
        } catch (OptimisticDeliveryLockException exception) { throw exception;
        } catch (SQLException exception) { throw new DiceRollPersistenceException("could not transition delivery", exception); }
    }
}
