package com.dndmaster.adventure.combat;

import com.dndmaster.adventure.application.combat.*;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyCharacterSheetPolicyTest {
    private static final String STAT_CITATION = "RULEBOOK:66666666-6666-6666-6666-666666666666:2:Monster p. 4";

    @Test
    void rejectsActionCitationOutsidePinnedEvidenceBeforeItCanBeSaved() {
        var candidate = candidate("RULEBOOK:foreign:1:page 9");
        assertEquals("ENEMY_SHEET_SOURCE_OUTSIDE_PINNED_SCOPE", assertThrows(IllegalArgumentException.class,
                () -> EnemyCharacterSheetPolicy.verify(candidate, Set.of(STAT_CITATION))).getMessage());
    }

    @Test
    void storesAndReusesOnlyCompleteVerifiedSheet() {
        var sheet = candidate(STAT_CITATION);
        var verified = EnemyCharacterSheetPolicy.verify(sheet, Set.of(STAT_CITATION));
        var repository = new InMemoryEnemyCharacterSheetRepository();

        assertSame(verified, repository.saveIfAbsent(verified));
        assertSame(verified, repository.saveIfAbsent(candidate(STAT_CITATION)));
        assertEquals(verified, repository.find(verified.identity()).orElseThrow());
    }

    @Test
    void requires_the_full_six_ability_set_and_individually_cited_actions() {
        var valid = candidateWithCompleteRulesProfile();
        assertEquals(6, EnemyCharacterSheetPolicy.verify(valid, Set.of(STAT_CITATION, "RULEBOOK:action:1:p. 1"))
                .abilities().size());
        assertThrows(IllegalArgumentException.class, () -> EnemyCharacterSheetPolicy.verify(
                new EnemyCharacterSheet(identity(), "Goblin", statBlock(), valid.abilities().subList(0, 5), valid.actions()),
                Set.of(STAT_CITATION, "RULEBOOK:action:1:p. 1")));
        assertThrows(IllegalArgumentException.class, () -> EnemyCharacterSheetPolicy.verify(
                new EnemyCharacterSheet(identity(), "Goblin", statBlock(), valid.abilities(), List.of(
                        new EnemyCharacterSheet.EnemyCombatAction("Claw", "Claw attack", List.of("RULEBOOK:foreign:1:p. 1")))),
                Set.of(STAT_CITATION)));
    }

    @Test
    void rejectsCandidateWithoutRulesBackedAction() {
        assertThrows(IllegalArgumentException.class, () -> new EnemyCharacterSheet(identity(), "Goblin",
                statBlock(), abilities(), List.of()));
    }

    private static EnemyCharacterSheet candidateWithCompleteRulesProfile() {
        return new EnemyCharacterSheet(identity(), "Goblin", statBlock(), abilities(), List.of(
                new EnemyCharacterSheet.EnemyCombatAction("Scimitar", "Melee weapon attack", List.of("RULEBOOK:action:1:p. 1"))));
    }

    private static List<com.dndmaster.adventure.application.runtime.CombatEnemyAbilityProposal> abilities() {
        return List.of("STR", "DEX", "CON", "INT", "WIS", "CHA").stream().map(ability ->
                new com.dndmaster.adventure.application.runtime.CombatEnemyAbilityProposal(ability, 10, List.of(STAT_CITATION))).toList();
    }

    private static EnemyCharacterSheet candidate(String actionCitation) {
        return new EnemyCharacterSheet(identity(), "Goblin", statBlock(), abilities(), List.of(
                new EnemyCharacterSheet.EnemyCombatAction("Scimitar", "근접 무기 공격", List.of(actionCitation))));
    }
    private static EnemyCharacterSheetIdentity identity() {
        return new EnemyCharacterSheetIdentity(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"), 1,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                List.of(UUID.fromString("77777777-7777-7777-7777-777777777777")), "goblin");
    }
    private static CombatEnemyStatBlock statBlock() {
        return new CombatEnemyStatBlock(15, 7, 4, "1d6+2",
                new CombatStatBlockSource(UUID.fromString("66666666-6666-6666-6666-666666666666"), 2,
                        "Monster p. 4", STAT_CITATION), 2);
    }
}
