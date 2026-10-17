package com.dndmaster.adventure;

import com.dndmaster.adventure.domain.runtime.ExplorationGroup;
import com.dndmaster.adventure.domain.runtime.ExplorationState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplorationGroupTest {
    @Test void explorationStateRoundTripsWithTheStoredStateShape() {
        var state = new ExplorationState(List.of(group(member(20))));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertEquals(state, assertDoesNotThrow(() -> mapper.readValue(mapper.writeValueAsBytes(state), ExplorationState.class)));
    }

    @Test void adventurePersistsEveryPartyMemberOnceAndEnforcesOwnerAndVersion() {
        var owner = new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(UUID.randomUUID());
        var first = new com.dndmaster.adventure.domain.adventure.CharacterSheetId(UUID.randomUUID());
        var second = new com.dndmaster.adventure.domain.adventure.CharacterSheetId(UUID.randomUUID());
        var adventure = com.dndmaster.adventure.domain.adventure.Adventure.create(
                com.dndmaster.adventure.domain.adventure.AdventureId.generate(),
                com.dndmaster.adventure.domain.adventure.SessionId.generate(), owner,
                new com.dndmaster.adventure.domain.adventure.ScenarioId(UUID.randomUUID()),
                new com.dndmaster.adventure.domain.adventure.RuleSetId(UUID.randomUUID()),
                List.of(partyMember(first), partyMember(second)),
                new com.dndmaster.adventure.domain.adventure.AdventureContext("장면", "위협", "상황", null));
        var state = new ExplorationState(List.of(group(
                new ExplorationGroup.Member(first.value(), ExplorationGroup.Formation.FRONT, 30),
                new ExplorationGroup.Member(second.value(), ExplorationGroup.Formation.REAR, 20))));

        adventure.updateExplorationState(owner, 0, state);

        assertEquals(1, adventure.version());
        assertEquals(state, adventure.gameState().values().get("explorationState"));
        assertThrows(IllegalArgumentException.class, () -> adventure.updateExplorationState(owner, 1,
                new ExplorationState(List.of(group(new ExplorationGroup.Member(first.value(), ExplorationGroup.Formation.FRONT, 30))))));
        assertThrows(IllegalStateException.class, () -> adventure.updateExplorationState(owner, 0, state));
        assertThrows(com.dndmaster.adventure.domain.adventure.AdventureAccessDeniedException.class, () -> adventure.updateExplorationState(
                new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(UUID.randomUUID()), 1, state));
    }

    @Test void anIndividualIsAValidGroupAndSpeedUsesTheSlowestMember() {
        var individual = group(member(10));
        assertEquals(10, individual.speed());
        assertEquals(20, group(member(30), member(20), member(25)).speed());
    }

    @Test void groupRequiresMembersAndUniqueCharacters() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new ExplorationGroup(id, UUID.randomUUID(), List.of()));
        var member = member(30);
        assertThrows(IllegalArgumentException.class,
                () -> new ExplorationGroup(id, UUID.randomUUID(), List.of(member, member)));
    }

    @Test void splitCreatesSeparateGroupAndOnlyExplicitMergeCombinesThem() {
        var front = member(30);
        var rear = new ExplorationGroup.Member(UUID.randomUUID(), ExplorationGroup.Formation.REAR, 20);
        UUID groupId = UUID.randomUUID(), tokenId = UUID.randomUUID();
        var state = new ExplorationState(List.of(new ExplorationGroup(groupId, tokenId, List.of(front, rear))));
        var split = state.split(groupId, UUID.randomUUID(), UUID.randomUUID(), List.of(rear));
        assertEquals(2, split.groups().size());
        assertEquals(1, split.groups().get(0).members().size());
        assertEquals(1, split.groups().get(1).members().size());
        var merged = split.merge(split.groups().get(0).id(), split.groups().get(1).id(), UUID.randomUUID(), List.of(front, rear));
        assertEquals(1, merged.groups().size());
        assertEquals(2, merged.groups().get(0).members().size());
    }

    private static ExplorationGroup group(ExplorationGroup.Member... members) {
        return new ExplorationGroup(UUID.randomUUID(), UUID.randomUUID(), List.of(members));
    }
    private static ExplorationGroup.Member member(int speed) {
        return new ExplorationGroup.Member(UUID.randomUUID(), ExplorationGroup.Formation.FRONT, speed);
    }
    private static com.dndmaster.adventure.domain.adventure.AdventurePartyMember partyMember(
            com.dndmaster.adventure.domain.adventure.CharacterSheetId id) {
        return new com.dndmaster.adventure.domain.adventure.AdventurePartyMember(id,
                com.dndmaster.adventure.domain.adventure.ControlMode.DIRECT, true, true, true, true, true, true);
    }
}
