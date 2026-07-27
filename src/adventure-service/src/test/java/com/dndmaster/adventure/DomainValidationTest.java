package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import org.junit.jupiter.api.Test;

class DomainValidationTest {
    @Test
    void adventureIdRequiresAValue() {
        assertThrows(NullPointerException.class, () -> new AdventureId(null));
        assertNotNull(AdventureId.generate().value());
    }
}
