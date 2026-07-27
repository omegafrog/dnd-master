package com.dndmaster.identityaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.identityaccess.domain.player.OwnerPlayerId;
import org.junit.jupiter.api.Test;

class DomainValidationTest {
    @Test
    void ownerPlayerIdRequiresAStoredUuid() {
        assertThrows(IllegalArgumentException.class, () -> OwnerPlayerId.fromStoredValue(" "));
        assertEquals("123e4567-e89b-12d3-a456-426614174000",
                OwnerPlayerId.fromStoredValue("123e4567-e89b-12d3-a456-426614174000").value());
    }
}
