package com.dndmaster.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PackageDependencyTest {
    private static final Map<String, String> SERVICE_PACKAGES = servicePackages();
    private static final List<String> FORBIDDEN_REPOSITORY_REFERENCES = List.of(
            "harness_codex/", ".codex/", "completions/", "docs/changes/", "docs/plans/");

    @Test
    void sourceImportsRespectPackageAndPersistenceBoundaries() throws Exception {
        List<String> violations = new ArrayList<>();
        Path root = Path.of(System.getProperty("reactorRoot"));

        for (var service : SERVICE_PACKAGES.entrySet()) {
            Path sourceRoot = root.resolve(service.getKey()).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (var paths = Files.walk(sourceRoot)) {
                for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    inspectSource(service, sourceRoot, source, Files.readString(source), violations);
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Forbidden dependencies:\n" + String.join("\n", violations));
    }

    private static void inspectSource(
            Map.Entry<String, String> service,
            Path sourceRoot,
            Path source,
            String content,
            List<String> violations) {
        String relativePath = sourceRoot.relativize(source).toString().replace('\\', '/');
        String location = service.getKey() + "/src/main/java/" + relativePath;

        if (relativePath.contains("/domain/")) {
            rejectContains(content, "import org.springframework.", location, "domain depends on Spring", violations);
            rejectContains(content, "import jakarta.persistence.", location, "domain depends on JPA", violations);
        }

        for (var otherService : SERVICE_PACKAGES.entrySet()) {
            if (!otherService.getKey().equals(service.getKey())) {
                rejectContains(
                        content,
                        "import " + otherService.getValue() + ".",
                        location,
                        "cross-BC Java import of " + otherService.getKey(),
                        violations);
                rejectContains(
                        content,
                        otherService.getValue() + ".infrastructure.persistence",
                        location,
                        "cross-BC persistence access",
                        violations);
            }
        }

        for (String forbiddenReference : FORBIDDEN_REPOSITORY_REFERENCES) {
            rejectContains(content, forbiddenReference, location, "forbidden repository path reference", violations);
        }
    }

    private static void rejectContains(
            String content, String forbidden, String location, String reason, List<String> violations) {
        if (content.contains(forbidden)) {
            violations.add(location + ": " + reason + " [" + forbidden + "]");
        }
    }

    private static Map<String, String> servicePackages() {
        Map<String, String> packages = new LinkedHashMap<>();
        packages.put("identity-access-service", "com.dndmaster.identityaccess");
        packages.put("adventure-service", "com.dndmaster.adventure");
        packages.put("rule-knowledge-service", "com.dndmaster.ruleknowledge");
        packages.put("character-management-service", "com.dndmaster.character");
        packages.put("dice-roll-service", "com.dndmaster.diceroll");
        packages.put("combat-map-service", "com.dndmaster.combatmap");
        packages.put("ai-game-master-service", "com.dndmaster.aigamemaster");
        return Map.copyOf(packages);
    }
}
