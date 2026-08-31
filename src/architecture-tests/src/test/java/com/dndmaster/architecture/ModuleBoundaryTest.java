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
    private static final Map<String, List<String>> CONTEXT_PACKAGES = contextPackages();

    @Test
    void contextBuildsDoNotCreateInterContextJavaDependencies() throws Exception {
        Path root = reactorRoot();
        List<String> contextModules = List.copyOf(CONTEXT_PACKAGES.keySet());
        for (String module : contextModules) {
            Path buildFile = root.resolve(module).resolve("build.gradle.kts");
            if (!Files.isRegularFile(buildFile)) {
                continue;
            }
            String buildContent = Files.readString(buildFile);
            for (String otherContext : contextModules) {
                if (otherContext.equals(module)) {
                    continue;
                }
                String projectDep = "project(\":" + otherContext + "\")";
                assertTrue(
                        !buildContent.contains(projectDep),
                        () -> module + " must communicate with " + otherContext + " through a contract, not a Java dependency");
            }
        }
    }

    @Test
    void compiledClassesRespectDomainAndBoundedContextBoundaries() throws Exception {
        List<Path> classDirectories = CONTEXT_PACKAGES.keySet().stream()
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

        for (Map.Entry<String, List<String>> ownContext : CONTEXT_PACKAGES.entrySet()) {
            String[] otherPackages = CONTEXT_PACKAGES.entrySet().stream()
                    .filter(candidate -> !candidate.getKey().equals(ownContext.getKey()))
                    .flatMap(candidate -> candidate.getValue().stream())
                    .map(candidate -> candidate + "..")
                    .toArray(String[]::new);

            for (String ownPackage : ownContext.getValue()) {
                noClasses()
                        .that().resideInAPackage(ownPackage + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(otherPackages)
                        .allowEmptyShould(true)
                        .check(classes);
            }
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
