package com.dndmaster.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BuildModulesTest {
    private static final List<String> EXPECTED_MODULES = List.of(
            "identity-access-service",
            "adventure-service",
            "rule-knowledge-service",
            "character-management-service",
            "dice-roll-service",
            "combat-map-service",
            "ai-game-master-service",
            "app-all",
            "architecture-tests",
            "contract-tests",
            "system-tests");

    @Test
    void settingsGradleIncludesAllModules() throws Exception {
        Path root = Path.of(System.getProperty("reactorRoot"));
        String settings = Files.readString(root.resolve("settings.gradle.kts"));

        Pattern includePattern = Pattern.compile("include\\(([^)]+)\\)");
        Matcher matcher = includePattern.matcher(settings);
        assertTrue(matcher.find(), "settings.gradle.kts must contain include(...)");

        String includeBlock = matcher.group(1);
        Pattern modulePattern = Pattern.compile("\":([^\"]+)\"");
        List<String> modules = modulePattern.matcher(includeBlock).results()
                .map(m -> m.group(1))
                .toList();

        assertEquals(EXPECTED_MODULES, modules);
        for (String module : modules) {
            assertTrue(
                    Files.isRegularFile(root.resolve(module).resolve("build.gradle.kts")),
                    () -> "Missing build.gradle.kts: " + module);
        }
    }
}
