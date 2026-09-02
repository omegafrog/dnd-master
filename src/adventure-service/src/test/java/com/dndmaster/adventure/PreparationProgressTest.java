package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.PreparationProgress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreparationProgressTest {
    @Test
    void derives_percentage_only_for_a_known_positive_total() {
        assertEquals(25, new PreparationProgress("MAP", 1, 4).percentage());
        assertNull(new PreparationProgress("MAP", 1, null).percentage());
        assertThrows(IllegalArgumentException.class, () -> new PreparationProgress("MAP", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PreparationProgress("MAP", 5, 4));
    }

    @Test
    void keeps_legacy_integer_progress_readable() {
        PreparationProgress legacy = PreparationProgress.legacy(70);
        assertEquals("LEGACY", legacy.phase());
        assertEquals(70, legacy.percentage());
    }
}
