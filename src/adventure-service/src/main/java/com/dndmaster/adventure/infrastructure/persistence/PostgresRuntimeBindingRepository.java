package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.RuntimeBindingRepository;
import com.dndmaster.adventure.domain.adventure.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresRuntimeBindingRepository implements RuntimeBindingRepository {
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PostgresRuntimeBindingRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(
                java.util.Objects.requireNonNull(dataSource, "data source must not be null"));
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public Optional<RuntimeBinding> findCurrentByAdventureId(AdventureId adventureId) {
        String sql = "SELECT * FROM adventure_runtime_binding WHERE adventure_id = ? ORDER BY binding_version DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, adventureId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RuntimeBindingPersistenceException("could not load runtime binding", exception);
        }
    }

    @Override
    public List<RuntimeBinding> findAllByAdventureId(AdventureId adventureId) {
        String sql = "SELECT * FROM adventure_runtime_binding WHERE adventure_id = ? ORDER BY binding_version";
        List<RuntimeBinding> bindings = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, adventureId.value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    bindings.add(map(rows));
                }
            }
            return bindings;
        } catch (SQLException exception) {
            throw new RuntimeBindingPersistenceException("could not list runtime bindings", exception);
        }
    }

    @Override
    public void save(RuntimeBinding binding) {
        String sql = """
                INSERT INTO adventure_runtime_binding (
                    adventure_id, binding_version, owner_player_id, scenario_package_id, scenario_package_revision,
                    rulebook_ids_json, party_json, engine_id, tool_ids_json, game_system_definition_version, character_blueprint_version,
                    playability_status, playability_warnings_json, playability_blockers_json,
                    playability_limits_json, active_source_context_json, source_context_candidates_json
                    , readiness_status, readiness_blockers_json, readiness_warnings_json, readiness_retryable
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (adventure_id, binding_version) DO UPDATE SET
                    owner_player_id = EXCLUDED.owner_player_id,
                    scenario_package_id = EXCLUDED.scenario_package_id,
                    scenario_package_revision = EXCLUDED.scenario_package_revision,
                    rulebook_ids_json = EXCLUDED.rulebook_ids_json,
                    party_json = EXCLUDED.party_json,
                    engine_id = EXCLUDED.engine_id,
                    tool_ids_json = EXCLUDED.tool_ids_json,
                    game_system_definition_version = EXCLUDED.game_system_definition_version,
                    character_blueprint_version = EXCLUDED.character_blueprint_version,
                    playability_status = EXCLUDED.playability_status,
                    playability_warnings_json = EXCLUDED.playability_warnings_json,
                    playability_blockers_json = EXCLUDED.playability_blockers_json,
                    playability_limits_json = EXCLUDED.playability_limits_json,
                    active_source_context_json = EXCLUDED.active_source_context_json,
                    source_context_candidates_json = EXCLUDED.source_context_candidates_json
                    , readiness_status = EXCLUDED.readiness_status
                    , readiness_blockers_json = EXCLUDED.readiness_blockers_json
                    , readiness_warnings_json = EXCLUDED.readiness_warnings_json
                    , readiness_retryable = EXCLUDED.readiness_retryable
                """;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, binding);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeBindingPersistenceException("could not save runtime binding", exception);
        }
    }

    private void bind(PreparedStatement statement, RuntimeBinding binding) throws SQLException {
        statement.setObject(1, binding.adventureId().value());
        statement.setLong(2, binding.bindingVersion());
        statement.setObject(3, binding.ownerPlayerId().value());
        statement.setObject(4, binding.scenarioPackageId());
        statement.setLong(5, binding.scenarioPackageRevision());
        statement.setString(6, write(binding.rulebookIds()));
        statement.setString(7, write(binding.party())); statement.setString(8, binding.engineId());
        statement.setString(9, write(binding.toolIds())); statement.setLong(10, binding.gameSystemDefinitionVersion()); statement.setLong(11, binding.characterBlueprintVersion());
        statement.setString(12, binding.playabilityReport().status().name());
        statement.setString(13, write(binding.playabilityReport().warnings())); statement.setString(14, write(binding.playabilityReport().blockers())); statement.setString(15, write(binding.playabilityReport().limits())); statement.setString(16, write(binding.activeSourceContext())); statement.setString(17, write(binding.playabilityReport().candidates()));
        statement.setString(18, binding.readiness().status().name()); statement.setString(19, write(binding.readiness().blockers())); statement.setString(20, write(binding.readiness().warnings())); statement.setBoolean(21, binding.readiness().retryable());
    }

    private RuntimeBinding map(ResultSet row) throws SQLException {
        return RuntimeBinding.rehydrate(
                new AdventureId(row.getObject("adventure_id", UUID.class)),
                new OwnerPlayerId(row.getObject("owner_player_id", UUID.class)),
                row.getLong("binding_version"),
                row.getObject("scenario_package_id", UUID.class),
                row.getLong("scenario_package_revision"),
                readUuidList(row.getString("rulebook_ids_json")),
                readParty(row.getString("party_json")),
                row.getString("engine_id"),
                readStringList(row.getString("tool_ids_json")), legacySafeLong(row, "game_system_definition_version"), legacySafeLong(row, "character_blueprint_version"),
                new PlayabilityReport(
                        PlayabilityStatus.valueOf(row.getString("playability_status")),
                        readStringList(row.getString("playability_warnings_json")),
                        readStringList(row.getString("playability_blockers_json")),
                        readStringList(row.getString("playability_limits_json")),
                        readCandidates(row.getString("source_context_candidates_json"))),
                readActive(row.getString("active_source_context_json")),
                readReadiness(row, row.getLong("binding_version")));
    }

    private RuntimeReadiness readReadiness(ResultSet row, long bindingVersion) throws SQLException {
        try {
            return new RuntimeReadiness(bindingVersion, RuntimeReadinessStatus.valueOf(row.getString("readiness_status")),
                    readStringList(row.getString("readiness_blockers_json")), readStringList(row.getString("readiness_warnings_json")),
                    row.getBoolean("readiness_retryable"));
        } catch (SQLException missingColumn) {
            return new RuntimeReadiness(bindingVersion, RuntimeReadinessStatus.BLOCKED,
                    List.of("runtime readiness must be re-evaluated"), List.of(), true);
        }
    }
    private static long legacySafeLong(ResultSet row, String column) throws SQLException {
        try { return row.getLong(column); } catch (SQLException missingColumn) { return 0L; }
    }
    private List<AdventurePartyMember> readParty(String json) throws SQLException { if (json == null || json.isBlank()) return List.of(); try { return objectMapper.readValue(json, new TypeReference<List<AdventurePartyMember>>() {}); } catch (Exception e) { throw new SQLException("could not read runtime party", e); } }

    private List<InitialSourceContextCandidate> readCandidates(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<InitialSourceContextCandidate>>() {});
        } catch (Exception exception) {
            throw new SQLException("could not read runtime binding candidates", exception);
        }
    }

    private ActiveSourceContext readActive(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ActiveSourceContext.class);
        } catch (Exception exception) {
            throw new SQLException("could not read runtime binding active source context", exception);
        }
    }

    private String write(Object value) throws SQLException {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new SQLException("could not write runtime binding payload", exception);
        }
    }

    private List<UUID> readUuidList(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<UUID> values = objectMapper.readValue(json, UUID_LIST);
            return values == null ? List.of() : values;
        } catch (Exception exception) {
            throw new SQLException("could not read runtime binding uuid list", exception);
        }
    }

    private List<String> readStringList(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, STRING_LIST);
            return values == null ? List.of() : values;
        } catch (Exception exception) {
            throw new SQLException("could not read runtime binding string list", exception);
        }
    }
}
