package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DoorMapMutationTest {
    private final CombatMapViewStore store = mock(CombatMapViewStore.class);
    private final MapOwnerId owner = new MapOwnerId(UUID.randomUUID());
    private final GridPosition position = new GridPosition(2, 2);
    private final CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
            new RuleSetId(UUID.randomUUID()), new GridSpec(5, 5, 50, 5), new PlayerId(owner.value()),
            List.of(), Set.of(), List.of(), 0, null);

    private CombatMapViewService service() {
        map.replaceDoors(Set.of(new Door(position, false, true)));
        when(store.find(map.id())).thenReturn(Optional.of(new VersionedOwnedCombatMap(map, owner, 0)));
        return new CombatMapViewService(store, source -> null, mock(AiMapGenerationPort.class));
    }

    @Test
    void 실제_문_열기_요청이_잠금을_우회하지_못한다() {
        var service = service();
        assertThrows(CombatMapMovementDeniedException.class,
                () -> service.changeDoor(map.id(), owner, 0, UUID.randomUUID(), position, true));
        assertTrue(map.doors().iterator().next().locked());
        verify(store, never()).update(any(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void 닫힌_문_요청은_기존_잠금을_해제하지_않는다() {
        var result = service().changeDoor(map.id(), owner, 0, UUID.randomUUID(), position, false);
        assertTrue(result.doors().iterator().next().locked());
        assertFalse(result.doors().iterator().next().open());
    }

    @Test
    void 격자와_벽_편집은_기존_문_상태를_초기화하지_않는다() {
        var result = service().updateLayout(map.id(), owner, 0, UUID.randomUUID(), Set.of(),
                List.of(new Door(position, false)), List.of(), "", null, null);
        assertTrue(result.doors().iterator().next().locked());
        assertFalse(result.doors().iterator().next().open());
    }
}
