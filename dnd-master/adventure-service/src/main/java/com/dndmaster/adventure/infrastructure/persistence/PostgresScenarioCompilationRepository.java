package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresScenarioCompilationRepository implements ScenarioCompilationRepository {
    private final DataSource dataSource;

    public PostgresScenarioCompilationRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "data source must not be null");
    }

    @Override
    public Optional<ScenarioCompilation> findById(UUID id) {
        return find("compilation_id", id);
    }

    @Override
    public Optional<ScenarioCompilation> findByInputFingerprint(String fingerprint) {
        return find("input_fingerprint", fingerprint);
    }

    private Optional<ScenarioCompilation> find(String column, Object value) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT compilation_id, bundle_id, bundle_revision, input_fingerprint, status, attempt, lease_token, package_id, failure_reason FROM scenario_compilation WHERE " + column + " = ?")) {
            statement.setObject(1, value);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(read(row));
            }
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not load compilation", exception);
        }
    }

    @Override
    public void save(ScenarioCompilation compilation) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO scenario_compilation(compilation_id, bundle_id, bundle_revision, input_fingerprint, status, attempt, lease_token, package_id, failure_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (compilation_id) DO UPDATE SET status = EXCLUDED.status, attempt = EXCLUDED.attempt, lease_token = EXCLUDED.lease_token, package_id = EXCLUDED.package_id, failure_reason = EXCLUDED.failure_reason")) {
            statement.setObject(1, compilation.id()); statement.setObject(2, compilation.bundleId().value());
            statement.setLong(3, compilation.bundleRevision()); statement.setString(4, compilation.inputFingerprint());
            statement.setString(5, compilation.status().name()); statement.setInt(6, compilation.attempt());
            statement.setObject(7, compilation.leaseToken()); statement.setObject(8, compilation.packageId());
            statement.setString(9, compilation.failureReason()); statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not save compilation", exception);
        }
    }

    private static ScenarioCompilation read(ResultSet row) throws SQLException {
        return ScenarioCompilation.rehydrate(
                row.getObject("compilation_id", UUID.class),
                new ScenarioBundleId(row.getObject("bundle_id", UUID.class)),
                row.getLong("bundle_revision"), row.getString("input_fingerprint"),
                ScenarioCompilationStatus.valueOf(row.getString("status")), row.getInt("attempt"),
                row.getObject("lease_token", UUID.class), row.getObject("package_id", UUID.class),
                row.getString("failure_reason"));
    }
}
