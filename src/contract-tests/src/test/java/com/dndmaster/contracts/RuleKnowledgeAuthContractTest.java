package com.dndmaster.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Contract-level guard for the three rule-knowledge authentication surfaces.
 * It intentionally checks the HTTP contract, not Spring implementation details:
 * browser sessions are opaque Bearer values, internal calls use one shared
 * header, and the public catalog is the only unauthenticated read.
 */
class RuleKnowledgeAuthContractTest {
    private static final Path CONTRACT = Path.of(System.getProperty("user.dir"))
            .resolveSibling("contracts/rule-knowledge/openapi.yaml");

    @SuppressWarnings("unchecked")
    @Test
    void inventories_all_rule_knowledge_routes_and_auth_surfaces() throws IOException {
        Map<String, Object> root = document();
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        assertEquals(22, paths.size(), "the audited rule-knowledge path inventory must stay complete");

        for (String path : List.of(
                "/api/v1/rulebooks", "/api/v1/rulebooks/{rulebookId}",
                "/api/v1/rulebooks/{rulebookId}/source-preview", "/api/v1/rulebooks/{rulebookId}/retry",
                "/api/v1/rulebooks/{rulebookId}/retry-pages", "/api/v1/rulebooks/rule-set",
                "/internal/v1/rulebooks", "/internal/v1/rulebooks/published-catalog",
                "/internal/v1/rulebook-indexes", "/internal/v1/rulebooks/{rulebookId}/ownership",
                "/internal/v1/evidence-candidates/search", "/internal/v1/evidence-candidates/preparation-search",
                "/internal/v1/rule-evidence/search", "/internal/v1/story-sources/search",
                "/internal/v1/character-context/search", "/internal/v1/story-sources/{documentId}/context",
                "/internal/v1/story-sources/{documentId}/assets", "/internal/v1/rulebooks/{rulebookId}/game-system-definition",
                "/internal/v1/rag/reset", "/api/v1/rulebook-catalog",
                "/api/v1/backoffice/rulebook-catalog", "/api/v1/backoffice/rulebook-catalog/{catalogRevisionId}/publish")) {
            assertTrue(paths.containsKey(path), "missing route contract: " + path);
        }
        assertTrue(((Map<String, Object>) paths.get("/api/v1/rulebooks/{rulebookId}")).containsKey("delete"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void distinguishes_browser_internal_and_intentionally_public_auth() throws IOException {
        Map<String, Object> paths = paths();
        assertSecurity(paths, "/api/v1/rulebooks", "post", Set.of("browserBearer", "internalToken"));
        assertSecurity(paths, "/api/v1/rulebooks/{rulebookId}", "get", Set.of("browserBearer"));
        assertSecurity(paths, "/api/v1/rulebooks/{rulebookId}/source-preview", "get", Set.of("browserBearer", "internalToken"));
        assertSecurity(paths, "/api/v1/backoffice/rulebook-catalog", "post", Set.of("browserBearer"));
        assertSecurity(paths, "/api/v1/backoffice/rulebook-catalog/{catalogRevisionId}/publish", "post", Set.of("browserBearer"));
        assertSecurity(paths, "/api/v1/rulebook-catalog", "get", Set.of());

        for (String path : paths.keySet()) {
            if (!path.startsWith("/internal/v1/")) continue;
            Map<String, Object> operation = operation(paths, path, firstMethod(paths, path));
            if (path.equals("/internal/v1/rulebooks")) {
                assertSecurity(paths, path, "get", Set.of("browserBearer", "internalToken"));
            } else {
                assertSecurity(paths, path, firstMethod(paths, path), Set.of("internalToken"));
            }
            assertHasResponse(operation, "401");
            assertHasResponse(operation, "403");
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void protects_owner_scope_version_and_idempotency_in_the_contract() throws IOException {
        Map<String, Object> paths = paths();
        Map<String, Object> upload = operation(paths, "/api/v1/rulebooks", "post");
        assertParameter(upload, "ownerPlayerId", "query", true);
        assertParameter(upload, "Idempotency-Key", "header", false);
        Map<String, Object> uploadSchema = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) upload
                .get("requestBody")).get("content")).get("multipart/form-data");
        assertTrue(uploadSchema.toString().contains("batch-multipart-upload.json"));

        Map<String, Object> candidates = operation(paths, "/internal/v1/evidence-candidates/search", "post");
        Map<String, Object> candidateSchema = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) candidates
                .get("requestBody")).get("content")).get("application/json");
        String candidateText = candidateSchema.toString();
        assertTrue(candidateText.contains("ownerId"));
        assertTrue(candidateText.contains("sessionId"));
        assertTrue(candidateText.contains("scenarioPackageId"));
        Map<String, Object> components = (Map<String, Object>) document().get("components");
        Map<String, Object> candidateScope = (Map<String, Object>) ((Map<String, Object>) components.get("schemas"))
                .get("CandidateScope");
        assertTrue(candidateScope.toString().contains("extractionVersion"));

        Map<String, Object> context = operation(paths, "/internal/v1/story-sources/{documentId}/context", "get");
        assertParameter(context, "ownerId", "query", true);
        assertParameter(context, "extractionVersion", "query", true);
        assertParameter(context, "locator", "query", true);

        Map<String, Object> catalog = operation(paths, "/api/v1/rulebook-catalog", "get");
        assertTrue(String.valueOf(catalog.get("description")).contains("READY"));
        assertTrue(String.valueOf(catalog.get("description")).contains("published"));
        assertFalse(catalog.containsKey("parameters"), "public catalog must not smuggle an owner scope into the request");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> document() throws IOException {
        Map<String, Object> root = new Yaml().load(Files.readString(CONTRACT));
        assertNotNull(root.get("components"));
        Map<String, Object> schemes = (Map<String, Object>) ((Map<String, Object>) root.get("components"))
                .get("securitySchemes");
        assertEquals("bearer", ((Map<String, Object>) schemes.get("browserBearer")).get("scheme"));
        assertEquals("X-Internal-Token", ((Map<String, Object>) schemes.get("internalToken")).get("name"));
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paths() throws IOException {
        return (Map<String, Object>) document().get("paths");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
    }

    @SuppressWarnings("unchecked")
    private static String firstMethod(Map<String, Object> paths, String path) {
        Map<String, Object> route = (Map<String, Object>) paths.get(path);
        return route.containsKey("get") ? "get" : route.containsKey("post") ? "post" : route.containsKey("delete") ? "delete" : "put";
    }

    @SuppressWarnings("unchecked")
    private static void assertSecurity(Map<String, Object> paths, String path, String method, Set<String> expected) {
        Map<String, Object> op = operation(paths, path, method);
        List<Map<String, Object>> security = (List<Map<String, Object>>) op.get("security");
        Set<String> actual = security.stream().flatMap(item -> item.keySet().stream()).collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, actual, path + " " + method + " security");
    }

    @SuppressWarnings("unchecked")
    private static void assertParameter(Map<String, Object> operation, String name, String location, boolean required) {
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertTrue(parameters.stream().anyMatch(parameter -> name.equals(parameter.get("name"))
                && location.equals(parameter.get("in")) && required == Boolean.TRUE.equals(parameter.get("required"))),
                "missing parameter contract " + name + " in " + location);
    }

    @SuppressWarnings("unchecked")
    private static void assertHasResponse(Map<String, Object> operation, String status) {
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        assertTrue(responses.containsKey(status), "protected operation must document " + status);
    }
}
