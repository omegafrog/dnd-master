package com.dndmaster.combatmap.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CombatMapApiConfigurationTest {
    @Test
    void internalTokenIsRequiredAtConfigurationBoundary() {
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> new CombatMapApiConfiguration().combatMapApiRequestGuard(""));
    }
}
