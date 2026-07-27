package com.dndmaster.character;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.character.domain.CharacterSheetId;
import org.junit.jupiter.api.Test;

class DomainValidationTest {
    @Test
    void characterSheetIdRequiresAValue() {
        assertThrows(NullPointerException.class, () -> new CharacterSheetId(null));
        assertNotNull(CharacterSheetId.generate().value());
    }
}
