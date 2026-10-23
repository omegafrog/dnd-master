package com.dndmaster.combatmap;

import com.dndmaster.combatmap.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CombatTokenTransitionTest {
    @Test void placesMembersBehindNearestEnemyAndPreservesGroupOnExit() {
        UUID adventureId = UUID.randomUUID(), ownerId = UUID.randomUUID(), groupId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID(), groupTokenId = UUID.randomUUID(), enemyId = UUID.randomUUID();
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(adventureId),
                new RuleSetId(UUID.randomUUID()), new GridSpec(9, 9, 1, 5), new PlayerId(ownerId),
                List.of(new CombatToken(new TokenId(groupTokenId), TokenType.PLAYER, new GridPosition(4, 4),
                                TokenController.PLAYER, new PlayerId(ownerId)),
                        new CombatToken(new TokenId(enemyId), TokenType.ENEMY, new GridPosition(7, 4),
                                TokenController.AI_GAME_MASTER, null)), List.of(), List.of(), 0, null);

        var entered = map.enterCombat(List.of(new CombatMap.CombatGroup(groupId, groupTokenId,
                List.of(new CombatMap.CombatMember(characterId, CombatMap.Formation.FRONT)))), new PlayerId(ownerId));
        assertEquals(new GridPosition(4, 4), entered.getFirst().position());
        assertEquals(UUID.nameUUIDFromBytes(("player-" + characterId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                entered.getFirst().id().value());

        var restored = map.exitCombat(List.of(new CombatMap.CombatGroup(groupId, groupTokenId,
                List.of(new CombatMap.CombatMember(characterId, CombatMap.Formation.FRONT)))), new PlayerId(ownerId));
        assertEquals(1, restored.size());
        assertEquals(groupTokenId, restored.getFirst().id().value());
    }

    @Test void rejectsInvalidFixedCellWithoutChangingMap() {
        UUID ownerId = UUID.randomUUID(), characterId = UUID.randomUUID(), groupTokenId = UUID.randomUUID();
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new GridSpec(3, 3, 1, 5), new PlayerId(ownerId),
                List.of(new CombatToken(new TokenId(groupTokenId), TokenType.PLAYER, new GridPosition(0, 0),
                        TokenController.PLAYER, new PlayerId(ownerId))), List.of(new GridPosition(0, 0)), List.of(), 0, null);
        var before = map.tokens();
        assertThrows(CombatMapTransitionPlacementRequiredException.class,
                () -> map.enterCombat(List.of(new CombatMap.CombatGroup(UUID.randomUUID(), groupTokenId,
                        List.of(new CombatMap.CombatMember(characterId, CombatMap.Formation.FRONT)))), new PlayerId(ownerId)));
        assertEquals(before, map.tokens());
    }

    @Test void rejectsFullPartyWhenLaterMemberHasNoValidCellAndLeavesEveryTokenUnchanged() {
        UUID ownerId = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID groupTokenId = UUID.randomUUID();
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new GridSpec(1, 1, 1, 5), new PlayerId(ownerId),
                List.of(new CombatToken(new TokenId(groupTokenId), TokenType.PLAYER, new GridPosition(0, 0),
                        TokenController.PLAYER, new PlayerId(ownerId))), List.of(), List.of(), 4, null);
        var before = map.tokens();
        var group = new CombatMap.CombatGroup(UUID.randomUUID(), groupTokenId, List.of(
                new CombatMap.CombatMember(first, CombatMap.Formation.FRONT),
                new CombatMap.CombatMember(second, CombatMap.Formation.MIDDLE)));

        assertThrows(CombatMapTransitionPlacementRequiredException.class,
                () -> map.enterCombat(List.of(group), new PlayerId(ownerId)));
        assertEquals(before, map.tokens());
    }
}
