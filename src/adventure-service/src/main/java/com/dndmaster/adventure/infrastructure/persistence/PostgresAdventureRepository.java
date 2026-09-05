package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameState;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import javax.sql.DataSource;

public final class PostgresAdventureRepository implements AdventureRepository {
    private final DataSource dataSource;
    private final DataSource transactionDataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PostgresAdventureRepository(DataSource dataSource) {
        this.transactionDataSource = java.util.Objects.requireNonNull(dataSource);
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(transactionDataSource);
    }

    @Override
    public Optional<Adventure> findById(AdventureId adventureId) {
        String sql = "SELECT * FROM adventure WHERE adventure_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, adventureId.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(mapAdventure(connection, row));
            }
        } catch (SQLException exception) { throw failure("could not load adventure", exception); }
    }

    @Override
    public List<Adventure> findSavedByOwner(OwnerPlayerId ownerPlayerId) {
        String sql = "SELECT adventure_id FROM adventure WHERE owner_player_id = ? AND status = 'SAVED' ORDER BY adventure_id";
        List<AdventureId> ids = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, ownerPlayerId.value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) ids.add(new AdventureId(rows.getObject(1, UUID.class)));
            }
        } catch (SQLException exception) { throw failure("could not list adventures", exception); }
        return ids.stream().map(id -> findById(id).orElseThrow()).toList();
    }

    @Override
    public void save(Adventure adventure) {
        try (Connection connection = dataSource.getConnection()) {
            // Adventure writes are owned by the configured DataSourceTransactionManager.
            boolean managed = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
            boolean priorAutoCommit = connection.getAutoCommit();
            if (!managed) connection.setAutoCommit(false);
            try {
                if (adventure.version() == 0) insert(connection, adventure);
                else update(connection, adventure);
                replaceConversation(connection, adventure);
                if (!managed) connection.commit();
            } catch (SQLException | RuntimeException exception) {
                if (!managed) rollback(connection, exception);
                if (exception instanceof OptimisticAdventureLockException optimistic) throw optimistic;
                throw failure("could not save adventure", exception);
            } finally { if (!managed) connection.setAutoCommit(priorAutoCommit); }
        } catch (SQLException exception) { throw failure("could not access adventure storage", exception); }
    }

    private void insert(Connection connection, Adventure adventure) throws SQLException {
        if (!runtimeColumnsAvailable(connection)) {
            insertLegacy(connection, adventure);
            return;
        }
        String sql = "INSERT INTO adventure(adventure_id, session_id, owner_player_id, scenario_id, rule_set_id, current_scene, npc_state, pending_action, latest_judgment, status, version, party_json, turn_index, last_turn_key, locked_scenario_package_id, locked_scenario_package_revision, game_state_jsonb, disclosure_state_jsonb, current_situation_id, situation_revision, current_situation_jsonb, runtime_added_facts_jsonb) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb)";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            bindCommon(s, adventure);
            s.setString(10, adventure.status().name());
            s.setLong(11, adventure.version());
            s.setString(12, partyJson(adventure));
            s.setInt(13, adventure.turnIndex());
            s.setString(14, adventure.lastTurnKey());
            bindRuntime(s, adventure, 15);
            s.executeUpdate();
        }
    }

    private void insertLegacy(Connection connection, Adventure adventure) throws SQLException {
        String sql = "INSERT INTO adventure(adventure_id, session_id, owner_player_id, scenario_id, rule_set_id, current_scene, npc_state, pending_action, latest_judgment, status, version, party_json, turn_index, last_turn_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            bindCommon(s, adventure);
            s.setString(10, adventure.status().name());
            s.setLong(11, adventure.version());
            s.setString(12, partyJson(adventure));
            s.setInt(13, adventure.turnIndex());
            s.setString(14, adventure.lastTurnKey());
            s.executeUpdate();
        }
    }

    private void update(Connection connection, Adventure adventure) throws SQLException {
        if (!runtimeColumnsAvailable(connection)) {
            updateLegacy(connection, adventure);
            return;
        }
        String sql = "UPDATE adventure SET current_scene=?, npc_state=?, pending_action=?, latest_judgment=?, status=?, version=?, party_json=?, turn_index=?, last_turn_key=?, locked_scenario_package_id=?, locked_scenario_package_revision=?, game_state_jsonb=?::jsonb, disclosure_state_jsonb=?::jsonb, current_situation_id=?, situation_revision=?, current_situation_jsonb=?::jsonb, runtime_added_facts_jsonb=?::jsonb WHERE adventure_id=? AND version=?";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setString(1, adventure.currentContext().currentScene()); s.setString(2, adventure.currentContext().npcState());
            s.setString(3, adventure.currentContext().pendingAction()); s.setString(4, adventure.currentContext().latestJudgment());
            s.setString(5, adventure.status().name()); s.setLong(6, adventure.version()); s.setString(7, partyJson(adventure));
            s.setInt(8, adventure.turnIndex()); s.setString(9, adventure.lastTurnKey());
            bindRuntime(s, adventure, 10);
            s.setObject(18, adventure.id().value()); s.setLong(19, adventure.version() - 1);
            if (s.executeUpdate() != 1) throw new OptimisticAdventureLockException();
        }
    }

    private void updateLegacy(Connection connection, Adventure adventure) throws SQLException {
        String sql = "UPDATE adventure SET current_scene=?, npc_state=?, pending_action=?, latest_judgment=?, status=?, version=?, party_json=?, turn_index=?, last_turn_key=? WHERE adventure_id=? AND version=?";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setString(1, adventure.currentContext().currentScene());
            s.setString(2, adventure.currentContext().npcState());
            s.setString(3, adventure.currentContext().pendingAction());
            s.setString(4, adventure.currentContext().latestJudgment());
            s.setString(5, adventure.status().name());
            s.setLong(6, adventure.version());
            s.setString(7, partyJson(adventure)); s.setInt(8, adventure.turnIndex()); s.setString(9, adventure.lastTurnKey());
            s.setObject(10, adventure.id().value()); s.setLong(11, adventure.version() - 1);
            if (s.executeUpdate() != 1) throw new OptimisticAdventureLockException();
        }
    }

    private static void bindCommon(PreparedStatement s, Adventure adventure) throws SQLException {
        s.setObject(1, adventure.id().value()); s.setObject(2, adventure.sessionId().value());
        s.setObject(3, adventure.ownerPlayerId().value()); s.setObject(4, adventure.scenarioId().value());
        s.setObject(5, adventure.ruleSetId().value());
        s.setString(6, adventure.currentContext().currentScene()); s.setString(7, adventure.currentContext().npcState());
        s.setString(8, adventure.currentContext().pendingAction()); s.setString(9, adventure.currentContext().latestJudgment());
    }

    private void bindRuntime(PreparedStatement s, Adventure adventure, int offset) throws SQLException {
        s.setObject(offset, adventure.lockedScenarioPackageId());
        if (adventure.lockedScenarioPackageId() == null) s.setNull(offset + 1, Types.BIGINT);
        else s.setLong(offset + 1, adventure.lockedScenarioPackageRevision());
        s.setString(offset + 2, writeJson(adventure.gameState()));
        s.setString(offset + 3, writeJson(adventure.disclosureState()));
        if (adventure.currentSituation() == null) {
            s.setNull(offset + 4, Types.OTHER); s.setNull(offset + 5, Types.BIGINT); s.setNull(offset + 6, Types.VARCHAR);
        } else {
            s.setObject(offset + 4, adventure.currentSituation().situationId());
            s.setLong(offset + 5, adventure.currentSituation().revision());
            s.setString(offset + 6, writeJson(adventure.currentSituation()));
        }
        s.setString(offset + 7, writeJson(adventure.runtimeAddedFacts()));
    }

    private static void replaceConversation(Connection connection, Adventure adventure) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM adventure_conversation WHERE adventure_id=?")) {
            delete.setObject(1, adventure.id().value()); delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO adventure_conversation(adventure_id, sequence, speaker, content) VALUES (?, ?, ?, ?)")) {
            for (ConversationEntry entry : adventure.conversation()) {
                insert.setObject(1, adventure.id().value()); insert.setLong(2, entry.sequence());
                insert.setString(3, entry.speaker()); insert.setString(4, entry.content()); insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private Adventure mapAdventure(Connection connection, ResultSet row) throws SQLException {
        UUID id = row.getObject("adventure_id", UUID.class);
        List<ConversationEntry> conversation = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT sequence, speaker, content FROM adventure_conversation WHERE adventure_id=? ORDER BY sequence")) {
            statement.setObject(1, id);
            try (ResultSet entries = statement.executeQuery()) {
                while (entries.next()) conversation.add(new ConversationEntry(entries.getLong(1), entries.getString(2), entries.getString(3)));
            }
        }
        boolean runtime = runtimeColumnsAvailable(connection);
        var lockedPackageId = runtime ? row.getObject("locked_scenario_package_id", UUID.class) : null;
        long lockedRevision = runtime && lockedPackageId != null ? row.getLong("locked_scenario_package_revision") : 0;
        GameState gameState = runtime ? readGameState(row.getString("game_state_jsonb")) : GameState.empty();
        DisclosureState disclosureState = runtime ? readDisclosureState(row.getString("disclosure_state_jsonb")) : DisclosureState.empty();
        CurrentSituation situation = runtime ? readSituation(row.getString("current_situation_jsonb")) : null;
        List<RuntimeAddedFact> runtimeFacts = runtime ? readRuntimeFacts(row.getString("runtime_added_facts_jsonb")) : List.of();
        return Adventure.rehydrateWithRuntimeState(new AdventureId(id), new SessionId(row.getObject("session_id", UUID.class)),
                new OwnerPlayerId(row.getObject("owner_player_id", UUID.class)), new ScenarioId(row.getObject("scenario_id", UUID.class)),
                new RuleSetId(row.getObject("rule_set_id", UUID.class)), party(row),
                conversation, new AdventureContext(row.getString("current_scene"), row.getString("npc_state"), row.getString("pending_action"), row.getString("latest_judgment")),
                AdventureStatus.valueOf(row.getString("status")), row.getLong("version"), row.getInt("turn_index"), row.getString("last_turn_key"),
                lockedPackageId, lockedRevision, gameState, disclosureState, situation, runtimeFacts);
    }
    private List<AdventurePartyMember> party(ResultSet row) throws SQLException { String json = row.getString("party_json"); if (json == null || json.isBlank()) return List.of(); try { return objectMapper.readValue(json, new TypeReference<List<AdventurePartyMember>>() {}); } catch (Exception e) { throw new SQLException("could not read adventure party", e); } }
    private String partyJson(Adventure adventure) throws SQLException { try { return objectMapper.writeValueAsString(adventure.party()); } catch (Exception e) { throw new SQLException("could not write adventure party", e); } }

    private String writeJson(Object value) throws SQLException {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new SQLException("could not serialize adventure runtime state", exception); }
    }

    private GameState readGameState(String value) throws SQLException {
        if (value == null || value.isBlank()) return GameState.empty();
        try { return objectMapper.readValue(value, GameState.class); }
        catch (Exception exception) { throw new SQLException("could not read game state", exception); }
    }

    private DisclosureState readDisclosureState(String value) throws SQLException {
        if (value == null || value.isBlank()) return DisclosureState.empty();
        try {
            var node = objectMapper.readTree(value);
            return new DisclosureState(objectMapper.convertValue(node.path("disclosedFactIds"), new TypeReference<List<String>>() {}));
        } catch (Exception exception) { throw new SQLException("could not read disclosure state", exception); }
    }

    private CurrentSituation readSituation(String value) throws SQLException {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readValue(value, CurrentSituation.class); }
        catch (Exception exception) { throw new SQLException("could not read current situation", exception); }
    }

    private List<RuntimeAddedFact> readRuntimeFacts(String value) throws SQLException {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, new TypeReference<List<RuntimeAddedFact>>() {}); }
        catch (Exception exception) { throw new SQLException("could not read runtime facts", exception); }
    }

    private static boolean runtimeColumnsAvailable(Connection connection) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "adventure", "locked_scenario_package_id")) {
            return columns.next();
        }
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }
    private static AdventurePersistenceException failure(String message, Throwable cause) { return new AdventurePersistenceException(message, cause); }
}
