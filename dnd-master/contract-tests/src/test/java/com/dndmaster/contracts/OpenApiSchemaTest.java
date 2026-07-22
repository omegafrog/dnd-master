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
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiSchemaTest {
    private static final Path CONTRACTS = contractsRoot();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void canonical_openapi_documents_contain_every_allowlisted_public_and_internal_path() throws IOException {
        assertPaths("identity-access", "/api/v1/identity/sessions", "/internal/v1/auth/introspections");
        assertPaths("adventure", "/api/v1/adventures/{adventureId}/messages", "/api/v1/adventures/{adventureId}/rule-inquiries",
                "/internal/v1/adventures", "/internal/v1/adventures/{adventureId}/edition",
                "/internal/v1/adventures/{adventureId}/roll-conditions",
                "/internal/v1/adventures/{adventureId}/movement-validations",
                "/internal/v1/adventures/{adventureId}/gm-context");
        assertPaths("rule-knowledge", "/api/v1/rulebooks", "/internal/v1/rulebooks",
                "/internal/v1/rulebook-indexes", "/internal/v1/rulebooks/{rulebookId}/ownership",
                "/internal/v1/rule-evidence/search");
        assertPaths("character-management", "/internal/v1/character-sheets/{sheetId}");
        assertPaths("dice-roll", "/internal/v1/dice-rolls/player", "/internal/v1/dice-rolls/ai");
        assertPaths("combat-map", "/internal/v1/combat-maps/{mapId}/player-view",
                "/internal/v1/combat-maps/{mapId}/moves", "/internal/v1/combat-maps/{mapId}/ai-state");
        assertPaths("ai-game-master", "/internal/v1/gm/scenes", "/internal/v1/gm/judgments",
                "/internal/v1/gm/rule-answers", "/internal/v1/gm/maps", "/internal/v1/gm/intent-classifications");
    }

    @Test
    void multipart_async_stream_source_candidate_and_player_map_have_separate_valid_schemas() throws IOException {
        JsonNode multipart = schema("rule-knowledge/schemas/multipart-upload.json");
        JsonNode batchMultipart = schema("rule-knowledge/schemas/batch-multipart-upload.json");
        JsonNode batchResponse = schema("rule-knowledge/schemas/batch-upload-response.json");
        JsonNode async = schema("rule-knowledge/schemas/async-status.json");
        JsonNode source = schema("rule-knowledge/schemas/source-location.json");
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
        assertTrue(stream.at("/properties/type/enum").toString().contains("INTERRUPTED"));
        assertEquals(1, candidate.at("/properties/sources/minItems").asInt());
        assertEquals("PLAYER_VISIBLE", playerMap.at("/properties/layers/items/properties/visibility/const").asText());
        assertFalse(playerMap.toString().contains("AI_ONLY"));
        assertTrue(evidenceSearch.at("/required").toString().contains("queryIntent"));
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
