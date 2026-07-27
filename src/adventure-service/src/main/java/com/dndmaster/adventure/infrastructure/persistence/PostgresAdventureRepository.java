package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresAdventureRepository implements AdventureRepository {
    private final DataSource dataSource;

    public PostgresAdventureRepository(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }

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
            boolean priorAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (adventure.version() == 0) insert(connection, adventure);
                else update(connection, adventure);
                replaceConversation(connection, adventure);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                if (exception instanceof OptimisticAdventureLockException optimistic) throw optimistic;
                throw failure("could not save adventure", exception);
            } finally { connection.setAutoCommit(priorAutoCommit); }
        } catch (SQLException exception) { throw failure("could not access adventure storage", exception); }
    }

    private static void insert(Connection connection, Adventure adventure) throws SQLException {
        String sql = "INSERT INTO adventure VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            bindCommon(s, adventure);
            s.setString(11, adventure.status().name());
            s.setLong(12, adventure.version());
            s.executeUpdate();
        }
    }

    private static void update(Connection connection, Adventure adventure) throws SQLException {
        String sql = "UPDATE adventure SET current_scene=?, npc_state=?, pending_action=?, latest_judgment=?, status=?, version=? WHERE adventure_id=? AND version=?";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setString(1, adventure.currentContext().currentScene());
            s.setString(2, adventure.currentContext().npcState());
            s.setString(3, adventure.currentContext().pendingAction());
            s.setString(4, adventure.currentContext().latestJudgment());
            s.setString(5, adventure.status().name());
            s.setLong(6, adventure.version());
            s.setObject(7, adventure.id().value());
            s.setLong(8, adventure.version() - 1);
            if (s.executeUpdate() != 1) throw new OptimisticAdventureLockException();
        }
    }

    private static void bindCommon(PreparedStatement s, Adventure adventure) throws SQLException {
        s.setObject(1, adventure.id().value()); s.setObject(2, adventure.sessionId().value());
        s.setObject(3, adventure.ownerPlayerId().value()); s.setObject(4, adventure.scenarioId().value());
        s.setObject(5, adventure.ruleSetId().value()); s.setObject(6, adventure.characterSheetId().value());
        s.setString(7, adventure.currentContext().currentScene()); s.setString(8, adventure.currentContext().npcState());
        s.setString(9, adventure.currentContext().pendingAction()); s.setString(10, adventure.currentContext().latestJudgment());
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

    private static Adventure mapAdventure(Connection connection, ResultSet row) throws SQLException {
        UUID id = row.getObject("adventure_id", UUID.class);
        List<ConversationEntry> conversation = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT sequence, speaker, content FROM adventure_conversation WHERE adventure_id=? ORDER BY sequence")) {
            statement.setObject(1, id);
            try (ResultSet entries = statement.executeQuery()) {
                while (entries.next()) conversation.add(new ConversationEntry(entries.getLong(1), entries.getString(2), entries.getString(3)));
            }
        }
        return Adventure.rehydrate(new AdventureId(id), new SessionId(row.getObject("session_id", UUID.class)),
                new OwnerPlayerId(row.getObject("owner_player_id", UUID.class)), new ScenarioId(row.getObject("scenario_id", UUID.class)),
                new RuleSetId(row.getObject("rule_set_id", UUID.class)), new CharacterSheetId(row.getObject("character_sheet_id", UUID.class)),
                conversation, new AdventureContext(row.getString("current_scene"), row.getString("npc_state"), row.getString("pending_action"), row.getString("latest_judgment")),
                AdventureStatus.valueOf(row.getString("status")), row.getLong("version"));
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }
    private static AdventurePersistenceException failure(String message, Throwable cause) { return new AdventurePersistenceException(message, cause); }
}
