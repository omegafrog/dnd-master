package com.dndmaster.adventure.application.scenario.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SeededOpeningSituationMapGmPortTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID mapId = UUID.randomUUID();

    @Test
    void seedsOpeningSituationWithAnAvailableMapAndRecordsOptInDiagnostics() {
        Logger logger = (Logger) LoggerFactory.getLogger(SeededOpeningSituationMapGmPort.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            JsonNode result = new SeededOpeningSituationMapGmPort(request -> json("""
                    {"openingSituationIndex":1,"situations":[{"mapNeeded":false,"mapDefinitionId":null},
                    {"mapNeeded":false,"mapDefinitionId":null}]}
                    """)).generate(request("initial-situation:adventure:package:stage:first-stage"));

            assertEquals(true, result.path("situations").get(1).path("mapNeeded").asBoolean());
            assertEquals(mapId.toString(), result.path("situations").get(1).path("mapDefinitionId").asText());
            assertEquals(false, result.path("situations").get(0).path("mapNeeded").asBoolean());
            String log = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            org.junit.jupiter.api.Assertions.assertTrue(log.contains("operation=seed_opening_situation_map outcome=started"));
            org.junit.jupiter.api.Assertions.assertTrue(log.contains("expected=opening_map_required"));
            org.junit.jupiter.api.Assertions.assertTrue(log.contains("actualMapDefinitionId=" + mapId));
            org.junit.jupiter.api.Assertions.assertTrue(log.contains("elapsedMs="));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
    }

    @Test
    void rejectsOpeningMapSeedWhenNoMapWasProvided() {
        Logger logger = (Logger) LoggerFactory.getLogger(SeededOpeningSituationMapGmPort.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            assertThrows(IllegalStateException.class, () -> new SeededOpeningSituationMapGmPort(request -> json(
                    "{\"openingSituationIndex\":0,\"situations\":[{}]}")).generate(request("key:stage:first-stage", false)));
            String log = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            org.junit.jupiter.api.Assertions.assertTrue(log.contains("outcome=started"));
            org.junit.jupiter.api.Assertions.assertTrue(log.contains("expected=opening_situation_and_map_id"));
            org.junit.jupiter.api.Assertions.assertTrue(log.contains("actual=invalid_provider_response"));
            org.junit.jupiter.api.Assertions.assertTrue(log.contains("failureClass=IllegalStateException"));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
    }

    @Test
    void leavesNonOpeningPreparationResponsesUntouched() throws Exception {
        JsonNode original = mapper.readTree("{\"stages\":[]}");
        JsonNode result = new SeededOpeningSituationMapGmPort(request -> original)
                .generate(request("initial-situation:adventure:package:backbone"));
        org.junit.jupiter.api.Assertions.assertSame(original, result);
    }

    private InitialSituationGmPort.Request request(String operationKey) {
        return request(operationKey, true);
    }

    private InitialSituationGmPort.Request request(String operationKey, boolean mapAvailable) {
        var schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putArray("required");
        var properties = schema.putObject("properties");
        properties.putObject("openingSituationIndex").put("type", "integer");
        var situationSchema = properties.putObject("situations").putObject("items").putObject("properties");
        situationSchema.putObject("mapDefinitionId").putArray("enum").add(mapAvailable ? mapId.toString() : (String) null).add((String) null);
        return new InitialSituationGmPort.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "fixture",
                "fixture", "low", operationKey, "safe fixture prompt", schema);
    }

    private JsonNode json(String value) {
        try { return mapper.readTree(value); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException(failure); }
    }
}
