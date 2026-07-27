package com.dndmaster.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DomainEventBoundaryTest {
    private static final Map<String, String> SERVICES = services();
    private static final Pattern RAW_TEXT_COMPONENT = Pattern.compile(
            "(?i)\\bString\\s+(rawText|text|content|excerpt|prompt|response|document|fileContent)\\b");

    @Test
    void domainEventsStayLocalAndDoNotCarryRawText() throws Exception {
        List<String> violations = new ArrayList<>();
        for (var service : SERVICES.entrySet()) {
            for (SourceFile source : javaSources(service.getKey())) {
                if (!isEvent(source)) {
                    continue;
                }
                if (!source.relativePath().contains("/domain/")) {
                    violations.add(source.location(service.getKey()) + ": domain event must live in its local domain package");
                }
                if (RAW_TEXT_COMPONENT.matcher(source.content()).find()) {
                    violations.add(source.location(service.getKey()) + ": event payload contains prohibited raw text");
                }
                for (var other : SERVICES.entrySet()) {
                    if (!other.getKey().equals(service.getKey())
                            && source.content().contains("import " + other.getValue() + ".")) {
                        violations.add(source.location(service.getKey()) + ": event imports another bounded context");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Domain event boundary violations:\n" + String.join("\n", violations));
    }

    @Test
    void eventHandlersRunOnlyAfterCommit() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String module : SERVICES.keySet()) {
            for (SourceFile source : javaSources(module)) {
                String content = source.content();
                if (content.contains("@EventListener") || content.contains("ApplicationListener<")) {
                    violations.add(source.location(module) + ": pre-commit event handler is forbidden");
                }
                if (content.contains("@TransactionalEventListener")
                        && !content.contains("TransactionPhase.AFTER_COMMIT")
                        && !content.contains("phase = AFTER_COMMIT")) {
                    violations.add(source.location(module) + ": transactional event handler must use AFTER_COMMIT");
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Event handler timing violations:\n" + String.join("\n", violations));
    }

    private static boolean isEvent(SourceFile source) {
        String name = Path.of(source.relativePath()).getFileName().toString();
        return source.relativePath().contains("/event/") || name.endsWith("Event.java");
    }

    private static List<SourceFile> javaSources(String module) throws Exception {
        Path sourceRoot = reactorRoot().resolve(module).resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        try (var paths = Files.walk(sourceRoot)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> read(sourceRoot, path))
                    .toList();
        }
    }

    private static SourceFile read(Path sourceRoot, Path source) {
        try {
            return new SourceFile(
                    sourceRoot.relativize(source).toString().replace('\\', '/'), Files.readString(source));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect " + source, exception);
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

    private record SourceFile(String relativePath, String content) {
        String location(String module) {
            return module + "/" + relativePath;
        }
    }
}
