package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.domain.rulebook.FileSize;
import org.junit.jupiter.api.Test;

class DomainValidationTest {
    @Test
    void rulebookFileSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new FileSize(0));
        assertEquals(1, new FileSize(1).bytes());
    }
}
