package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.ruleset.AppliedRuleSetRepository;
import com.dndmaster.adventure.domain.ruleset.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class PostgresAppliedRuleSetRepository implements AppliedRuleSetRepository {
    private final DataSource dataSource;
    public PostgresAppliedRuleSetRepository(DataSource dataSource) { this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource); }
    @Override public Optional<AppliedRuleSet> findById(RuleSetId id) {
        String sql = "SELECT rule_set_id, adventure_id, owner_player_id, edition FROM applied_rule_set WHERE rule_set_id = ?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id.value()); var rows = statement.executeQuery();
            if (!rows.next()) return Optional.empty();
            var rulebookIds = new ArrayList<RegisteredRulebookReference>();
            try (var children = connection.prepareStatement("SELECT rulebook_id FROM applied_rule_set_rulebook WHERE rule_set_id = ? ORDER BY rulebook_id")) {
                children.setObject(1, id.value()); var childRows = children.executeQuery();
                while (childRows.next()) rulebookIds.add(new RegisteredRulebookReference(new RulebookId(childRows.getObject(1, java.util.UUID.class)), new OwnerPlayerId(rows.getObject("owner_player_id", java.util.UUID.class))));
            }
            return Optional.of(new AppliedRuleSet(id, new AdventureId(rows.getObject("adventure_id", java.util.UUID.class)), new OwnerPlayerId(rows.getObject("owner_player_id", java.util.UUID.class)), new DndEdition(rows.getString("edition")), new SelectedRulebooks(rulebookIds)));
        } catch (SQLException error) { throw new IllegalStateException("could not load applied rule set", error); }
    }
    @Override public void save(AppliedRuleSet ruleSet) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var parent = connection.prepareStatement("INSERT INTO applied_rule_set (rule_set_id, adventure_id, owner_player_id, edition) VALUES (?, ?, ?, ?)")) {
                parent.setObject(1, ruleSet.id().value()); parent.setObject(2, ruleSet.adventureId().value()); parent.setObject(3, ruleSet.ownerPlayerId().value()); parent.setString(4, ruleSet.edition().value()); parent.executeUpdate();
            }
            try (var child = connection.prepareStatement("INSERT INTO applied_rule_set_rulebook (rule_set_id, rulebook_id) VALUES (?, ?)")) {
                for (RegisteredRulebookReference reference : ruleSet.selectedRulebooks().values()) { child.setObject(1, ruleSet.id().value()); child.setObject(2, reference.rulebookId().value()); child.addBatch(); }
                child.executeBatch();
            }
            connection.commit();
        } catch (SQLException error) { throw new IllegalStateException("could not save applied rule set", error); }
    }
}
