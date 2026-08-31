package com.dndmaster.combatmap.infrastructure.persistence;

import com.dndmaster.combatmap.application.view.CombatMapViewStore;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.LastSeenState;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import com.dndmaster.combatmap.domain.TokenDiscovery;
import com.dndmaster.combatmap.domain.VisibilitySnapshot;
import com.dndmaster.combatmap.domain.TacticalRuntimeState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresCombatMapViewStore implements CombatMapViewStore {
    private static final String TABLE = "combat_map";
    private static final String TOKEN_TABLE = "combat_map_token";
    private static final String OBSTACLE_TABLE = "combat_map_obstacle";
    private static final String LAYER_TABLE = "combat_map_layer";
    private static final String DOOR_TABLE = "combat_map_door";
    private static final String HISTORY_TABLE = "combat_map_command_history";
    private static final String HISTORY_TOKEN_TABLE = "combat_map_command_token_history";
    private static final String HISTORY_OBSTACLE_TABLE = "combat_map_command_obstacle_history";
    private static final String HISTORY_LAYER_TABLE = "combat_map_command_layer_history";
    private static final String HISTORY_DOOR_TABLE = "combat_map_command_door_history";

    private final DataSource dataSource;

    public PostgresCombatMapViewStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void insert(MapOwnerId owner, CombatMap map) {
        write(owner, map, -1, true, 0L, map.operationKey(), map.operationFingerprint());
        map.markPersisted(0, map.operationKey(), map.operationFingerprint());
    }

    @Override
    public long update(MapOwnerId owner, CombatMap map, long expected) {
        write(owner, map, expected, false, expected + 1, map.operationKey(), map.operationFingerprint());
        map.markPersisted(expected + 1, map.operationKey(), map.operationFingerprint());
        return expected + 1;
    }

    @Override
    public long update(
            MapOwnerId owner,
            CombatMap map,
            long expected,
            long persistedVersion,
            UUID operationKey,
            String operationFingerprint) {
        if (persistedVersion != expected + 1) {
            throw new IllegalArgumentException("persisted version must advance by one");
        }
        write(owner, map, expected, false, persistedVersion, operationKey, operationFingerprint);
        map.markPersisted(persistedVersion, operationKey, operationFingerprint);
        return persistedVersion;
    }

    @Override
    public Optional<VersionedOwnedCombatMap> find(MapId id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + TABLE + " WHERE map_id=?")) {
            statement.setObject(1, id.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(readCurrent(connection, row));
            }
        } catch (SQLException exception) {
            throw new CombatMapPersistenceException("map load failed", exception);
        }
    }

    @Override
    public Optional<VersionedOwnedCombatMap> findByAdventureId(AdventureId adventureId, MapOwnerId owner) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT map.* FROM " + TABLE + " map JOIN adventure_active_tactical_map active_map ON active_map.combat_map_id = map.map_id AND active_map.adventure_id = map.adventure_id AND active_map.owner_player_id = map.owner_player_id WHERE active_map.adventure_id=? AND active_map.owner_player_id=? AND active_map.active=true ORDER BY active_map.stage_position DESC, map.updated_at DESC, map.map_id DESC LIMIT 1")) {
            statement.setObject(1, adventureId.value());
            statement.setObject(2, owner.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readCurrent(connection, row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new CombatMapPersistenceException("map load by adventure failed", exception);
        }
    }

    @Override
    public Optional<VersionedOwnedCombatMap> findByCommandId(UUID commandId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + HISTORY_TABLE + " WHERE command_id=?")) {
            statement.setObject(1, commandId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(readHistory(connection, row));
            }
        } catch (SQLException exception) {
            throw new CombatMapPersistenceException("map command history load failed", exception);
        }
    }

    private void write(
            MapOwnerId owner,
            CombatMap map,
            long expected,
            boolean insert,
            long persistedVersion,
            UUID operationKey,
            String operationFingerprint) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (insert) {
                    insertMap(connection, owner, map, operationKey, operationFingerprint);
                } else {
                    updateMap(connection, owner, map, expected, persistedVersion, operationKey, operationFingerprint);
                }
                replaceCurrentChildren(connection, map);
                writeVisibility(connection, map);
                recordHistory(connection, owner, map, persistedVersion, operationKey, operationFingerprint);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                if (exception instanceof OptimisticCombatMapLockException optimistic) {
                    throw optimistic;
                }
                throw new CombatMapPersistenceException("map save failed", exception);
            }
        } catch (SQLException exception) {
            throw new CombatMapPersistenceException("map DB failed", exception);
        }
    }

    private static void insertMap(Connection connection, MapOwnerId owner, CombatMap map, UUID operationKey, String operationFingerprint)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO combat_map(map_id,owner_player_id,adventure_id,rule_set_id,grid_width,grid_height,cell_size,distance_unit,version,operation_key,operation_fingerprint,runtime_combat_entered,runtime_alarm_raised,runtime_reinforcements_activated,runtime_boss_activated,runtime_reward_discovered,runtime_outcome,runtime_transition_id) VALUES (?,?,?,?,?,?,?,?,0,?,?,?,?,?,?,?,?,?)")) {
            statement.setObject(1, map.id().value());
            statement.setObject(2, owner.value());
            statement.setObject(3, map.adventureId().value());
            statement.setObject(4, map.ruleSetId().value());
            statement.setInt(5, map.grid().width());
            statement.setInt(6, map.grid().height());
            statement.setInt(7, map.grid().cellSize());
            statement.setInt(8, map.grid().distanceUnit());
            statement.setObject(9, operationKey);
            statement.setString(10, operationFingerprint);
            bindRuntime(statement, 11, map.runtimeState());
            statement.executeUpdate();
        }
    }

    private static void updateMap(
            Connection connection,
            MapOwnerId owner,
            CombatMap map,
            long expectedVersion,
            long persistedVersion,
            UUID operationKey,
            String operationFingerprint) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE combat_map SET grid_width=?,grid_height=?,cell_size=?,distance_unit=?,operation_key=?,operation_fingerprint=?,runtime_combat_entered=?,runtime_alarm_raised=?,runtime_reinforcements_activated=?,runtime_boss_activated=?,runtime_reward_discovered=?,runtime_outcome=?,runtime_transition_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE map_id=? AND owner_player_id=? AND version=?")) {
            statement.setInt(1, map.grid().width());
            statement.setInt(2, map.grid().height());
            statement.setInt(3, map.grid().cellSize());
            statement.setInt(4, map.grid().distanceUnit());
            statement.setObject(5, operationKey);
            statement.setString(6, operationFingerprint);
            bindRuntime(statement, 7, map.runtimeState());
            statement.setObject(14, map.id().value());
            statement.setObject(15, owner.value());
            statement.setLong(16, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new OptimisticCombatMapLockException();
            }
        }
    }

    private static void replaceCurrentChildren(Connection connection, CombatMap map) throws SQLException {
        for (String table : List.of(TOKEN_TABLE, OBSTACLE_TABLE, LAYER_TABLE, DOOR_TABLE)) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE map_id=?")) {
                statement.setObject(1, map.id().value());
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO combat_map_token VALUES (?,?,?,?,?,?,?,?)")) {
            for (CombatToken token : map.tokens()) {
                statement.setObject(1, map.id().value());
                statement.setObject(2, token.id().value());
                statement.setString(3, token.type().name());
                statement.setInt(4, token.position().x());
                statement.setInt(5, token.position().y());
                statement.setString(6, token.controller().name());
                statement.setObject(7, token.ownerPlayerId().map(PlayerId::value).orElse(null));
                statement.setString(8, token.discovery().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO combat_map_obstacle VALUES (?,?,?)")) {
            for (GridPosition obstacle : map.obstacles()) {
                statement.setObject(1, map.id().value());
                statement.setInt(2, obstacle.x());
                statement.setInt(3, obstacle.y());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO combat_map_layer(map_id,sequence,layer_type,layer_value,visibility) VALUES (?,?,?,?,?)")) {
            for (int index = 0; index < map.layers().size(); index++) {
                MapLayer layer = map.layers().get(index);
                statement.setObject(1, map.id().value());
                statement.setInt(2, index);
                statement.setString(3, layer.type());
                statement.setString(4, layer.value());
                statement.setString(5, layer.visibility().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement=connection.prepareStatement("INSERT INTO combat_map_door(map_id,x,y,open) VALUES (?,?,?,?)")) {
            for(Door door:map.doors()){statement.setObject(1,map.id().value());statement.setInt(2,door.position().x());statement.setInt(3,door.position().y());statement.setBoolean(4,door.open());statement.addBatch();} statement.executeBatch();
        }
    }

    private void recordHistory(
            Connection connection,
            MapOwnerId owner,
            CombatMap map,
            long persistedVersion,
            UUID operationKey,
            String operationFingerprint) throws SQLException {
        if (operationKey == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + HISTORY_TABLE
                        + " (command_id, map_id, owner_player_id, adventure_id, rule_set_id, grid_width, grid_height, cell_size, distance_unit, version, operation_key, operation_fingerprint, runtime_combat_entered, runtime_alarm_raised, runtime_reinforcements_activated, runtime_boss_activated, runtime_reward_discovered, runtime_outcome, runtime_transition_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (command_id) DO UPDATE SET"
                        + " map_id = EXCLUDED.map_id,"
                        + " owner_player_id = EXCLUDED.owner_player_id,"
                        + " adventure_id = EXCLUDED.adventure_id,"
                        + " rule_set_id = EXCLUDED.rule_set_id,"
                        + " grid_width = EXCLUDED.grid_width,"
                        + " grid_height = EXCLUDED.grid_height,"
                        + " cell_size = EXCLUDED.cell_size,"
                        + " distance_unit = EXCLUDED.distance_unit,"
                        + " version = EXCLUDED.version,"
                        + " operation_key = EXCLUDED.operation_key,"
                        + " operation_fingerprint = EXCLUDED.operation_fingerprint, runtime_combat_entered = EXCLUDED.runtime_combat_entered, runtime_alarm_raised = EXCLUDED.runtime_alarm_raised, runtime_reinforcements_activated = EXCLUDED.runtime_reinforcements_activated, runtime_boss_activated = EXCLUDED.runtime_boss_activated, runtime_reward_discovered = EXCLUDED.runtime_reward_discovered, runtime_outcome = EXCLUDED.runtime_outcome, runtime_transition_id = EXCLUDED.runtime_transition_id")) {
            statement.setObject(1, operationKey);
            statement.setObject(2, map.id().value());
            statement.setObject(3, owner.value());
            statement.setObject(4, map.adventureId().value());
            statement.setObject(5, map.ruleSetId().value());
            statement.setInt(6, map.grid().width());
            statement.setInt(7, map.grid().height());
            statement.setInt(8, map.grid().cellSize());
            statement.setInt(9, map.grid().distanceUnit());
            statement.setLong(10, persistedVersion);
            statement.setObject(11, operationKey);
            statement.setString(12, operationFingerprint);
            bindRuntime(statement, 13, map.runtimeState());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + HISTORY_TOKEN_TABLE + " WHERE command_id=?")) {
            statement.setObject(1, operationKey);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + HISTORY_TOKEN_TABLE + " (command_id, sequence, token_id, token_type, x, y, controller, owner_player_id, discovery) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int index = 0;
            for (CombatToken token : map.tokens()) {
                statement.setObject(1, operationKey);
                statement.setInt(2, index++);
                statement.setObject(3, token.id().value());
                statement.setString(4, token.type().name());
                statement.setInt(5, token.position().x());
                statement.setInt(6, token.position().y());
                statement.setString(7, token.controller().name());
                statement.setObject(8, token.ownerPlayerId().map(PlayerId::value).orElse(null));
                statement.setString(9, token.discovery().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + HISTORY_OBSTACLE_TABLE + " WHERE command_id=?")) {
            statement.setObject(1, operationKey);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + HISTORY_OBSTACLE_TABLE + " (command_id, sequence, x, y) VALUES (?, ?, ?, ?)")) {
            int index = 0;
            for (GridPosition obstacle : map.obstacles()) {
                statement.setObject(1, operationKey);
                statement.setInt(2, index++);
                statement.setInt(3, obstacle.x());
                statement.setInt(4, obstacle.y());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + HISTORY_LAYER_TABLE + " WHERE command_id=?")) {
            statement.setObject(1, operationKey);
            statement.executeUpdate();
        }
        try (PreparedStatement statement=connection.prepareStatement("DELETE FROM "+HISTORY_DOOR_TABLE+" WHERE command_id=?")){statement.setObject(1,operationKey);statement.executeUpdate();}
        try (PreparedStatement statement=connection.prepareStatement("INSERT INTO "+HISTORY_DOOR_TABLE+" (command_id,sequence,x,y,open) VALUES (?,?,?,?,?)")){int index=0;for(Door door:map.doors()){statement.setObject(1,operationKey);statement.setInt(2,index++);statement.setInt(3,door.position().x());statement.setInt(4,door.position().y());statement.setBoolean(5,door.open());statement.addBatch();}statement.executeBatch();}
        writeHistoryVisibility(connection, map, operationKey);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + HISTORY_LAYER_TABLE + " (command_id, sequence, layer_type, layer_value, visibility) VALUES (?, ?, ?, ?, ?)")) {
            for (int index = 0; index < map.layers().size(); index++) {
                MapLayer layer = map.layers().get(index);
                statement.setObject(1, operationKey);
                statement.setInt(2, index);
                statement.setString(3, layer.type());
                statement.setString(4, layer.value());
                statement.setString(5, layer.visibility().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private VersionedOwnedCombatMap readCurrent(Connection connection, ResultSet row) throws SQLException {
        GridSpec grid = new GridSpec(row.getInt("grid_width"), row.getInt("grid_height"), row.getInt("cell_size"), row.getInt("distance_unit"));
        List<CombatToken> tokens = readTokens(connection, TOKEN_TABLE, "map_id", row.getObject("map_id", UUID.class));
        Set<GridPosition> obstacles = new HashSet<>(readPositions(connection, OBSTACLE_TABLE, "map_id", row.getObject("map_id", UUID.class)));
        List<MapLayer> layers = readLayers(connection, LAYER_TABLE, "map_id", row.getObject("map_id", UUID.class));
        Set<Door> doors = readDoors(connection, DOOR_TABLE, "map_id", row.getObject("map_id", UUID.class));
        UUID ownerId = row.getObject("owner_player_id", UUID.class);
        CombatMap map = new CombatMap(
                new MapId(row.getObject("map_id", UUID.class)),
                new AdventureId(row.getObject("adventure_id", UUID.class)),
                new RuleSetId(row.getObject("rule_set_id", UUID.class)),
                grid,
                new PlayerId(ownerId),
                tokens,
                obstacles,
                layers,
                row.getLong("version"),
                row.getString("operation_key") == null ? null : UUID.fromString(row.getString("operation_key")),
                row.getString("operation_fingerprint"));
        readVisibility(row, map);
        map.replaceRuntimeState(readRuntime(row));
        map.replaceDoors(doors);
        return new VersionedOwnedCombatMap(map, new MapOwnerId(ownerId), row.getLong("version"));
    }

    private VersionedOwnedCombatMap readHistory(Connection connection, ResultSet row) throws SQLException {
        UUID commandId = row.getObject("command_id", UUID.class);
        GridSpec grid = new GridSpec(row.getInt("grid_width"), row.getInt("grid_height"), row.getInt("cell_size"), row.getInt("distance_unit"));
        List<CombatToken> tokens = readHistoryTokens(connection, commandId);
        Set<GridPosition> obstacles = new HashSet<>(readHistoryPositions(connection, commandId));
        List<MapLayer> layers = readHistoryLayers(connection, commandId);
        Set<Door> doors = readDoors(connection, HISTORY_DOOR_TABLE, "command_id", commandId);
        UUID ownerId = row.getObject("owner_player_id", UUID.class);
        CombatMap map = new CombatMap(
                new MapId(row.getObject("map_id", UUID.class)),
                new AdventureId(row.getObject("adventure_id", UUID.class)),
                new RuleSetId(row.getObject("rule_set_id", UUID.class)),
                grid,
                new PlayerId(ownerId),
                tokens,
                obstacles,
                layers,
                row.getLong("version"),
                row.getString("operation_key") == null ? null : UUID.fromString(row.getString("operation_key")),
                row.getString("operation_fingerprint"));
        readVisibility(row, map);
        map.replaceRuntimeState(readRuntime(row));
        map.replaceDoors(doors);
        return new VersionedOwnedCombatMap(map, new MapOwnerId(ownerId), row.getLong("version"));
    }

    private List<CombatToken> readTokens(Connection connection, String table, String fkColumn, UUID fkValue) throws SQLException {
        List<CombatToken> tokens = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + fkColumn + "=? ORDER BY token_id")) {
            statement.setObject(1, fkValue);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    tokens.add(readToken(rows));
                }
            }
        }
        return tokens;
    }

    private List<GridPosition> readPositions(Connection connection, String table, String fkColumn, UUID fkValue) throws SQLException {
        List<GridPosition> positions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT x,y FROM " + table + " WHERE " + fkColumn + "=? ORDER BY x, y")) {
            statement.setObject(1, fkValue);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    positions.add(new GridPosition(rows.getInt(1), rows.getInt(2)));
                }
            }
        }
        return positions;
    }

    private List<MapLayer> readLayers(Connection connection, String table, String fkColumn, UUID fkValue) throws SQLException {
        List<MapLayer> layers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + fkColumn + "=? ORDER BY sequence")) {
            statement.setObject(1, fkValue);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    layers.add(new MapLayer(rows.getString("layer_type"), rows.getString("layer_value"), LayerVisibility.valueOf(rows.getString("visibility"))));
                }
            }
        }
        return layers;
    }
    private Set<Door> readDoors(Connection connection, String table, String fkColumn, UUID fkValue) throws SQLException {
        Set<Door> doors=new HashSet<>(); try(PreparedStatement statement=connection.prepareStatement("SELECT x,y,open FROM "+table+" WHERE "+fkColumn+"=?")){statement.setObject(1,fkValue);try(ResultSet rows=statement.executeQuery()){while(rows.next())doors.add(new Door(new GridPosition(rows.getInt(1),rows.getInt(2)),rows.getBoolean(3)));}} return doors;
    }

    private List<CombatToken> readHistoryTokens(Connection connection, UUID commandId) throws SQLException {
        List<CombatToken> tokens = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM " + HISTORY_TOKEN_TABLE + " WHERE command_id=? ORDER BY sequence")) {
            statement.setObject(1, commandId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    tokens.add(readToken(rows));
                }
            }
        }
        return tokens;
    }

    private List<GridPosition> readHistoryPositions(Connection connection, UUID commandId) throws SQLException {
        List<GridPosition> positions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT x,y FROM " + HISTORY_OBSTACLE_TABLE + " WHERE command_id=? ORDER BY sequence")) {
            statement.setObject(1, commandId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    positions.add(new GridPosition(rows.getInt(1), rows.getInt(2)));
                }
            }
        }
        return positions;
    }

    private List<MapLayer> readHistoryLayers(Connection connection, UUID commandId) throws SQLException {
        List<MapLayer> layers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM " + HISTORY_LAYER_TABLE + " WHERE command_id=? ORDER BY sequence")) {
            statement.setObject(1, commandId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    layers.add(new MapLayer(rows.getString("layer_type"), rows.getString("layer_value"), LayerVisibility.valueOf(rows.getString("visibility"))));
                }
            }
        }
        return layers;
    }

    private static CombatToken readToken(ResultSet row) throws SQLException {
        return new CombatToken(
                new TokenId(row.getObject("token_id", UUID.class)),
                TokenType.valueOf(row.getString("token_type")),
                new GridPosition(row.getInt("x"), row.getInt("y")),
                TokenController.valueOf(row.getString("controller")),
                row.getObject("owner_player_id") == null ? null : new PlayerId(row.getObject("owner_player_id", UUID.class)),
                row.getString("discovery") == null ? TokenDiscovery.DISCOVERED : TokenDiscovery.valueOf(row.getString("discovery")));
    }

    private static void writeVisibility(Connection connection, CombatMap map) throws SQLException {
        VisibilitySnapshot snapshot = map.visibilitySnapshot();
        if (snapshot == null) return;
        try (PreparedStatement statement = connection.prepareStatement("UPDATE combat_map SET visibility_current=?, visibility_explored=?, visibility_last_seen=?, visibility_rule_turn=? WHERE map_id=?")) {
            statement.setString(1, encodePositions(snapshot.current()));
            statement.setString(2, encodePositions(snapshot.explored()));
            statement.setString(3, snapshot.lastSeen().stream().map(last -> last.tokenId().value()+"|"+last.type()+"|"+last.position().x()+"|"+last.position().y()+"|"+last.expiresAtTurn()).collect(java.util.stream.Collectors.joining(";")));
            statement.setLong(4, snapshot.ruleTurn()); statement.setObject(5, map.id().value()); statement.executeUpdate();
        }
    }
    private static void writeHistoryVisibility(Connection connection, CombatMap map, UUID commandId) throws SQLException {
        VisibilitySnapshot snapshot=map.visibilitySnapshot(); if(snapshot==null) return;
        try (PreparedStatement statement=connection.prepareStatement("UPDATE combat_map_command_history SET visibility_current=?, visibility_explored=?, visibility_last_seen=?, visibility_rule_turn=? WHERE command_id=?")) {
            statement.setString(1, encodePositions(snapshot.current())); statement.setString(2, encodePositions(snapshot.explored()));
            statement.setString(3, snapshot.lastSeen().stream().map(last -> last.tokenId().value()+"|"+last.type()+"|"+last.position().x()+"|"+last.position().y()+"|"+last.expiresAtTurn()).collect(java.util.stream.Collectors.joining(";")));
            statement.setLong(4, snapshot.ruleTurn()); statement.setObject(5, commandId); statement.executeUpdate();
        }
    }
    private static String encodePositions(Collection<GridPosition> positions) { return positions.stream().map(p -> p.x()+","+p.y()).collect(java.util.stream.Collectors.joining(";")); }
    private static void readVisibility(ResultSet row, CombatMap map) throws SQLException {
        String current = row.getString("visibility_current"), explored = row.getString("visibility_explored"), encodedLastSeen = row.getString("visibility_last_seen");
        if (current == null || explored == null) return;
        Set<GridPosition> currentPositions = decodePositions(current), exploredPositions = decodePositions(explored); List<LastSeenState> states = new ArrayList<>();
        if (encodedLastSeen != null && !encodedLastSeen.isBlank()) for (String encoded : encodedLastSeen.split(";")) { String[] p = encoded.split("\\|"); if (p.length == 5) states.add(new LastSeenState(new TokenId(UUID.fromString(p[0])), TokenType.valueOf(p[1]), new GridPosition(Integer.parseInt(p[2]), Integer.parseInt(p[3])), Long.parseLong(p[4]))); }
        map.replaceVisibility(new VisibilitySnapshot(currentPositions, exploredPositions, map.tokens().stream().filter(t -> (currentPositions.contains(t.position()) && t.discovery()!=TokenDiscovery.HIDDEN) || (t.type()==TokenType.TRAP && t.discovery()!=TokenDiscovery.HIDDEN)).map(CombatToken::id).collect(java.util.stream.Collectors.toSet()), states, row.getLong("visibility_rule_turn")));
    }
    private static TacticalRuntimeState readRuntime(ResultSet row) throws SQLException {
        return new TacticalRuntimeState(row.getBoolean("runtime_combat_entered"), row.getBoolean("runtime_alarm_raised"),
                row.getBoolean("runtime_reinforcements_activated"), row.getBoolean("runtime_boss_activated"),
                row.getBoolean("runtime_reward_discovered"), row.getString("runtime_outcome"), row.getString("runtime_transition_id"));
    }
    private static void bindRuntime(PreparedStatement statement, int start, TacticalRuntimeState state) throws SQLException {
        statement.setBoolean(start, state.combatEntered()); statement.setBoolean(start + 1, state.alarmRaised());
        statement.setBoolean(start + 2, state.reinforcementsActivated()); statement.setBoolean(start + 3, state.bossActivated());
        statement.setBoolean(start + 4, state.rewardDiscovered()); statement.setString(start + 5, state.outcome());
        statement.setString(start + 6, state.transitionId());
    }
    private static Set<GridPosition> decodePositions(String value) { Set<GridPosition> result = new HashSet<>(); if (value == null || value.isBlank()) return result; for (String encoded : value.split(";")) { String[] p = encoded.split(","); if (p.length == 2) result.add(new GridPosition(Integer.parseInt(p[0]), Integer.parseInt(p[1]))); } return result; }
}
