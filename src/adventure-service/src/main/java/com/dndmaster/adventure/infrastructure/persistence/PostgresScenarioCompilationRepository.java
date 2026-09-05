package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationDiagnostic;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationInputSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresScenarioCompilationRepository implements ScenarioCompilationRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
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

    @Override public Optional<ScenarioCompilation> findByIdempotencyKey(String key) { return find("idempotency_key", key); }

    private Optional<ScenarioCompilation> find(String column, Object value) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT compilation_id, bundle_id, bundle_revision, input_fingerprint, idempotency_key, status, attempt, lease_token, package_id, failure_reason, input_snapshot_json, diagnostics FROM scenario_compilation WHERE " + column + " = ?")) {
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
                        "INSERT INTO scenario_compilation(compilation_id, bundle_id, bundle_revision, input_fingerprint, idempotency_key, status, attempt, lease_token, package_id, failure_reason, primary_storybook_id, integration_prompt, creativity, input_snapshot_json, diagnostics, processing_started_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, CASE WHEN ? IN ('PROCESSING','RUNNING') THEN CURRENT_TIMESTAMP ELSE NULL END, CURRENT_TIMESTAMP) ON CONFLICT (compilation_id) DO UPDATE SET status = EXCLUDED.status, attempt = EXCLUDED.attempt, lease_token = EXCLUDED.lease_token, package_id = EXCLUDED.package_id, failure_reason = EXCLUDED.failure_reason, diagnostics = EXCLUDED.diagnostics, processing_started_at = EXCLUDED.processing_started_at, updated_at = CURRENT_TIMESTAMP")) {
            statement.setObject(1, compilation.id()); statement.setObject(2, compilation.bundleId().value());
            statement.setLong(3, compilation.bundleRevision()); statement.setString(4, compilation.inputFingerprint()); statement.setString(5, compilation.idempotencyKey());
            statement.setString(6, compilation.status().name()); statement.setInt(7, compilation.attempt());
            statement.setObject(8, compilation.leaseToken()); statement.setObject(9, compilation.packageId());
            statement.setString(10, compilation.failureReason());
            var snapshot = compilation.inputSnapshot();
            statement.setObject(11, snapshot == null ? null : snapshot.primaryStorybookId());
            statement.setString(12, snapshot == null ? "" : snapshot.integrationPrompt());
            statement.setString(13, snapshot == null ? "CONSERVATIVE" : snapshot.creativity().name());
            statement.setString(14, writeJson(snapshot));
            statement.setString(15, writeJson(compilation.diagnostics()));
            statement.setString(16, compilation.status().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not save compilation", exception);
        }
    }

    @Override
    public boolean saveIfLeaseMatches(ScenarioCompilation compilation, UUID expectedLeaseToken) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE scenario_compilation SET status = ?, attempt = ?, lease_token = ?, package_id = ?, failure_reason = ?, diagnostics = ?::jsonb, updated_at = CURRENT_TIMESTAMP WHERE compilation_id = ? AND ((CAST(? AS UUID) IS NULL AND lease_token IS NULL) OR lease_token = ?)")) {
            statement.setString(1, compilation.status().name()); statement.setInt(2, compilation.attempt());
            statement.setObject(3, compilation.leaseToken()); statement.setObject(4, compilation.packageId());
            statement.setString(5, compilation.failureReason()); statement.setString(6, writeJson(compilation.diagnostics()));
            statement.setObject(7, compilation.id()); statement.setObject(8, expectedLeaseToken); statement.setObject(9, expectedLeaseToken);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new ScenarioPackagePersistenceException("could not conditionally save compilation", exception);
        }
    }

    private static ScenarioCompilation read(ResultSet row) throws SQLException {
        return ScenarioCompilation.rehydrate(
                row.getObject("compilation_id", UUID.class),
                new ScenarioBundleId(row.getObject("bundle_id", UUID.class)),
                row.getLong("bundle_revision"), row.getString("input_fingerprint"), row.getString("idempotency_key"),
                ScenarioCompilationStatus.valueOf(row.getString("status")), row.getInt("attempt"),
                row.getObject("lease_token", UUID.class), row.getObject("package_id", UUID.class),
                row.getString("failure_reason"), readSnapshot(row.getString("input_snapshot_json")),
                readDiagnostics(row.getString("diagnostics")));
    }

    private static String writeJson(Object value) {
        if (value == null) return null;
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new ScenarioPackagePersistenceException("could not serialize compilation data", exception); }
    }

    private static ScenarioCompilationInputSnapshot readSnapshot(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        try { return JSON.readValue(value, ScenarioCompilationInputSnapshot.class); }
        catch (JsonProcessingException exception) { throw new ScenarioPackagePersistenceException("could not deserialize compilation input", exception); }
    }

    private static java.util.List<ScenarioCompilationDiagnostic> readDiagnostics(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        try { return JSON.readValue(value, JSON.getTypeFactory().constructCollectionType(java.util.List.class, ScenarioCompilationDiagnostic.class)); }
        catch (JsonProcessingException exception) { throw new ScenarioPackagePersistenceException("could not deserialize compilation diagnostics", exception); }
    }
}
