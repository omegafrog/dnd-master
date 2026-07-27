package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatOperation;
import com.dndmaster.adventure.application.combat.CombatOperationRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class TransactionBoundaryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS combat_operation_checkpoint");
            statement.execute("""
                    CREATE TABLE combat_operation_checkpoint (
                        sequence BIGSERIAL PRIMARY KEY,
                        operation_id UUID NOT NULL,
                        character_verified BOOLEAN NOT NULL,
                        dice_total INTEGER,
                        movement_completed BOOLEAN NOT NULL,
                        ai_state_controlled BOOLEAN NOT NULL,
                        judgment TEXT
                    )
                    """);
        }
    }

    @Test
    void pendingAndEachAggregateResultCommitBeforeTheNextExternalCall() throws Exception {
        CheckpointRepository repository = new CheckpointRepository();
        BoundaryProbe probe = new BoundaryProbe();
        AiCombatPort ai = new AiCombatPort() {
            @Override public void controlState(CombatActionCommand command) { probe.assertCommittedCheckpointVisible(); }
            @Override public String adjudicate(CombatActionCommand command, int diceTotal) {
                probe.assertCommittedCheckpointVisible();
                return "hit";
            }
        };
        AdventureCombatApplicationService service = new AdventureCombatApplicationService(
                repository,
                command -> probe.assertCommittedCheckpointVisible(),
                command -> { probe.assertCommittedCheckpointVisible(); return 18; },
                command -> probe.assertCommittedCheckpointVisible(),
                ai);

        service.resolveCombatAction(command());

        assertEquals(5, probe.externalCalls);
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT count(*) FROM combat_operation_checkpoint")) {
                assertTrue(rows.next());
                assertEquals(6, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("""
                    SELECT character_verified, dice_total, movement_completed, ai_state_controlled, judgment
                    FROM combat_operation_checkpoint ORDER BY sequence LIMIT 1
                    """)) {
                assertTrue(rows.next());
                assertTrue(!rows.getBoolean(1) && rows.getObject(2) == null
                        && !rows.getBoolean(3) && !rows.getBoolean(4) && rows.getString(5) == null,
                        "the first independently committed checkpoint must be PENDING");
            }
            try (var rows = statement.executeQuery("""
                    SELECT judgment FROM combat_operation_checkpoint ORDER BY sequence DESC LIMIT 1
                    """)) {
                assertTrue(rows.next());
                assertEquals("hit", rows.getString(1));
            }
        }
    }

    private static CombatActionCommand command() {
        return new CombatActionCommand(
                UUID.randomUUID(), AdventureId.generate(), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), CombatActorRole.PLAYER, "attack", "A1>B1");
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static final class BoundaryProbe {
        private int externalCalls;

        void assertCommittedCheckpointVisible() {
            externalCalls++;
            try (Connection connection = connection(); var statement = connection.createStatement()) {
                assertTrue(connection.getAutoCommit(), "external calls must not inherit an open database transaction");
                try (var transaction = statement.executeQuery("SELECT txid_current_if_assigned() IS NULL")) {
                    assertTrue(transaction.next() && transaction.getBoolean(1),
                            "external call started while a database transaction was assigned");
                }
                try (var checkpoints = statement.executeQuery("SELECT count(*) > 0 FROM combat_operation_checkpoint")) {
                    assertTrue(checkpoints.next() && checkpoints.getBoolean(1),
                            "PENDING/result checkpoint must commit before an external call");
                }
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static final class CheckpointRepository implements CombatOperationRepository {
        private final Map<UUID, CombatOperation> operations = new ConcurrentHashMap<>();

        @Override
        public Optional<CombatOperation> findById(UUID operationId) {
            return Optional.ofNullable(operations.get(operationId));
        }

        @Override
        public void save(CombatOperation operation) {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try (var statement = connection.prepareStatement("""
                        INSERT INTO combat_operation_checkpoint (
                            operation_id, character_verified, dice_total, movement_completed,
                            ai_state_controlled, judgment
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setObject(1, operation.id());
                    statement.setBoolean(2, operation.isCharacterVerified());
                    if (operation.diceTotal().isPresent()) statement.setInt(3, operation.diceTotal().orElseThrow());
                    else statement.setNull(3, java.sql.Types.INTEGER);
                    statement.setBoolean(4, operation.isMovementCompleted());
                    statement.setBoolean(5, operation.isAiStateControlled());
                    statement.setString(6, operation.judgment().orElse(null));
                    statement.executeUpdate();
                }
                operations.put(operation.id(), operation);
                connection.commit();
            } catch (Exception exception) {
                throw new IllegalStateException("cannot commit aggregate checkpoint", exception);
            }
        }
    }
}
