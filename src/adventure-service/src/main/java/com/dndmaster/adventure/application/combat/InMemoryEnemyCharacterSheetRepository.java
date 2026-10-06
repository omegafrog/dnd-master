package com.dndmaster.adventure.application.combat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test/local repository with the same insert-once identity behavior as PostgreSQL. */
public final class InMemoryEnemyCharacterSheetRepository implements EnemyCharacterSheetRepository {
    private final Map<EnemyCharacterSheetIdentity, EnemyCharacterSheet> sheets = new ConcurrentHashMap<>();
    @Override public Optional<EnemyCharacterSheet> find(EnemyCharacterSheetIdentity identity) {
        return Optional.ofNullable(sheets.get(identity));
    }
    @Override public EnemyCharacterSheet saveIfAbsent(EnemyCharacterSheet sheet) {
        return sheets.computeIfAbsent(sheet.identity(), ignored -> sheet);
    }
}
