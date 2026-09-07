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
    MapGridDetectionPort mapGridDetectionPort() {
        return new MapGridDetector();
    }

    @Bean
    MapFilePreparationPort mapFilePreparationPort(MapGridDetectionPort gridDetection) {
        return new MapPreparationPipeline(new MapContentBoundsDetector(), gridDetection, new FallbackGridPolicy(20))::prepare;
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
    AiMapGenerationPort aiMapGenerationPort(
            @Value("${combat-map.integration.ai-game-master.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${combat-map.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            @Value("${combat-map.integration.ai-game-master.map-generation-timeout:300s}") java.time.Duration timeout) {
        return new HttpAiMapGenerationGateway(java.net.http.HttpClient.newHttpClient(),
                java.net.URI.create(baseUrl), timeout, objectMapper, internalToken);
    }

    @Bean
    MapImageEvidencePort mapImageEvidencePort(
            @Value("${combat-map.integration.rule-knowledge.base-url:http://127.0.0.1:8080/}") String baseUrl,
            @Value("${combat-map.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        return new HttpMapImageEvidenceGateway(java.net.http.HttpClient.newHttpClient(), java.net.URI.create(baseUrl),
                java.time.Duration.ofSeconds(30), internalToken);
    }

    @Bean
    AppliedEditionMovementPort appliedEditionMovementPort() {
        return (ruleSetId, appliedEdition) -> 30;
    }

    @Bean
    CombatMapController combatMapController(
            CombatMapViewService mapViewService, CombatMapMovementService movementService, ApiRequestGuard requestGuard,
            MapImageEvidencePort mapImageEvidence) {
        return new CombatMapController(mapViewService, movementService, requestGuard, mapImageEvidence);
    }
}
