package com.dndmaster.diceroll.infrastructure.persistence;

import com.dndmaster.diceroll.application.DiceRollRepository;
import com.dndmaster.diceroll.domain.*;
import java.sql.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PostgresDiceRollRepository implements DiceRollRepository {
    private final DataSource dataSource;

    public PostgresDiceRollRepository(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }

    @Override
    public void save(DiceRoll roll) {
        DiceResult result = roll.result().orElseThrow(() -> new IllegalStateException("completed roll requires result"));
        String sql = "INSERT INTO dice_roll(roll_id, adventure_id, rule_set_id, scope, dice_count, dice_sides, modifier, faces, total, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, roll.id().value()); statement.setObject(2, roll.adventureId().value());
            statement.setObject(3, roll.ruleSetId().value()); statement.setString(4, roll.scope().name());
            statement.setInt(5, roll.expression().count()); statement.setInt(6, roll.expression().sides());
            statement.setInt(7, roll.expression().modifier());
            statement.setArray(8, connection.createArrayOf("integer", result.faces().toArray(Integer[]::new)));
            statement.setInt(9, result.total()); statement.executeUpdate();
        } catch (SQLException exception) { throw failure("could not save dice roll", exception); }
    }

    public Optional<DiceRoll> findById(RollId id) {
        String sql = "SELECT * FROM dice_roll WHERE roll_id=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                DiceExpression expression = new DiceExpression(row.getInt("dice_count"), row.getInt("dice_sides"), row.getInt("modifier"));
                DiceRoll roll = new DiceRoll(new RollId(row.getObject("roll_id", UUID.class)),
                        new AdventureId(row.getObject("adventure_id", UUID.class)), new RuleSetId(row.getObject("rule_set_id", UUID.class)),
                        RollScope.valueOf(row.getString("scope")), expression);
                Integer[] storedFaces = (Integer[]) row.getArray("faces").getArray();
                roll.recordBuiltInResult(new DiceResult(Arrays.asList(storedFaces), row.getInt("total")));
                return Optional.of(roll);
            }
        } catch (SQLException exception) { throw failure("could not load dice roll", exception); }
    }

    private static DiceRollPersistenceException failure(String message, Throwable cause) {
        return new DiceRollPersistenceException(message, cause);
    }
}
