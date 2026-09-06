package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Fail-closed policy between GM combat proposals and source-backed combat situations. */
public final class CombatScenarioGroundingPolicy {
    private CombatScenarioGroundingPolicy() {}

    public static List<CombatEnemyProposal> ground(ScenarioModel model, CurrentSituation situation,
            List<CombatEnemyProposal> proposals) {
        Objects.requireNonNull(model, "scenario model must not be null");
        Objects.requireNonNull(situation, "current situation must not be null");
        if (proposals == null || proposals.isEmpty()) throw new IllegalArgumentException("COMBAT_SCENARIO_REQUIRED");
        Set<String> seen = new HashSet<>();
        return proposals.stream().map(proposal -> groundOne(model, situation, proposal, seen, List.of(), null)).toList();
    }

    public static List<CombatEnemyProposal> ground(ScenarioModel model, CurrentSituation situation,
            List<CombatEnemyProposal> proposals, List<RuntimeEvidence> storybookEvidence,
            List<RuntimeEvidence> rulebookEvidence) {
        Objects.requireNonNull(storybookEvidence, "storybook evidence must not be null");
        Objects.requireNonNull(rulebookEvidence, "rulebook evidence must not be null");
        if (proposals == null || proposals.isEmpty()) throw new IllegalArgumentException("COMBAT_SCENARIO_REQUIRED");
        Set<String> seen = new HashSet<>();
        return proposals.stream().map(proposal -> groundOne(model, situation, proposal, seen, storybookEvidence, rulebookEvidence)).toList();
    }

    private static CombatEnemyProposal groundOne(ScenarioModel model, CurrentSituation situation,
            CombatEnemyProposal proposal, Set<String> seen, List<RuntimeEvidence> storybookEvidence,
            List<RuntimeEvidence> rulebookEvidence) {
        if (proposal == null) {
            throw new IllegalArgumentException("COMBAT_SCENARIO_REFERENCE_REQUIRED");
        }
        if (proposal.mode() == CombatStartMode.INSTANT) {
            return groundInstant(situation, proposal, seen, rulebookEvidence);
        }
        if (proposal.mode() == CombatStartMode.SITUATION) {
            return groundSituation(situation, proposal, seen, storybookEvidence, rulebookEvidence);
        }
        if (proposal.scenarioId() == null || proposal.scenarioId().isBlank()) {
            throw new IllegalArgumentException("COMBAT_SCENARIO_REFERENCE_REQUIRED");
        }
        if (!seen.add(proposal.scenarioId())) throw new IllegalArgumentException("COMBAT_SCENARIO_DUPLICATE");
        CombatScenarioDefinition definition = model.combatScenario(proposal.scenarioId())
                .orElseThrow(() -> new IllegalArgumentException("COMBAT_SCENARIO_NOT_IN_SCENARIO_MODEL"));
        if (situation.activeCombatScenarioId() != null
                && !situation.activeCombatScenarioId().equals(definition.scenarioId())) {
            throw new IllegalArgumentException("COMBAT_SCENARIO_ACTIVE_MISMATCH");
        }
        if (!definition.displayName().equalsIgnoreCase(proposal.name())
                && !definition.enemyKey().equalsIgnoreCase(proposal.name())) {
            throw new IllegalArgumentException("COMBAT_SCENARIO_ENEMY_MISMATCH");
        }
        CombatEnemyProposal grounded = new CombatEnemyProposal(definition.scenarioId(), definition.enemyKey(),
                definition.displayName(), definition.count(), CombatStartMode.SCENARIO);
        return withStats(grounded, rulebookEvidence);
    }

    private static CombatEnemyProposal groundSituation(CurrentSituation situation, CombatEnemyProposal proposal,
            Set<String> seen, List<RuntimeEvidence> storybookEvidence, List<RuntimeEvidence> rulebookEvidence) {
        boolean supportedByThisTurn = storybookEvidence.stream().anyMatch(evidence -> evidence != null
                && (containsIgnoreCase(evidence.excerpt(), proposal.name())
                || containsIgnoreCase(evidence.excerpt(), proposal.enemyKey().replace('-', ' '))));
        boolean supportedByCurrentSituation = containsEnemy(situation.location(), proposal)
                || containsEnemy(situation.problem(), proposal)
                || containsEnemy(situation.threat(), proposal)
                || containsEnemy(situation.goal(), proposal);
        if (!supportedByThisTurn && !supportedByCurrentSituation) {
            throw new IllegalArgumentException("COMBAT_STORY_EVIDENCE_REQUIRED");
        }
        var stats = RulebookCombatStatBlockResolver.resolve(proposal, rulebookEvidence)
                .orElseThrow(() -> new IllegalArgumentException("COMBAT_STAT_BLOCK_NOT_FOUND"));
        String scenarioId = "situation-" + situation.situationId() + "-" + proposal.enemyKey().toLowerCase(java.util.Locale.ROOT);
        if (!seen.add(scenarioId)) throw new IllegalArgumentException("COMBAT_SCENARIO_DUPLICATE");
        return new CombatEnemyProposal(scenarioId, proposal.enemyKey(), proposal.name(), proposal.count(),
                CombatStartMode.SITUATION, stats);
    }

    private static boolean containsIgnoreCase(String text, String value) {
        return text != null && value != null && !value.isBlank()
                && text.toLowerCase(java.util.Locale.ROOT).contains(value.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean containsEnemy(String text, CombatEnemyProposal proposal) {
        return containsIgnoreCase(text, proposal.name())
                || containsIgnoreCase(text, proposal.enemyKey().replace('-', ' '));
    }

    private static CombatEnemyProposal groundInstant(CurrentSituation situation, CombatEnemyProposal proposal,
            Set<String> seen, List<RuntimeEvidence> rulebookEvidence) {
        var stats = RulebookCombatStatBlockResolver.resolve(proposal, rulebookEvidence)
                .orElseThrow(() -> new IllegalArgumentException("COMBAT_STAT_BLOCK_NOT_FOUND"));
        String scenarioId = "instant-" + proposal.enemyKey().toLowerCase(java.util.Locale.ROOT) + "-"
                + Integer.toUnsignedString(stats.source().locator().hashCode(), 36);
        if (!seen.add(scenarioId)) throw new IllegalArgumentException("COMBAT_SCENARIO_DUPLICATE");
        if (situation.activeCombatScenarioId() != null
                && !situation.activeCombatScenarioId().equals(scenarioId)) {
            throw new IllegalArgumentException("COMBAT_SCENARIO_ACTIVE_MISMATCH");
        }
        return new CombatEnemyProposal(scenarioId, proposal.enemyKey(), proposal.name(), proposal.count(),
                CombatStartMode.INSTANT, stats);
    }

    private static CombatEnemyProposal withStats(CombatEnemyProposal proposal, List<RuntimeEvidence> evidence) {
        if (evidence == null) return proposal;
        return proposal.withStatBlock(RulebookCombatStatBlockResolver.resolve(proposal, evidence)
                .orElseThrow(() -> new IllegalArgumentException("COMBAT_STAT_BLOCK_NOT_FOUND")));
    }
}
