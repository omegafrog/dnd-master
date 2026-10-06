package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.application.runtime.CombatEnemyAbilityProposal;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import java.util.List;
import java.util.Objects;

/** Immutable, fully source-backed enemy profile. Combat-changing values stay on each participant. */
public record EnemyCharacterSheet(EnemyCharacterSheetIdentity identity, String displayName,
        CombatEnemyStatBlock statBlock, List<CombatEnemyAbilityProposal> abilities,
        List<EnemyCombatAction> actions) {
    private static final java.util.Set<String> ABILITY_NAMES = java.util.Set.of("STR", "DEX", "CON", "INT", "WIS", "CHA");
    public EnemyCharacterSheet {
        Objects.requireNonNull(identity, "sheet identity is required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("sheet display name is required");
        displayName = displayName.trim();
        Objects.requireNonNull(statBlock, "verified combat numbers are required");
        abilities = List.copyOf(abilities == null ? List.of() : abilities);
        var abilityNames = abilities.stream().map(CombatEnemyAbilityProposal::ability).collect(java.util.stream.Collectors.toSet());
        if (abilities.size() != 6 || !abilityNames.equals(ABILITY_NAMES)) {
            throw new IllegalArgumentException("all six enemy ability scores are required");
        }
        actions = List.copyOf(actions == null ? List.of() : actions);
        if (actions.isEmpty()) throw new IllegalArgumentException("at least one source-backed enemy action is required");
    }

    public record EnemyCombatAction(String name, String description, List<String> citationKeys) {
        public EnemyCombatAction {
            if (name == null || name.isBlank() || description == null || description.isBlank()) {
                throw new IllegalArgumentException("enemy action name and rule description are required");
            }
            name = name.trim();
            description = description.trim();
            citationKeys = List.copyOf(citationKeys == null ? List.of() : citationKeys);
            if (citationKeys.isEmpty() || citationKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
                throw new IllegalArgumentException("enemy action source citations are required");
            }
        }
    }
}
