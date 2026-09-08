package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import com.dndmaster.combatmap.infrastructure.persistence.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresMapGridAlignmentStoreIntegrationTest {
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("combat_map").withUsername("combat_map").withPassword("combat_map");
    static DataSource dataSource;
    private MapOwnerId owner; private CombatMap map; private MapGridAlignmentService service;

    @BeforeAll static void start() { PG.start(); dataSource = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); Flyway.configure().dataSource(dataSource).load().migrate(); }
    @AfterAll static void stop() { PG.stop(); }
    @BeforeEach void setup() throws SQLException {
        try (Connection c=dataSource.getConnection(); Statement s=c.createStatement()) { s.execute("TRUNCATE combat_map CASCADE"); }
        owner = new MapOwnerId(UUID.randomUUID());
        map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), new GridSpec(8, 8, 50, 5),
                new PlayerId(owner.value()), List.of(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER, new GridPosition(1, 2), TokenController.PLAYER, new PlayerId(owner.value()))),
                Set.of(new GridPosition(3, 3)), List.of(new MapLayer("MAP_IMAGE", "data:image/png;base64,test", LayerVisibility.PLAYER_VISIBLE)), 0, null);
        PostgresCombatMapViewStore maps = new PostgresCombatMapViewStore(dataSource); maps.insert(owner, map);
        service = new MapGridAlignmentService(maps, new PostgresMapGridAlignmentStore(dataSource));
    }

    @Test void savesAndReplaysDecimalAlignmentWithoutWritingMapState() {
        MapGridAlignment initial = service.find(map.id(), owner);
        UUID commandId = UUID.randomUUID();
        MapGridAlignmentRequest request = new MapGridAlignmentRequest(commandId, 0, initial.imageRevision(), 12.25, 8.5, 31.75);
        MapGridAlignment saved = service.apply(map.id(), owner, request);
        assertEquals(saved, service.apply(map.id(), owner, request));
        assertEquals(saved, service.find(map.id(), owner));
        try (Connection c=dataSource.getConnection(); PreparedStatement s=c.prepareStatement("SELECT version,grid_width,grid_height,cell_size FROM combat_map WHERE map_id=?")) {
            s.setObject(1, map.id().value()); try (ResultSet rows=s.executeQuery()) { assertTrue(rows.next()); assertEquals(0, rows.getLong(1)); assertEquals(8, rows.getInt(2)); assertEquals(8, rows.getInt(3)); assertEquals(50, rows.getInt(4)); }
        } catch (SQLException e) { throw new AssertionError(e); }
    }

    @Test void rejectsStaleVersionAndChangedCommandPayload() {
        MapGridAlignment initial = service.find(map.id(), owner); UUID commandId = UUID.randomUUID();
        service.apply(map.id(), owner, new MapGridAlignmentRequest(commandId, 0, initial.imageRevision(), 1.25, 2.5, 25.5));
        assertThrows(MapGridAlignmentConflictException.class, () -> service.apply(map.id(), owner, new MapGridAlignmentRequest(UUID.randomUUID(), 0, initial.imageRevision(), 1.25, 2.5, 25.5)));
        assertThrows(MapGridAlignmentConflictException.class, () -> service.apply(map.id(), owner, new MapGridAlignmentRequest(commandId, 0, initial.imageRevision(), 2.25, 2.5, 25.5)));
    }

    private record DriverManagerDataSource(String url, String user, String password) implements DataSource {
        public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, user, password); }
        public Connection getConnection(String username, String password) throws SQLException { return DriverManager.getConnection(url, username, password); }
        public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException(); }
        public boolean isWrapperFor(Class<?> iface) { return false; }
        public java.io.PrintWriter getLogWriter() { return null; } public void setLogWriter(java.io.PrintWriter out) {}
        public void setLoginTimeout(int seconds) {} public int getLoginTimeout() { return 0; }
        public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
