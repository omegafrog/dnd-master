package com.dndmaster.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PackageDependencyTest {
    private static final Map<String, List<String>> CONTEXT_PACKAGES = contextPackages();
    private static final List<String> FORBIDDEN_REPOSITORY_REFERENCES = List.of(
            "harness_codex/", ".codex/", "completions/", "docs/changes/", "docs/plans/");

    @Test
    void sourceImportsRespectPackageAndPersistenceBoundaries() throws Exception {
        List<String> violations = new ArrayList<>();
        Path root = Path.of(System.getProperty("reactorRoot"));

        for (var context : CONTEXT_PACKAGES.entrySet()) {
            Path sourceRoot = root.resolve(context.getKey()).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (var paths = Files.walk(sourceRoot)) {
                for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    inspectSource(context, sourceRoot, source, Files.readString(source), violations);
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Forbidden dependencies:\n" + String.join("\n", violations));
    }

    private static void inspectSource(
            Map.Entry<String, List<String>> context,
            Path sourceRoot,
            Path source,
            String content,
            List<String> violations) {
        String relativePath = sourceRoot.relativize(source).toString().replace('\\', '/');
        String location = context.getKey() + "/src/main/java/" + relativePath;

        if (relativePath.contains("/domain/")) {
            rejectContains(content, "import org.springframework.", location, "domain depends on Spring", violations);
            rejectContains(content, "import jakarta.persistence.", location, "domain depends on JPA", violations);
        }

        for (var otherContext : CONTEXT_PACKAGES.entrySet()) {
            if (otherContext.getKey().equals(context.getKey())) {
                continue;
            }
            for (String otherPackage : otherContext.getValue()) {
                rejectContains(
                        content,
                        "import " + otherPackage + ".",
                        location,
                        "cross-BC Java import of " + otherContext.getKey(),
                        violations);
                rejectContains(
                        content,
                        otherPackage + ".infrastructure.persistence",
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

    private static Map<String, List<String>> contextPackages() {
        Map<String, List<String>> packages = new LinkedHashMap<>();
        packages.put("identity-access-service", List.of("com.dndmaster.identityaccess"));
        packages.put("adventure-service", List.of(
                "com.dndmaster.adventure",
                "com.dndmaster.diceroll",
                "com.dndmaster.combatmap"));
        packages.put("rule-knowledge-service", List.of("com.dndmaster.ruleknowledge"));
        packages.put("character-management-service", List.of("com.dndmaster.character"));
        packages.put("ai-game-master-service", List.of("com.dndmaster.aigamemaster"));
        return Map.copyOf(packages);
    }
}
