package com.dndmaster.combatmap.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatMapApiConfigurationTest {
    @Test
    void internalTokenIsRequiredAtConfigurationBoundary() {
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> new CombatMapApiConfiguration().combatMapApiRequestGuard(""));
    }

    @Test
    void bundledPotentBrewMapPublishesPrintedGridBounds() {
        var data = new CombatMapApiConfiguration().aiMapGenerationPort().generate("A_Potent_Brew_Map");

        assertTrue(data.layers().stream().anyMatch(layer -> layer.type().equals("MAP_IMAGE")));
        assertEquals("311,105,800,800,1403,992", data.layers().stream()
                .filter(layer -> layer.type().equals("GRID_BOUNDS"))
                .findFirst().orElseThrow().value());
    }

    @Test
    void bundledPotentBrewMapProvidesTacticalTokensAndInitialFog() {
        var data = new CombatMapApiConfiguration().aiMapGenerationPort().generate("A_Potent_Brew_Map");

        assertTrue(data.tokens().stream().anyMatch(token -> token.type().name().equals("ENEMY")));
        assertTrue(data.layers().stream().anyMatch(layer -> layer.type().equals("INITIAL_FOG")
                && layer.visibility().name().equals("AI_ONLY") && !layer.value().isBlank()));
    }
}
