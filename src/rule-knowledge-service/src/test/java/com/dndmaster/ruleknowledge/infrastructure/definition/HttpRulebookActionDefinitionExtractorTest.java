package com.dndmaster.ruleknowledge.infrastructure.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.definition.RulebookActionEvidence;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionAssignment;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionCandidate;
import com.dndmaster.ruleknowledge.application.definition.RulebookActionQueryCoverage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpRulebookActionDefinitionExtractorTest {
    private static final UUID DOCUMENT = UUID.fromString("89ef62fc-b2dd-4b1b-91c9-04810d880f3a");
    private static final UUID EVIDENCE = UUID.fromString("82ad2366-a013-4d13-b922-9fa2c5970634");
    private static final String QUOTE = "Fire Bolt has range 120 feet.";

    @Test
    void preservesExplicitNullAndUsesOnlyTheCitedInputEvidence() throws Exception {
        HttpServer server = server("{\"definitions\":[{\"id\":\"fire\",\"name\":\"Fire Bolt\",\"actionKind\":\"SPELL\","
                + "\"sourceAnchorId\":\"" + anchorId() + "\",\"unsupportedFields\":[\"range\"],"
                + "\"fields\":{\"range\":null}}]}", null);
        try {
            var extractor = extractor(server);
            var definition = extractor.extract(DOCUMENT, "ev-one", evidence(), coverage(), UUID.randomUUID()).getFirst();
            assertEquals(EVIDENCE.toString(), definition.sourceChunkId());
            assertTrue(definition.fields().containsKey("range"));
            assertNull(definition.fields().get("range"));
            assertTrue(new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(definition)).path("fields").path("range").isNull());
        } finally { server.stop(0); }
    }

    @Test
    void sendsBoundedTypedEvidenceAndStrictDynamicOutputSchema() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server("{\"definitions\":[]}", body);
        try {
            var extractor = extractor(server);
            UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000001");
            assertTrue(extractor.extract(DOCUMENT, "ev-one", evidence(), coverage(), actor).isEmpty());
            var request = new ObjectMapper().readTree(body.get());
            assertEquals(actor.toString(), request.path("soloPlayerId").asText());
            assertEquals(DOCUMENT.toString(), request.path("extractionInput").path("documentId").asText());
            assertEquals("ev-one", request.path("extractionInput").path("extractionVersion").asText());
            assertEquals(1, request.path("extractionInput").path("evidence").size());
            assertEquals("^[0-9]{1,6}$", request.path("outputSchema").path("properties").path("definitions")
                    .path("items").path("properties").path("sourceAnchorId").path("pattern").asText());
            assertTrue(request.path("outputSchema").path("additionalProperties").booleanValue() == false);
            assertTrue(request.path("operationKey").asText().contains("source-anchor-v4"));
            assertEquals(QUOTE, request.path("extractionInput").path("evidence").get(0).path("anchors").get(0).path("text").asText());
        } finally { server.stop(0); }
    }

    @Test
    void rejectsAQuoteThatDoesNotAppearInTheCitedEvidence() throws Exception {
        HttpServer server = server("{\"definitions\":[{\"id\":\"fire\",\"name\":\"Fire Bolt\",\"actionKind\":\"SPELL\","
                + "\"sourceAnchorId\":\"invented-anchor\",\"unsupportedFields\":[],\"fields\":{}}]}", null);
        try {
            assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> extractor(server).extract(DOCUMENT, "ev-one", evidence(), coverage(), UUID.randomUUID()))
                    .getMessage().contains("evidence validation"));
        } finally { server.stop(0); }
    }

    @Test
    void discoveryReturnsNamesGroundedInListedSourceAnchors() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server("{\"candidates\":[{\"name\":\"Fire Bolt\",\"actionKind\":\"SPELL\",\"sourceAnchorId\":\"" + anchorId() + "\"}]}", body);
        try {
            var candidates = extractor(server).discover(DOCUMENT, "ev-one", evidence(), coverage(), UUID.randomUUID());
            assertEquals(1, candidates.size());
            assertEquals("Fire Bolt", candidates.getFirst().name());
            assertEquals(EVIDENCE.toString(), candidates.getFirst().sourceChunkId());
            assertEquals(QUOTE, candidates.getFirst().sourceEvidence());
            var request = new ObjectMapper().readTree(body.get());
            assertEquals("DISCOVERY", request.path("outputSchema").path("x-stage").asText());
            assertEquals(List.of("name", "actionKind", "sourceAnchorId"),
                    new ObjectMapper().convertValue(request.path("outputSchema").path("properties").path("candidates").path("items").path("required"),
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            assertEquals("^[0-9]{1,6}$", request.path("outputSchema").path("properties").path("candidates").path("items")
                    .path("properties").path("sourceAnchorId").path("pattern").asText());
            assertEquals(List.of(anchorId()), new ObjectMapper().convertValue(request.path("outputSchema").path("properties").path("candidates")
                    .path("items").path("properties").path("sourceAnchorId").path("enum"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
        } finally { server.stop(0); }
    }

    @Test
    void assignedSchemaUsesCodexCompatibleAnyOfAndEnumBranchesForCandidateIdentity() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        String response = "{\"candidateResults\":[{\"candidateId\":\"candidate-a\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing details\",\"neededFields\":[],\"sharedRules\":[],\"sharedRuleApplicability\":[]}],\"definitions\":[]}";
        HttpServer server = server(response, body);
        try {
            var candidate = new RulebookActionCandidate("candidate-a", "Fire Bolt", "SPELL", "page=1:section=Spells",
                    EVIDENCE.toString(), "Fire Bolt has range.");
            var assignment = new RulebookActionAssignment(candidate, List.of(EVIDENCE.toString()), "fingerprint", 1);
            var extractor = extractor(server);
            var results = extractor.extractAssignedBatch(DOCUMENT, "ev-one", List.of(assignment), evidence(), coverage(), UUID.randomUUID());

            assertEquals("INSUFFICIENT_EVIDENCE", results.getFirst().status());
            var request = new ObjectMapper().readTree(body.get());
            assertTrue(request.path("operationKey").asText().contains("assigned-v4"));
            var schema = request.path("outputSchema");
            assertTrue(providerCompatibleSchema(schema));
            var items = schema.path("properties").path("definitions").path("items");
            assertTrue(items.path("oneOf").isMissingNode());
            assertTrue(items.findValue("const") == null);
            var branch = items.path("anyOf").get(0);
            assertEquals(List.of(anchorId()), new ObjectMapper().convertValue(branch.path("properties").path("sourceAnchorId").path("enum"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            assertEquals(0, schema.path("properties").path("candidateResults").path("items").path("properties").path("sharedRules").path("maxItems").asInt());
            assertEquals(List.of("candidate-a"), new ObjectMapper().convertValue(branch.path("properties").path("id").path("enum"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            assertEquals(List.of("Fire Bolt"), new ObjectMapper().convertValue(branch.path("properties").path("name").path("enum"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            assertEquals(List.of("SPELL"), new ObjectMapper().convertValue(branch.path("properties").path("actionKind").path("enum"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            assertEquals(new ObjectMapper().convertValue(items.path("required"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}),
                    new ObjectMapper().convertValue(branch.path("required"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            assertEquals(new java.util.HashSet<>(new ObjectMapper().convertValue(items.path("properties"),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}).keySet()),
                    new java.util.HashSet<>(new ObjectMapper().convertValue(branch.path("properties"),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}).keySet()));
        } finally { server.stop(0); }
    }

    @Test
    void sendsTheSameAssignedInputAndSchemaToTheEstimateOnlyEndpoint() throws Exception {
        var candidate = new RulebookActionCandidate("candidate-a", "Fire Bolt", "SPELL", "page=1:section=Spells",
                EVIDENCE.toString(), QUOTE);
        var assignment = new RulebookActionAssignment(candidate, List.of(EVIDENCE.toString()), "fingerprint", 1);
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server("{\"estimatedInputTokens\":321,\"tokenizer\":\"o200k_base\",\"includesProviderSystemAndToolOverhead\":false}", body);
        try {
            var extractor = extractor(server);
            long estimate = extractor.estimateAssignedBatchInputTokens(DOCUMENT, "ev-one", List.of(assignment),
                    evidence(), coverage(), UUID.randomUUID());
            var request = new ObjectMapper().readTree(body.get());

            assertEquals(321L, estimate);
            assertEquals(DOCUMENT.toString(), request.path("extractionInput").path("documentId").asText());
            assertEquals(QUOTE, request.path("extractionInput").path("evidence").get(0).path("anchors").get(0).path("text").asText());
            assertEquals("ASSIGNED", request.path("outputSchema").path("x-stage").asText());
            assertEquals(List.of("candidate-a"), new ObjectMapper().convertValue(request.path("outputSchema").path("properties")
                    .path("definitions").path("items").path("anyOf").get(0).path("properties").path("id").path("enum"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
        } finally { server.stop(0); }
    }

    @Test
    void rejectsMalformedOrMismatchedTokenEstimateMetadata() throws Exception {
        var candidate = new RulebookActionCandidate("candidate-a", "Fire Bolt", "SPELL", "page=1:section=Spells",
                EVIDENCE.toString(), QUOTE);
        var assignment = new RulebookActionAssignment(candidate, List.of(EVIDENCE.toString()), "fingerprint", 1);
        for (String response : List.of(
                "{\"estimatedInputTokens\":1.5,\"tokenizer\":\"o200k_base\",\"includesProviderSystemAndToolOverhead\":false}",
                "{\"estimatedInputTokens\":-1,\"tokenizer\":\"o200k_base\",\"includesProviderSystemAndToolOverhead\":false}",
                "{\"estimatedInputTokens\":10,\"tokenizer\":\"other\",\"includesProviderSystemAndToolOverhead\":false}",
                "{\"estimatedInputTokens\":10,\"tokenizer\":\"o200k_base\",\"includesProviderSystemAndToolOverhead\":true}")) {
            HttpServer server = server(response, null);
            try {
                var extractor = extractor(server);
                org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> extractor.estimateAssignedBatchInputTokens(DOCUMENT, "ev-one", List.of(assignment), evidence(),
                                coverage(), UUID.randomUUID()));
            } finally { server.stop(0); }
        }
    }

    @Test
    void appliesAndStoresRequestedSharedRuleEvenWhenOutputListsNoRemainingFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var response = mapper.createObjectNode();
        var outcome = response.putArray("candidateResults").addObject().put("candidateId", "candidate-a")
                .put("status", "COMPLETED").putNull("reason");
        outcome.putArray("neededFields");
        var rule = outcome.putArray("sharedRules").addObject().put("applicability", "APPLIES")
                .put("conditions", "When using a listed action").put("sourceAnchorId", anchorId());
        rule.putObject("fields").put("cost", "one action");
        outcome.putArray("sharedRuleApplicability");
        var item = response.putArray("definitions").addObject().put("id", "candidate-a").put("name", "Fire Bolt")
                .put("actionKind", "SPELL").put("sourceAnchorId", anchorId());
        item.putArray("unsupportedFields");
        var fields = item.putObject("fields");
        for (String name : List.of("level", "castingTime", "cost", "range", "rangeForm", "components", "duration", "school",
                "conditions", "attackSave", "damageEffect", "effect", "targetMode", "originMode", "effectShape", "pathBlocking",
                "propagation", "verticalDirection", "normalRangeFeet", "longRangeFeet", "lengthFeet", "widthFeet", "heightFeet", "radiusFeet"))
            fields.putNull(name);
        HttpServer server = server(mapper.writeValueAsString(response), null);
        try {
            var candidate = new RulebookActionCandidate("candidate-a", "Fire Bolt", "SPELL", "page=1:section=Spells",
                    EVIDENCE.toString(), QUOTE);
            var assignment = new RulebookActionAssignment(candidate, List.of(EVIDENCE.toString()), "fingerprint", 1,
                    java.util.Map.of(), List.of("cost"));

            var result = extractor(server).extractAssignedBatch(DOCUMENT, "ev-one", List.of(assignment), evidence(), coverage(), UUID.randomUUID()).getFirst();

            assertEquals("one action", result.definition().fields().get("cost"));
            assertEquals(1, result.sharedRules().size());
            assertEquals(result.sharedRules().getFirst().id(), result.definition().sharedRuleRefs().getFirst());
        } finally { server.stop(0); }
    }

    @Test
    void assignedSchemaValidatesCompletedAndNonCompletedResultsWithTheRuntimeValidator() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        String response = "{\"candidateResults\":[{\"candidateId\":\"candidate-a\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing details\"}],\"definitions\":[]}";
        HttpServer server = server(response, body);
        try {
            var candidate = new RulebookActionCandidate("candidate-a", "Fire Bolt", "SPELL", "page=1:section=Spells",
                    EVIDENCE.toString(), "Fire Bolt has range.");
            var assignment = new RulebookActionAssignment(candidate, List.of(EVIDENCE.toString()), "fingerprint", 1);
            extractor(server).extractAssignedBatch(DOCUMENT, "ev-one", List.of(assignment), evidence(), coverage(), UUID.randomUUID());
            ObjectMapper mapper = new ObjectMapper();
            var schemaNode = mapper.readTree(body.get()).path("outputSchema");
            var schema = com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_2020_12).getSchema(schemaNode);
            var completed = mapper.createObjectNode();
            var completedOutcome = completed.putArray("candidateResults").addObject().put("candidateId", "candidate-a").put("status", "COMPLETED").putNull("reason");
            completedOutcome.putArray("neededFields"); completedOutcome.putArray("sharedRules"); completedOutcome.putArray("sharedRuleApplicability");
            var definition = completed.putArray("definitions").addObject().put("id", "candidate-a").put("name", "Fire Bolt")
                    .put("actionKind", "SPELL").put("sourceAnchorId", anchorId());
            definition.putArray("unsupportedFields");
            var fields = definition.putObject("fields");
            schemaNode.path("properties").path("definitions").path("items").path("properties").path("fields").path("properties")
                    .fieldNames().forEachRemaining(name -> fields.putNull(name));
            assertTrue(schema.validate(completed).isEmpty());

            var insufficient = mapper.readTree("{\"candidateResults\":[{\"candidateId\":\"candidate-a\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"reason\":\"missing details\",\"neededFields\":[],\"sharedRules\":[],\"sharedRuleApplicability\":[]}],\"definitions\":[]}");
            var failed = mapper.readTree("{\"candidateResults\":[{\"candidateId\":\"candidate-a\",\"status\":\"FAILED\",\"reason\":\"unprocessable\",\"neededFields\":[],\"sharedRules\":[],\"sharedRuleApplicability\":[]}],\"definitions\":[]}");
            var mismatchedId = completed.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) mismatchedId.path("definitions").get(0)).put("name", "Other Spell");
            assertTrue(schema.validate(insufficient).isEmpty());
            assertTrue(schema.validate(failed).isEmpty());
            assertTrue(!schema.validate(mismatchedId).isEmpty());
        } finally { server.stop(0); }
    }

    private static HttpServer server(String response, AtomicReference<String> body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/gm/rulebook-action-definitions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body != null) body.set(request);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); return server;
    }
    private static HttpRulebookActionDefinitionExtractor extractor(HttpServer server) {
        return new HttpRulebookActionDefinitionExtractor(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                "internal-token", null, "ollama", "exaone3.5:7.8b", "medium", Duration.ofSeconds(5), new ObjectMapper());
    }
    private static List<RulebookActionEvidence> evidence() { return List.of(new RulebookActionEvidence(EVIDENCE.toString(), "page=1:section=Spells", QUOTE, 1)); }
    private static String anchorId() { return "0"; }
    private static List<RulebookActionQueryCoverage> coverage() { return List.of(new RulebookActionQueryCoverage("spells", "abc123", 1)); }

    private static boolean providerCompatibleSchema(com.fasterxml.jackson.databind.JsonNode schema) {
        if (schema.path("enum").isArray() && schema.path("enum").isEmpty()) return false;
        if ("object".equals(schema.path("type").asText())) {
            var properties = schema.path("properties");
            java.util.Set<String> propertyNames = new java.util.HashSet<>();
            if (properties.isObject()) properties.fieldNames().forEachRemaining(propertyNames::add);
            java.util.Set<String> required = new java.util.HashSet<>();
            schema.path("required").forEach(value -> { if (value.isTextual()) required.add(value.asText()); });
            if (!properties.isObject() || !schema.path("additionalProperties").isBoolean()
                    || schema.path("additionalProperties").booleanValue() || !propertyNames.equals(required)) return false;
        }
        var children = schema.elements();
        while (children.hasNext()) if (!providerCompatibleSchema(children.next())) return false;
        return true;
    }
}
