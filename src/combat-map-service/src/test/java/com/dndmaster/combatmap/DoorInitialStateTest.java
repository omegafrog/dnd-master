package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.combatmap.domain.CombatMapMovementDeniedException;
import com.dndmaster.combatmap.domain.Door;
import com.dndmaster.combatmap.domain.GridPosition;
import org.junit.jupiter.api.Test;

class DoorInitialStateTest {
    @Test
    void 잠긴_초기_문은_일반_열기로_열리지_않는다() {
        var door = new Door(new GridPosition(1, 2), false, true);
        assertThrows(CombatMapMovementDeniedException.class, door::opened);
        assertFalse(door.open());
        assertTrue(door.locked());
        assertThrows(IllegalArgumentException.class, () -> new Door(door.position(), true, true));
    }

    @Test
    void 기존_잠금_없는_문은_정상적으로_열린다() {
        var door = new Door(new GridPosition(1, 2), false).opened();
        assertTrue(door.open());
        assertFalse(door.locked());
    }
}
