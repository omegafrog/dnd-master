package com.dndmaster.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {
    private static final Map<String, String> SERVICE_PACKAGES = servicePackages();

    @Test
    void serviceBuildsDoNotCreateInterServiceJavaDependencies() throws Exception {
        Path root = reactorRoot();
        List<String> allServiceNames = List.copyOf(SERVICE_PACKAGES.keySet());
        for (String module : SERVICE_PACKAGES.keySet()) {
            Path buildFile = root.resolve(module).resolve("build.gradle.kts");
            if (!Files.isRegularFile(buildFile)) {
                continue;
            }
            String buildContent = Files.readString(buildFile);
            for (String otherService : allServiceNames) {
                if (otherService.equals(module)) {
                    continue;
                }
                String projectDep = "project(\":" + otherService + "\")";
                assertTrue(
                        !buildContent.contains(projectDep),
                        () -> module + " must communicate with " + otherService + " through a contract, not a Java dependency");
            }
        }
    }

    @Test
    void compiledClassesRespectDomainAndBoundedContextBoundaries() throws Exception {
        List<Path> classDirectories = SERVICE_PACKAGES.keySet().stream()
                .map(module -> reactorRoot().resolve(module).resolve("build/classes/java/main"))
                .filter(Files::isDirectory)
                .filter(ModuleBoundaryTest::containsClassFile)
                .toList();
        if (classDirectories.isEmpty()) {
            return;
        }

        var classes = new ClassFileImporter().importPaths(classDirectories);
        noClasses()
                .that().resideInAPackage("com.dndmaster..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "org.springframework.ai..", "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(classes);

        for (String ownPackage : SERVICE_PACKAGES.values()) {
            String[] otherPackages = SERVICE_PACKAGES.values().stream()
                    .filter(candidate -> !candidate.equals(ownPackage))
                    .map(candidate -> candidate + "..")
                    .toArray(String[]::new);
            noClasses()
                    .that().resideInAPackage(ownPackage + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(otherPackages)
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    private static boolean containsClassFile(Path directory) {
        try (var paths = Files.walk(directory)) {
            return paths.anyMatch(path -> path.toString().endsWith(".class"));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect compiled classes in " + directory, exception);
        }
    }

    private static Path reactorRoot() {
        return Path.of(System.getProperty("reactorRoot"));
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
