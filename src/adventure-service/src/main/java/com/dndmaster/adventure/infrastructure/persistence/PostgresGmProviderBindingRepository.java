package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.runtime.ProviderBinding;
import com.dndmaster.adventure.application.runtime.GmProviderSelection;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresGmProviderBindingRepository implements GmProviderBindingRepository {
    private final DataSource dataSource;
    public PostgresGmProviderBindingRepository(DataSource dataSource) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource);
    }

    @Override public Optional<ProviderBinding> current(UUID sessionId) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "SELECT requested_endpoint_id, provider, model, reasoning, state_version, turn_in_progress FROM gm_provider_binding WHERE session_id=?")) {
            statement.setObject(1, sessionId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new ProviderBinding(sessionId,
                        new GmProviderSelection(rows.getObject(1, UUID.class), rows.getString(2), rows.getString(3), rows.getString(4)),
                        rows.getLong(5), rows.getBoolean(6))) : Optional.empty();
            }
        } catch (Exception exception) { throw new AdventurePersistenceException("could not load GM provider binding", exception); }
    }

    @Override public void save(ProviderBinding binding) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO gm_provider_binding(session_id,requested_endpoint_id,provider,model,reasoning,state_version,turn_in_progress) VALUES (?,?,?,?,?,?,?) ON CONFLICT(session_id) DO UPDATE SET requested_endpoint_id=EXCLUDED.requested_endpoint_id,provider=EXCLUDED.provider,model=EXCLUDED.model,reasoning=EXCLUDED.reasoning,state_version=EXCLUDED.state_version,turn_in_progress=EXCLUDED.turn_in_progress")) {
            bind(statement, binding); statement.executeUpdate();
        } catch (Exception exception) { throw new AdventurePersistenceException("could not save GM provider binding", exception); }
    }

    @Override public boolean compareAndSet(UUID sessionId, long expectedVersion, ProviderBinding updated) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "UPDATE gm_provider_binding SET requested_endpoint_id=?,provider=?,model=?,reasoning=?,state_version=?,turn_in_progress=? WHERE session_id=? AND state_version=?")) {
            statement.setObject(1, updated.selection().endpointId()); statement.setString(2, updated.selection().provider()); statement.setString(3, updated.selection().model());
            statement.setString(4, updated.selection().reasoning()); statement.setLong(5, updated.stateVersion());
            statement.setBoolean(6, updated.turnInProgress()); statement.setObject(7, sessionId); statement.setLong(8, expectedVersion);
            return statement.executeUpdate() == 1;
        } catch (Exception exception) { throw new AdventurePersistenceException("could not update GM provider binding", exception); }
    }

    private static void bind(java.sql.PreparedStatement statement, ProviderBinding binding) throws java.sql.SQLException {
        statement.setObject(1, binding.sessionId()); statement.setObject(2, binding.selection().endpointId()); statement.setString(3, binding.selection().provider());
        statement.setString(4, binding.selection().model()); statement.setString(5, binding.selection().reasoning());
        statement.setLong(6, binding.stateVersion()); statement.setBoolean(7, binding.turnInProgress());
    }
}
