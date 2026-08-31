package com.dndmaster.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CiManifestTest {
    private static final List<String> CONTEXT_MODULES = List.of(
            "identity-access-service",
            "adventure-service",
            "rule-knowledge-service",
            "character-management-service",
            "ai-game-master-service");

    @Test
    void workflowCoversAllDeploymentAndVerificationUnits() throws Exception {
        Path root = Path.of(System.getProperty("reactorRoot"));
        String workflow = Files.readString(root.getParent().resolve(".github/workflows/dnd-master-ci.yml"));

        assertContainsAll(workflow, CONTEXT_MODULES);
        assertContainsAll(workflow, List.of(
                "actions/setup-java@v4",
                "java-version: \"21\"",
                "cache: gradle",
                "actions/setup-node@v4",
                "node-version: \"22\"",
                "docker version",
                "docker compose -f src/infra/compose.yaml config",
                ":architecture-tests:test",
                ":contract-tests:test",
                ":system-tests:integrationTest",
                "npm --prefix src/web-ui run lint",
                "npm --prefix src/web-ui test -- --run",
                "npm --prefix src/web-ui run build",
                "npm --prefix src/web-ui run test:e2e"));
        assertFalse(workflow.contains("continue-on-error: true"));
    }

    private static void assertContainsAll(String content, List<String> expectedTokens) {
        for (String token : expectedTokens) {
            assertTrue(content.contains(token), () -> "Missing CI entry: " + token);
        }
    }
}
