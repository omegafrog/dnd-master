package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Starts a source-defined encounter when the player clearly chooses to fight its supported threat. */
public final class PlayerCombatIntentPolicy {
    private PlayerCombatIntentPolicy() { }

    public static List<CombatEnemyProposal> proposalsFor(String action, CurrentSituation situation, ScenarioModel model) {
        Objects.requireNonNull(situation, "current situation must not be null");
        Objects.requireNonNull(model, "scenario model must not be null");
        if (!clearlyRequestsCombat(action)) return List.of();
        String actionText = normalize(action);
        String situationText = normalize(String.join(" ", safe(situation.location()), safe(situation.problem()),
                safe(situation.threat()), safe(situation.goal())));
        List<CombatScenarioDefinition> actionMatches = model.combatScenarios().stream()
                .filter(encounter -> mentions(actionText, encounter)).toList();
        List<CombatScenarioDefinition> supported = actionMatches.isEmpty()
                ? model.combatScenarios().stream().filter(encounter -> mentions(situationText, encounter)).toList()
                : actionMatches;
        if (supported.size() != 1) return List.of();
        CombatScenarioDefinition encounter = supported.getFirst();
        return List.of(new CombatEnemyProposal(encounter.scenarioId(), encounter.enemyKey(),
                encounter.displayName(), encounter.count(), CombatStartMode.SCENARIO));
    }

    private static boolean clearlyRequestsCombat(String action) {
        if (action == null || action.isBlank()) return false;
        String value = normalize(action);
        if (value.matches(".*(?:don't|do not|never|avoid|refuse to|won't|will not)(?:\\s+want to|\\s+wish to)?\\s+(?:fight|attack|battle|engage|kill|strike).*")) return false;
        if (value.matches(".*(?:공격|싸우|전투|싸움|죽이|때리).{0,8}(?:않|말|피하|원치|싫).*")) return false;
        if (value.matches(".*(?:안|못|않고|말고|피해서).{0,6}(?:공격|싸우|전투|죽이|때리).*")) return false;
        return value.matches(".*\\b(?:fight|attack|battle|combat|engage|kill|strike|shoot)\\b.*")
                || value.matches(".*(?:공격|싸우|전투|싸움|죽이|때리|쳐).*" );
    }

    private static boolean mentions(String text, CombatScenarioDefinition encounter) {
        if (text.contains(normalize(encounter.displayName()))
                || text.contains(normalize(encounter.enemyKey().replace('-', ' ')))) return true;
        String displayName = normalize(encounter.displayName());
        if (displayName.matches(".*[가-힣].*")) {
            String[] nameParts = displayName.split(" ");
            return nameParts.length > 1 && java.util.Arrays.stream(nameParts).allMatch(text::contains);
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
