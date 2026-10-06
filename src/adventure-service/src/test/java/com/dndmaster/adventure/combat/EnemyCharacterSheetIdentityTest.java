package com.dndmaster.adventure.combat;

import com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EnemyCharacterSheetIdentityTest {
    private final UUID adventureId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID bundleId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID packageId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID rulebookOne = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID rulebookTwo = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void reusesOnlyWithinSameAdventureAndFixedMaterialScopeAndEnemyKind() {
        var first = identity(adventureId, 4, List.of(rulebookTwo, rulebookOne), "Goblin");
        var sameScope = identity(adventureId, 4, List.of(rulebookOne, rulebookTwo), "goblin");

        assertEquals(first, sameScope);
        assertNotEquals(first, identity(adventureId, 5, List.of(rulebookOne, rulebookTwo), "goblin"));
        assertNotEquals(first, identity(UUID.randomUUID(), 4, List.of(rulebookOne, rulebookTwo), "goblin"));
        assertNotEquals(first, identity(adventureId, 4, List.of(rulebookOne), "goblin"));
        assertNotEquals(first, identity(adventureId, 4, List.of(rulebookOne, rulebookTwo), "hobgoblin"));
    }

    private EnemyCharacterSheetIdentity identity(UUID adventure, long revision, List<UUID> books, String kind) {
        return new EnemyCharacterSheetIdentity(adventure, bundleId, revision, packageId, books, kind);
    }
}
