package com.dndmaster.adventure.application.combat;

import java.util.Optional;

/** Stores only complete sheets that have passed source verification. */
public interface EnemyCharacterSheetRepository {
    Optional<EnemyCharacterSheet> find(EnemyCharacterSheetIdentity identity);
    EnemyCharacterSheet saveIfAbsent(EnemyCharacterSheet sheet);
}
