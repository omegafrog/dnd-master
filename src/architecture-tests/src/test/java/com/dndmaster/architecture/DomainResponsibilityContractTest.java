package com.dndmaster.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainResponsibilityContractTest {
    private static final Map<String, String> SERVICES = services();
    private static final List<String> ALLOWED_DOMAIN_SERVICES = List.of(
            "OwnershipAccessPolicy.java",
            "RulebookIndexingPolicy.java",
            "VisibilityPolicy.java",
            "GameTimePolicy.java",
            "InformationPolicy.java");

    @Test
    void domainAndApplicationDependenciesPointInward() throws Exception {
        List<String> violations = new ArrayList<>();
        for (var service : SERVICES.entrySet()) {
            Path sourceRoot = reactorRoot().resolve(service.getKey()).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (var paths = Files.walk(sourceRoot)) {
                for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    inspectLayer(service, sourceRoot, source, Files.readString(source), violations);
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Domain responsibility violations:\n" + String.join("\n", violations));
    }

    @Test
    void domainServicesAreExplicitlyAllowedPolicies() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String module : SERVICES.keySet()) {
            Path domainRoot = reactorRoot().resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(domainRoot)) {
                continue;
            }
            try (var paths = Files.walk(domainRoot)) {
                for (Path source : paths.filter(path -> path.toString().contains("domain"))
                        .filter(path -> path.toString().endsWith("Service.java") || path.toString().endsWith("Policy.java"))
                        .toList()) {
                    if (!ALLOWED_DOMAIN_SERVICES.contains(source.getFileName().toString())) {
                        violations.add(module + ": unapproved domain service " + source.getFileName());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Domain services require an explicit allowlist entry:\n"
                + String.join("\n", violations));
    }

    private static void inspectLayer(
            Map.Entry<String, String> service,
            Path sourceRoot,
            Path source,
            String content,
            List<String> violations) {
        String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
        String location = service.getKey() + "/" + relative;
        if (relative.contains("/domain/")) {
            reject(content, service.getValue() + ".application.", location, "domain depends on application", violations);
            reject(content, service.getValue() + ".api.", location, "domain depends on API adapter", violations);
            reject(content, service.getValue() + ".infrastructure.", location, "domain depends on infrastructure adapter", violations);
            reject(content, "org.springframework.", location, "domain depends on Spring", violations);
            reject(content, "jakarta.persistence.", location, "domain depends on persistence", violations);
        }
        if (relative.contains("/application/")) {
            reject(content, service.getValue() + ".api.", location, "application depends on API adapter", violations);
            reject(content, service.getValue() + ".infrastructure.", location, "application depends on infrastructure adapter", violations);
        }
    }

    private static void reject(
            String content, String forbidden, String location, String reason, List<String> violations) {
        if (content.contains("import " + forbidden)) {
            violations.add(location + ": " + reason);
        }
    }

    private static Path reactorRoot() {
        return Path.of(System.getProperty("reactorRoot"));
    }

    private static Map<String, String> services() {
        Map<String, String> services = new LinkedHashMap<>();
        services.put("identity-access-service", "com.dndmaster.identityaccess");
        services.put("adventure-service", "com.dndmaster.adventure");
        services.put("rule-knowledge-service", "com.dndmaster.ruleknowledge");
        services.put("character-management-service", "com.dndmaster.character");
        services.put("dice-roll-service", "com.dndmaster.diceroll");
        services.put("combat-map-service", "com.dndmaster.combatmap");
        services.put("ai-game-master-service", "com.dndmaster.aigamemaster");
        return Map.copyOf(services);
    }
}
