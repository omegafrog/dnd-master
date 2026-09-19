package com.dndmaster.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiSchemaTest {
    private static final Path CONTRACTS = contractsRoot();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void canonical_openapi_documents_contain_every_allowlisted_public_and_internal_path() throws IOException {
        assertPaths("identity-access", "/api/v1/identity/sessions", "/internal/v1/auth/introspections");
        assertPaths("adventure", "/api/v1/adventures/{adventureId}/turns", "/api/v1/adventures/{adventureId}/rule-inquiries",
                "/internal/v1/adventures", "/internal/v1/adventures/{adventureId}/edition",
                "/internal/v1/adventures/{adventureId}/roll-conditions",
                "/internal/v1/adventures/{adventureId}/movement-validations",
                "/api/v1/adventures/{adventureId}/map-movement/pending",
                "/api/v1/adventures/{adventureId}/combat-map/spatial/observe",
                "/api/v1/adventures/{adventureId}/combat-map/spatial/interact",
                "/api/v1/adventures/{adventureId}/combat-map/spatial/combat-turn-start",
                "/api/v1/adventures/{adventureId}/combat-map/spatial/advance-durations",
                "/api/v1/adventures/{adventureId}/combat-map/movement-operations/{operationId}",
                "/api/v1/adventures/{adventureId}/combat-map/movement-operations/{operationId}/roll");
        assertResumeTurnIdempotencyContract();
        assertCombatMapTriggerQualification();
        assertMovementContracts();
        assertPaths("rule-knowledge", "/api/v1/rulebooks", "/api/v1/rulebooks/{rulebookId}/source-preview", "/api/v1/rulebooks/rule-set", "/internal/v1/rulebooks",
                "/internal/v1/rulebook-indexes", "/internal/v1/rulebooks/{rulebookId}/ownership",
                "/internal/v1/rule-evidence/search");
        assertPaths("character-management", "/internal/v1/character-sheets/{sheetId}");
        assertPaths("dice-roll", "/internal/v1/dice-rolls/player", "/internal/v1/dice-rolls/ai");
        assertDiceRollSecurityContract();
        assertPaths("combat-map", "/internal/v1/combat-maps/{mapId}/player-view",
                "/internal/v1/combat-maps/{mapId}/moves", "/internal/v1/combat-maps/{mapId}/ai-state",
                "/internal/v1/combat-maps/{mapId}/spatial/observe",
                "/internal/v1/combat-maps/{mapId}/spatial/interact",
                "/internal/v1/combat-maps/{mapId}/spatial/combat-turn-start",
                "/internal/v1/combat-maps/{mapId}/spatial/advance-durations");
        assertPaths("ai-game-master", "/internal/v1/gm/scenes", "/internal/v1/gm/judgments",
                "/internal/v1/gm/rule-answers", "/internal/v1/gm/maps", "/internal/v1/gm/intent-classifications");
    }

    @SuppressWarnings("unchecked")
    private static void assertResumeTurnIdempotencyContract() throws IOException {
        Map<String, Object> root = new Yaml().load(Files.readString(CONTRACTS.resolve("adventure/openapi.yaml")));
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>) paths
                .get("/api/v1/adventures/{adventureId}/turns/{pendingTurnId}/resume")).get("post");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertTrue(parameters.stream().anyMatch(parameter -> "Idempotency-Key".equals(parameter.get("name"))
                && "header".equals(parameter.get("in")) && Boolean.TRUE.equals(parameter.get("required"))));
    }

    @SuppressWarnings("unchecked")
    private static void assertDiceRollSecurityContract() throws IOException {
        Map<String, Object> root = new Yaml().load(Files.readString(CONTRACTS.resolve("dice-roll/openapi.yaml")));
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        Map<String, Object> player = (Map<String, Object>) ((Map<String, Object>) paths.get("/internal/v1/dice-rolls/player")).get("post");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) player.get("parameters");
        assertTrue(parameters.stream().anyMatch(parameter -> "X-Internal-Token".equals(parameter.get("name"))
                && "header".equals(parameter.get("in")) && Boolean.TRUE.equals(parameter.get("required"))));
        assertTrue(parameters.stream().anyMatch(parameter -> "Idempotency-Key".equals(parameter.get("name"))
                && "header".equals(parameter.get("in")) && Boolean.TRUE.equals(parameter.get("required"))));
    }

    @SuppressWarnings("unchecked")
    private static void assertMovementContracts() throws IOException {
        Map<String, Object> combatMap = new Yaml().load(Files.readString(CONTRACTS.resolve("combat-map").resolve("openapi.yaml")));
        Map<String, Object> paths = (Map<String, Object>) combatMap.get("paths");
        for (String path : List.of("/internal/v1/combat-maps/{mapId}/spatial/observe",
                "/internal/v1/combat-maps/{mapId}/spatial/interact",
                "/internal/v1/combat-maps/{mapId}/spatial/combat-turn-start",
                "/internal/v1/combat-maps/{mapId}/spatial/advance-durations")) {
            Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get("post");
            List<Map<String, Object>> spatialParameters = (List<Map<String, Object>>) operation.get("parameters");
            assertTrue(spatialParameters.stream().anyMatch(parameter -> "Idempotency-Key".equals(parameter.get("name"))
                    && "header".equals(parameter.get("in")) && Boolean.TRUE.equals(parameter.get("required"))),
                    path + " must require Idempotency-Key");
        }
        Map<String, Object> legacyMove = (Map<String, Object>) ((Map<String, Object>) paths.get("/internal/v1/combat-maps/{mapId}/moves")).get("post");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) legacyMove.get("parameters");
        assertTrue(parameters.stream().anyMatch(parameter -> "Idempotency-Key".equals(parameter.get("name"))
                && Boolean.TRUE.equals(parameter.get("required"))),
                "legacy move contract must require Idempotency-Key");
        Map<String, Object> internalCancel = (Map<String, Object>) ((Map<String, Object>) paths
                .get("/internal/v1/combat-maps/{mapId}/movement-operations/{operationId}")).get("delete");
        assertRequiredHeader(internalCancel, "internal cancel");
        assertTrue(internalCancel.toString().contains("Distinct cancel command identity"));
        assertRequiredJsonBody(internalCancel, "MovementCancelRequest", "internal cancel");

        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) combatMap.get("components")).get("schemas");
        Map<String, Object> operation = (Map<String, Object>) schemas.get("MovementOperationResponse");
        Map<String, Object> properties = (Map<String, Object>) operation.get("properties");
        assertNullableFinalPosition(properties, "staged movement");
        assertTrue(properties.containsKey("pendingCheck"), "staged movement must expose a safe pending check projection");
        Map<String, Object> submission = (Map<String, Object>) schemas.get("MovementCheckResultSubmission");
        assertEquals(List.of("commandId", "operationId", "checkId", "success", "ownerPlayerId", "actor"), submission.get("required"));

        Map<String, Object> adventure = new Yaml().load(Files.readString(CONTRACTS.resolve("adventure").resolve("openapi.yaml")));
        Map<String, Object> adventurePaths = (Map<String, Object>) adventure.get("paths");
        for (String path : List.of("/api/v1/adventures/{adventureId}/combat-map/spatial/observe",
                "/api/v1/adventures/{adventureId}/combat-map/spatial/interact",
                "/api/v1/adventures/{adventureId}/combat-map/spatial/combat-turn-start",
                "/api/v1/adventures/{adventureId}/combat-map/spatial/advance-durations")) {
            Map<String, Object> spatialOperation = (Map<String, Object>) ((Map<String, Object>) adventurePaths.get(path)).get("post");
            List<Map<String, Object>> spatialParameters = (List<Map<String, Object>>) spatialOperation.get("parameters");
            assertTrue(spatialParameters.stream().anyMatch(parameter -> "Idempotency-Key".equals(parameter.get("name"))
                    && "header".equals(parameter.get("in")) && Boolean.TRUE.equals(parameter.get("required"))),
                    path + " must require Idempotency-Key");
        }
        Map<String, Object> adventureCancel = (Map<String, Object>) ((Map<String, Object>) adventurePaths
                .get("/api/v1/adventures/{adventureId}/combat-map/movement-operations/{operationId}")).get("delete");
        assertRequiredHeader(adventureCancel, "adventure cancel");
        assertTrue(adventureCancel.toString().contains("Distinct cancel command identity"));
        Map<String, Object> adventureSchemas = (Map<String, Object>) ((Map<String, Object>) adventure.get("components")).get("schemas");
        Map<String, Object> followUp = (Map<String, Object>) adventureSchemas.get("MovementFollowUpCommand");
        Map<String, Object> followUpProperties = (Map<String, Object>) followUp.get("properties");
        assertEquals(List.of("COMBAT"), ((Map<String, Object>) followUpProperties.get("kind")).get("enum"));
        assertEquals(List.of("HOSTILE_OBSERVED"), ((Map<String, Object>) followUpProperties.get("trigger")).get("enum"));
        assertNullableFinalPosition(schemaProperties(adventureSchemas, "MovementResult"), "Adventure movement result");
        assertNullableFinalPosition(schemaProperties(adventureSchemas, "AdventureMovementOperationResponse"),
                "Adventure movement operation response");
        assertNullableProperty(schemaProperties(adventureSchemas, "RuntimeTurnResponse"), "movementResult",
                "Runtime turn response movement result");
        assertTrue(schemaProperties(adventureSchemas, "AdventureMovementOperationResponse").containsKey("pendingCheck"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperties(Map<String, Object> schemas, String schemaName) {
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        return (Map<String, Object>) schema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static void assertRequiredHeader(Map<String, Object> operation, String contractName) {
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertTrue(parameters.stream().anyMatch(parameter -> "Idempotency-Key".equals(parameter.get("name"))
                && "header".equals(parameter.get("in")) && Boolean.TRUE.equals(parameter.get("required"))),
                contractName + " must require Idempotency-Key");
    }

    @SuppressWarnings("unchecked")
    private static void assertRequiredJsonBody(Map<String, Object> operation, String schemaName, String contractName) {
        Map<String, Object> body = (Map<String, Object>) operation.get("requestBody");
        assertTrue(Boolean.TRUE.equals(body.get("required")), contractName + " must require a request body");
        Map<String, Object> content = (Map<String, Object>) body.get("content");
        Map<String, Object> json = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schema = (Map<String, Object>) json.get("schema");
        assertEquals("#/components/schemas/" + schemaName, schema.get("$ref"), contractName + " body schema");
    }

    private static void assertNullableFinalPosition(Map<String, Object> properties, String contractName) {
        Map<String, Object> finalPosition = (Map<String, Object>) properties.get("finalPosition");
        assertTrue(finalPosition.containsKey("oneOf") || finalPosition.containsKey("nullable"),
                contractName + " finalPosition must allow null before a terminal result");
    }

    @SuppressWarnings("unchecked")
    private static void assertNullableProperty(Map<String, Object> properties, String propertyName, String contractName) {
        Map<String, Object> property = (Map<String, Object>) properties.get(propertyName);
        boolean oneOfNull = property.get("oneOf") instanceof List<?> variants
                && variants.stream().anyMatch(variant -> variant instanceof Map<?, ?> schema
                        && "null".equals(schema.get("type")));
        assertTrue(oneOfNull || Boolean.TRUE.equals(property.get("nullable"))
                        || (property.get("type") instanceof List<?> types && types.contains("null")),
                contractName + " must allow null");
    }

    @SuppressWarnings("unchecked")
    private static void assertCombatMapTriggerQualification() throws IOException {
        Map<String, Object> root = new Yaml().load(Files.readString(CONTRACTS.resolve("combat-map").resolve("openapi.yaml")));
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>) paths.get("/internal/v1/combat-maps/{mapId}/tactical-triggers")).get("post");
        String body = operation.toString();
        assertTrue(body.contains("qualifyingAction"), "combat-map trigger contract must require qualifyingAction");
        assertTrue(body.contains("400"), "combat-map trigger contract must document validation failure");
        assertTrue(body.contains("invalid tactical trigger kind"), "combat-map trigger contract must document kind validation");
        assertTrue(body.contains("pattern=\\S") || body.contains("pattern=\\\\S"), "combat-map trigger schema must reject whitespace-only actions");
    }

    @Test
    void multipart_async_stream_source_candidate_and_player_map_have_separate_valid_schemas() throws IOException {
        JsonNode multipart = schema("rule-knowledge/schemas/multipart-upload.json");
        JsonNode batchMultipart = schema("rule-knowledge/schemas/batch-multipart-upload.json");
        JsonNode batchResponse = schema("rule-knowledge/schemas/batch-upload-response.json");
        JsonNode async = schema("rule-knowledge/schemas/async-status.json");
        JsonNode source = schema("rule-knowledge/schemas/source-location.json");
        JsonNode preview = schema("rule-knowledge/schemas/source-preview-response.json");
        JsonNode stream = schema("adventure/schemas/stream-event.json");
        JsonNode candidate = schema("adventure/schemas/candidate-rule.json");
        JsonNode playerMap = schema("combat-map/schemas/player-map-view.json");
        JsonNode evidenceSearch = schema("rule-knowledge/schemas/evidence-search-request.json");

        assertTrue(multipart.at("/required").toString().contains("file"));
        assertTrue(batchMultipart.at("/required").toString().contains("documents"));
        assertTrue(batchMultipart.at("/properties/documents/items/properties/documentType/enum").toString().contains("STORYBOOK"));
        assertTrue(batchResponse.at("/properties/documents/items/properties/status/enum").toString().contains("VALIDATION_FAILED"));
        assertTrue(async.at("/properties/status/enum").toString().contains("PARTIAL"));
        assertEquals(2, source.at("/required").size());
        assertTrue(preview.at("/properties/spans/items/properties/locator/minLength").asInt() >= 1);
        assertTrue(stream.at("/properties/type/enum").toString().contains("INTERRUPTED"));
        assertEquals(1, candidate.at("/properties/sources/minItems").asInt());
        assertEquals("PLAYER_VISIBLE", playerMap.at("/properties/layers/items/properties/visibility/const").asText());
        assertFalse(playerMap.toString().contains("AI_ONLY"));
        assertTrue(playerMap.at("/required").toString().contains("spatialFeatures"));
        assertTrue(playerMap.at("/properties/spatialFeatures/items/required").toString().contains("interactable"));
        assertFalse(playerMap.at("/properties/spatialFeatures").toString().contains("difficulty"));
        assertFalse(playerMap.at("/properties/spatialFeatures").toString().contains("ruleReference"));
        assertFalse(playerMap.at("/properties/spatialFeatures").toString().contains("payload"));
        assertTrue(evidenceSearch.at("/required").toString().contains("queryIntent"));
    }

    @Test
    void combat_map_internal_routes_require_shared_internal_token() throws IOException {
        Map<String, Object> root = new Yaml().load(Files.readString(CONTRACTS.resolve("combat-map/openapi.yaml")));
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            if (!entry.getKey().startsWith("/internal/v1/")) continue;
            Map<String, Object> operations = (Map<String, Object>) entry.getValue();
            for (Object operation : operations.values()) {
                if (!(operation instanceof Map<?, ?> operationMap)) continue;
                List<Map<String, Object>> parameters = (List<Map<String, Object>>) operationMap.get("parameters");
                assertTrue(parameters != null && parameters.stream().anyMatch(parameter ->
                        "X-Internal-Token".equals(parameter.get("name"))
                                && "header".equals(parameter.get("in"))
                                && Boolean.TRUE.equals(parameter.get("required"))),
                        entry.getKey() + " must require X-Internal-Token");
            }
        }
    }

    @Test
    void combat_map_player_view_requires_owner_scope() throws IOException {
        Map<String, Object> root = new Yaml().load(Files.readString(CONTRACTS.resolve("combat-map/openapi.yaml")));
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>) paths.get("/internal/v1/combat-maps/{mapId}/player-view")).get("get");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertTrue(parameters.stream().anyMatch(parameter ->
                "ownerId".equals(parameter.get("name")) && "query".equals(parameter.get("in"))
                        && Boolean.TRUE.equals(parameter.get("required"))));
    }

    @SuppressWarnings("unchecked")
    @Test
    void combat_map_internal_movement_contract_keeps_enemy_check_details_out_of_player_pending_check() throws IOException {
        Map<String, Object> root = new Yaml().load(Files.readString(CONTRACTS.resolve("combat-map/openapi.yaml")));
        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) root.get("components")).get("schemas");
        Map<String, Object> pending = (Map<String, Object>) schemas.get("MovementCheckPending");
        Map<String, Object> details = (Map<String, Object>) schemas.get("MovementCheckDetails");
        Map<String, Object> pendingActor = (Map<String, Object>) ((Map<String, Object>) pending.get("properties")).get("actor");
        Map<String, Object> detailsActor = (Map<String, Object>) ((Map<String, Object>) details.get("properties")).get("actor");
        assertEquals(List.of("PLAYER"), pendingActor.get("enum"));
        assertEquals(List.of("PLAYER", "ENEMY"), detailsActor.get("enum"));
        assertTrue(details.get("description").toString().contains("Internal-only"));
    }

    @SuppressWarnings("unchecked")
    private static void assertPaths(String provider, String... expected) throws IOException {
        Path document = CONTRACTS.resolve(provider).resolve("openapi.yaml");
        Map<String, Object> root = new Yaml().load(Files.readString(document));
        assertEquals("3.1.0", root.get("openapi"));
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        for (String path : expected) assertTrue(paths.containsKey(path), provider + " is missing " + path);
        assertTrue(paths.keySet().stream().allMatch(path -> path.startsWith("/api/v1/") || path.startsWith("/internal/v1/")));
    }

    private static JsonNode schema(String relative) throws IOException {
        Path path = CONTRACTS.resolve(relative);
        assertTrue(Files.isRegularFile(path));
        return JSON.readTree(path.toFile());
    }

    private static Path contractsRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path direct = workingDirectory.resolve("contracts");
        if (Files.isDirectory(direct)) return direct;
        Path moduleSibling = workingDirectory.resolveSibling("contracts");
        return Files.isDirectory(moduleSibling) ? moduleSibling : workingDirectory.resolve("dnd-master").resolve("contracts");
    }
}
