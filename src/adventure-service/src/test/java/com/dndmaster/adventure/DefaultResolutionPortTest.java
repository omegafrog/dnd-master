package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.DefaultResolutionPort;
import com.dndmaster.adventure.application.runtime.ResolutionPort;
import org.junit.jupiter.api.Test;

class DefaultResolutionPortTest {
    private final DefaultResolutionPort resolution = new DefaultResolutionPort();

    @Test
    void resolves_spatial_check_at_the_typed_rules_boundary() {
        ResolutionPort.PlayerCheckResult success = resolution.resolvePlayerCheck(
                new ResolutionPort.PlayerCheckRequest("dnd5e.perception", "2d6", 3, 15, 15));
        ResolutionPort.PlayerCheckResult failure = resolution.resolvePlayerCheck(
                new ResolutionPort.PlayerCheckRequest("dnd5e.perception", "2d6", 3, 15, 14));

        assertTrue(success.success());
        assertFalse(failure.success());
        assertEquals("dnd5e.perception", success.ruleReference());
        assertEquals("2d6", success.diceExpression());
        assertEquals(3, success.modifier());
        assertEquals(15, success.difficulty());
    }

    @Test
    void rejects_a_missing_difficulty_instead_of_using_a_default_check_target() {
        assertThrows(IllegalArgumentException.class,
                () -> resolution.resolvePlayerCheck(new ResolutionPort.PlayerCheckRequest("", "2d6", 0, 10, 10)));
    }
}
