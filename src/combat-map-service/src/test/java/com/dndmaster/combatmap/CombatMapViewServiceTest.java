package com.dndmaster.combatmap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dndmaster.combatmap.application.view.CombatMapViewService;
import com.dndmaster.combatmap.application.view.CombatMapViewStore;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.domain.AdventureId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatMapViewServiceTest {
    @Test
    void mapless_situation_removes_previous_map_from_adventure_player_view() {
        CombatMapViewStore store = mock(CombatMapViewStore.class);
        var service = new CombatMapViewService(store, request -> null, request -> null);
        AdventureId adventureId = new AdventureId(UUID.randomUUID());
        MapOwnerId ownerId = new MapOwnerId(UUID.randomUUID());

        service.deactivateForMaplessSituation(adventureId, ownerId);

        verify(store).deactivateForAdventure(adventureId, ownerId);
    }
}
