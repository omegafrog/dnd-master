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
                "SELECT provider, model, reasoning, state_version, turn_in_progress FROM gm_provider_binding WHERE session_id=?")) {
            statement.setObject(1, sessionId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new ProviderBinding(sessionId,
                        new GmProviderSelection(rows.getString(1), rows.getString(2), rows.getString(3)),
                        rows.getLong(4), rows.getBoolean(5))) : Optional.empty();
            }
        } catch (Exception exception) { throw new AdventurePersistenceException("could not load GM provider binding", exception); }
    }

    @Override public void save(ProviderBinding binding) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO gm_provider_binding(session_id,provider,model,reasoning,state_version,turn_in_progress) VALUES (?,?,?,?,?,?) ON CONFLICT(session_id) DO UPDATE SET provider=EXCLUDED.provider,model=EXCLUDED.model,reasoning=EXCLUDED.reasoning,state_version=EXCLUDED.state_version,turn_in_progress=EXCLUDED.turn_in_progress")) {
            bind(statement, binding); statement.executeUpdate();
        } catch (Exception exception) { throw new AdventurePersistenceException("could not save GM provider binding", exception); }
    }

    @Override public boolean compareAndSet(UUID sessionId, long expectedVersion, ProviderBinding updated) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "UPDATE gm_provider_binding SET provider=?,model=?,reasoning=?,state_version=?,turn_in_progress=? WHERE session_id=? AND state_version=?")) {
            statement.setString(1, updated.selection().provider()); statement.setString(2, updated.selection().model());
            statement.setString(3, updated.selection().reasoning()); statement.setLong(4, updated.stateVersion());
            statement.setBoolean(5, updated.turnInProgress()); statement.setObject(6, sessionId); statement.setLong(7, expectedVersion);
            return statement.executeUpdate() == 1;
        } catch (Exception exception) { throw new AdventurePersistenceException("could not update GM provider binding", exception); }
    }

    private static void bind(java.sql.PreparedStatement statement, ProviderBinding binding) throws java.sql.SQLException {
        statement.setObject(1, binding.sessionId()); statement.setString(2, binding.selection().provider());
        statement.setString(3, binding.selection().model()); statement.setString(4, binding.selection().reasoning());
        statement.setLong(5, binding.stateVersion()); statement.setBoolean(6, binding.turnInProgress());
    }
}
