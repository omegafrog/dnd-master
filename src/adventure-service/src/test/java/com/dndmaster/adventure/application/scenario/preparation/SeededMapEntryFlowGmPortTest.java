package com.dndmaster.adventure.application.scenario.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

class SeededMapEntryFlowGmPortTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID mapId = UUID.randomUUID();

    @Test
    void pinsOneMaplessOpeningThenOneMapRequiredDestination() {
        Logger logger = (Logger) LoggerFactory.getLogger(SeededMapEntryFlowGmPort.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            JsonNode result = new SeededMapEntryFlowGmPort(request -> json("""
                    {"situations":[
                      {"mapNeeded":true,"mapDefinitionId":null},
                      {"mapNeeded":false,"mapDefinitionId":null},
                      {"mapNeeded":false,"mapDefinitionId":null}]}
                    """)).generate(request("initial-situation:adventure:package:stage:first-stage"));

            assertEquals(false, result.path("situations").get(0).path("mapNeeded").asBoolean());
            assertEquals(true, result.path("situations").get(1).path("mapNeeded").asBoolean());
            assertEquals(mapId.toString(), result.path("situations").get(1).path("mapDefinitionId").asText());
            assertEquals(false, result.path("situations").get(2).path("mapNeeded").asBoolean());
            org.junit.jupiter.api.Assertions.assertFalse(result.toString().contains("Index"));
            String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            org.junit.jupiter.api.Assertions.assertTrue(logs.contains("operation=seed_map_entry_flow outcome=started"));
            org.junit.jupiter.api.Assertions.assertTrue(logs.contains("expected=opening_mapless_destination_map_required"));
            org.junit.jupiter.api.Assertions.assertTrue(logs.contains("actualMapDefinitionId=" + mapId));
            org.junit.jupiter.api.Assertions.assertTrue(logs.contains("elapsedMs="));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
    }

    @Test
    void rejectsMapEntryFixtureWithoutDestinationOrAvailableMap() {
        Logger logger = (Logger) LoggerFactory.getLogger(SeededMapEntryFlowGmPort.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            assertThrows(IllegalStateException.class, () -> new SeededMapEntryFlowGmPort(request -> json(
                    "{\"situations\":[{}]}"))
                    .generate(request("initial-situation:adventure:package:stage:first-stage")));
            String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            org.junit.jupiter.api.Assertions.assertTrue(logs.contains("expectedSituationCountAtLeast=2 actualSituationCount=1"));
            org.junit.jupiter.api.Assertions.assertTrue(logs.contains("failure=invalid_provider_response"));
            org.junit.jupiter.api.Assertions.assertTrue(logs.contains("failureClass=IllegalStateException"));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
    }

    @Test
    void leavesOtherStagePreparationUntouched() {
        JsonNode original = json("{\"stages\":[]}");
        JsonNode result = new SeededMapEntryFlowGmPort(request -> original)
                .generate(request("initial-situation:adventure:package:backbone"));
        org.junit.jupiter.api.Assertions.assertSame(original, result);
    }

    private InitialSituationGmPort.Request request(String operationKey) {
        var schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putArray("required");
        var properties = schema.putObject("properties");
        properties.putObject("situations").putObject("items").putObject("properties")
                .putObject("mapDefinitionId").putArray("enum").add(mapId.toString()).addNull();
        return new InitialSituationGmPort.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "fixture",
                "fixture", "low", operationKey, "fixture", schema, false, List.of(), true, 1);
    }

    private JsonNode json(String value) {
        try { return mapper.readTree(value); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException(failure); }
    }
}
