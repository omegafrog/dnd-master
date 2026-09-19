package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.DefaultResolutionPort;
import com.dndmaster.adventure.application.runtime.ResolutionPort;
import org.junit.jupiter.api.Test;

class DefaultResolutionPortTest {
    private final DefaultResolutionPort resolution = new DefaultResolutionPort();

    @Test
    void resolves_spatial_check_at_the_typed_rules_boundary() {
        assertTrue(resolution.resolvePlayerCheck(new ResolutionPort.PlayerCheckRequest("perception", 15, 15)).success());
        assertFalse(resolution.resolvePlayerCheck(new ResolutionPort.PlayerCheckRequest("perception", 15, 14)).success());
    }

    @Test
    void rejects_a_missing_difficulty_instead_of_using_a_default_check_target() {
        assertThrows(IllegalArgumentException.class,
                () -> resolution.resolvePlayerCheck(new ResolutionPort.PlayerCheckRequest("", 10, 10)));
    }
}
