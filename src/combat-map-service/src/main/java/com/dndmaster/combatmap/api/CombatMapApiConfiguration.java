package com.dndmaster.combatmap.api;

import com.dndmaster.combatmap.application.movement.AppliedEditionMovementPort;
import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.CombatMapRepository;
import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import com.dndmaster.combatmap.infrastructure.persistence.PostgresCombatMapViewStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class CombatMapApiConfiguration {

    @Bean
    ApiRequestGuard combatMapApiRequestGuard(@Value("${combat-map.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token) {
        return new ApiRequestGuard(token);
    }

    @Bean
    CombatMapViewStore combatMapViewStore(DataSource dataSource) {
        return new PostgresCombatMapViewStore(dataSource);
    }

    @Bean
    CombatMapRepository combatMapRepository(DataSource dataSource) {
        return new CombatMapRepository() {
            private final PostgresCombatMapViewStore store = new PostgresCombatMapViewStore(dataSource);

            @Override
            public java.util.Optional<CombatMap> findById(MapId id) {
                return store.find(id).map(VersionedOwnedCombatMap::map);
            }

            @Override
            public java.util.Optional<CombatMap> findByCommandId(java.util.UUID commandId) {
                return store.findByCommandId(commandId).map(VersionedOwnedCombatMap::map);
            }

            @Override
            public void save(CombatMap map) {
                if (map.ownerPlayerId() == null) {
                    throw new IllegalStateException("combat map owner is required for persistence");
                }
                store.update(
                        new com.dndmaster.combatmap.application.view.MapOwnerId(map.ownerPlayerId().value()),
                        map,
                        map.version(),
                        map.version() + 1,
                        map.operationKey(),
                        map.operationFingerprint());
            }

            @Override
            public void save(CombatMap map, long persistedVersion, java.util.UUID operationKey, String operationFingerprint) {
                if (map.ownerPlayerId() == null) {
                    throw new IllegalStateException("combat map owner is required for persistence");
                }
                store.update(
                        new com.dndmaster.combatmap.application.view.MapOwnerId(map.ownerPlayerId().value()),
                        map,
                        map.version(),
                        persistedVersion,
                        operationKey,
                        operationFingerprint);
            }
        };
    }

    @Bean
    CombatMapViewService combatMapViewService(
            CombatMapViewStore store, MapFilePreparationPort filePort, AiMapGenerationPort aiPort) {
        return new CombatMapViewService(store, filePort, aiPort);
    }

    @Bean
    CombatMapMovementService combatMapMovementService(
            CombatMapRepository repository, AppliedEditionMovementPort movementPort) {
        return new CombatMapMovementService(repository, movementPort);
    }

    @Bean
    MapFilePreparationPort mapFilePreparationPort() {
        return source -> {
            try {
                var image = decodeImage(source);
                if (image == null) throw new IllegalArgumentException("map image format is not supported");
                var detected = new MapGridDetector().detect(image);
                String contentType = source.filename().toLowerCase().endsWith(".jpg") || source.filename().toLowerCase().endsWith(".jpeg") ? "image/jpeg" : "image/png";
                String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(renderPng(source));
                return new PreparedMapData(new GridSpec(detected.width(), detected.height(), detected.cellSize(), 5), List.of(), Set.of(),
                        List.of(new MapLayer("MAP_IMAGE", dataUrl, LayerVisibility.PLAYER_VISIBLE),
                                new MapLayer("GRID_BOUNDS", detected.boundsValue(image.getWidth(), image.getHeight()), LayerVisibility.PLAYER_VISIBLE)));
            } catch (java.io.IOException exception) { throw new IllegalArgumentException("map image cannot be decoded", exception); }
        };
    }

    private static java.awt.image.BufferedImage decodeImage(UploadedMapSource source) throws java.io.IOException {
        var image = ImageIO.read(new ByteArrayInputStream(source.content()));
        if (image != null) return image;
        if (!source.filename().toLowerCase().endsWith(".pdf")) return null;
        try (var document = org.apache.pdfbox.Loader.loadPDF(source.content())) {
            return new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 144);
        }
    }

    private static byte[] renderPng(UploadedMapSource source) throws java.io.IOException {
        if (!source.filename().toLowerCase().endsWith(".pdf")) return source.content();
        try (var document = org.apache.pdfbox.Loader.loadPDF(source.content()); var output = new java.io.ByteArrayOutputStream()) {
            ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 144), "png", output);
            return output.toByteArray();
        }
    }

    @Bean
    AiMapGenerationPort aiMapGenerationPort() {
        return scenarioDescription -> new PreparedMapData(
                new GridSpec(20, 20, 30, 5),
                scenarioDescription != null && scenarioDescription.contains("A_Potent_Brew_Map")
                        ? List.of(new CombatToken(new TokenId(CombatMap.canonicalTokenId("potent-brew-enemy-1")),
                                TokenType.ENEMY, new GridPosition(14, 14), TokenController.AI_GAME_MASTER, null,
                                TokenDiscovery.HIDDEN))
                        : List.of(),
                Set.of(),
                scenarioDescription != null && (scenarioDescription.contains("A_Potent_Brew_Map")
                        || scenarioDescription.contains("page 1 image 1"))
                        ? List.of(new MapLayer("MAP_IMAGE", "/assets/maps/a-potent-brew-map.png", LayerVisibility.PLAYER_VISIBLE),
                                // The bundled image includes a title/paper margin. Keep the
                                // tactical grid aligned to its printed 20x20 play area.
                                new MapLayer("GRID_BOUNDS", "311,105,800,800,1403,992", LayerVisibility.PLAYER_VISIBLE),
                                new MapLayer("INITIAL_FOG", initialFog(), LayerVisibility.AI_ONLY))
                        : List.of());
    }

    private static String initialFog() {
        List<String> cells = new ArrayList<>();
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) {
            if (x > 5 || y > 5) cells.add(x + "," + y);
        }
        return String.join(";", cells);
    }

    @Bean
    AppliedEditionMovementPort appliedEditionMovementPort() {
        return (ruleSetId, appliedEdition) -> 30;
    }

    @Bean
    CombatMapController combatMapController(
            CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard) {
        return new CombatMapController(mapViewService, movementService, requestGuard);
    }
}
