package com.dndmaster.combatmap.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.view.DetectedMapGrid;
import com.dndmaster.combatmap.application.view.MapGridDetectionPort;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.application.view.PreparedMapData;
import com.dndmaster.combatmap.application.view.UploadedMapSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CombatMapApiConfigurationTest {
    @Test
    void internalTokenIsRequiredAtConfigurationBoundary() {
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> new CombatMapApiConfiguration().combatMapApiRequestGuard(""));
    }

    @Test
    void bundledPotentBrewMapDoesNotClaimUndetectedGridBounds() {
        var data = new CombatMapApiConfiguration().aiMapGenerationPort().generate("A_Potent_Brew_Map");

        assertTrue(data.layers().stream().anyMatch(layer -> layer.type().equals("MAP_IMAGE")));
        assertTrue(data.layers().stream().noneMatch(layer -> layer.type().equals("GRID_BOUNDS")));
    }

    @Test
    void uploadedMapPreparationDelegatesGridGeometryToPreprocessingPort() throws Exception {
        var image = new BufferedImage(32, 40, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        MapGridDetectionPort detector = ignored -> new DetectedMapGrid(2, 3, 8, 4, 6, 1.0);

        PreparedMapData prepared = new CombatMapApiConfiguration().mapFilePreparationPort(detector)
                .prepare(new UploadedMapSource("map.png", bytes.toByteArray()));

        assertEquals(new GridSpec(2, 3, 8, 5), prepared.grid());
        assertEquals("4,6,16,24,32,40", prepared.layers().stream()
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
