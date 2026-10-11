package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegacyDiceCommandIdTest {
    @Test
    void supplied_idempotency_key_is_the_command_identity_for_retries() {
        UUID key = UUID.randomUUID();

        assertEquals(key, AdventureController.legacyDiceCommandId(key));
        assertEquals(key, AdventureController.legacyDiceCommandId(key));
    }

    @Test
    void missing_key_preserves_separate_independent_legacy_executions() {
        assertNotEquals(AdventureController.legacyDiceCommandId(null), AdventureController.legacyDiceCommandId(null));
    }
}
