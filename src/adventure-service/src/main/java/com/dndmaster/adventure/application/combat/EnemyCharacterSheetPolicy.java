package com.dndmaster.adventure.application.combat;

import java.util.Set;

/** Accepts a candidate only when its complete profile and every citation are in the pinned evidence set. */
public final class EnemyCharacterSheetPolicy {
    private EnemyCharacterSheetPolicy() {}

    public static EnemyCharacterSheet verify(EnemyCharacterSheet candidate, Set<String> pinnedEvidenceKeys) {
        if (candidate == null || pinnedEvidenceKeys == null || pinnedEvidenceKeys.isEmpty()) {
            throw new IllegalArgumentException("ENEMY_SHEET_SOURCE_SCOPE_REQUIRED");
        }
        var citations = new java.util.ArrayList<String>();
        citations.add(candidate.statBlock().source().citationKey());
        candidate.abilities().forEach(ability -> citations.addAll(ability.citationKeys()));
        candidate.actions().forEach(action -> citations.addAll(action.citationKeys()));
        if (!pinnedEvidenceKeys.containsAll(citations)) {
            throw new IllegalArgumentException("ENEMY_SHEET_SOURCE_OUTSIDE_PINNED_SCOPE");
        }
        return candidate;
    }
}
