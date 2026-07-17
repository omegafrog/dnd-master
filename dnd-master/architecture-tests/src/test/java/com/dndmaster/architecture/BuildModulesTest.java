package com.dndmaster.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class BuildModulesTest {
    private static final List<String> EXPECTED_MODULES = List.of(
            "identity-access-service",
            "adventure-service",
            "rule-knowledge-service",
            "character-management-service",
            "dice-roll-service",
            "combat-map-service",
            "ai-game-master-service",
            "architecture-tests",
            "contract-tests",
            "system-tests");

    @Test
    void rootBuildPinsPlatformAndIncludesAllModules() throws Exception {
        Path root = Path.of(System.getProperty("reactorRoot"));
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve("pom.xml").toFile());

        assertEquals("21", firstText(document.getDocumentElement(), "java.version"));
        assertEquals("3.5.16", firstText(document.getDocumentElement(), "spring-boot.version"));
        assertEquals("1.1.8", firstText(document.getDocumentElement(), "spring-ai.version"));

        var moduleNodes = document.getElementsByTagName("module");
        var modules = java.util.stream.IntStream.range(0, moduleNodes.getLength())
                .mapToObj(index -> moduleNodes.item(index).getTextContent().trim())
                .toList();
        assertEquals(EXPECTED_MODULES, modules);
        for (String module : modules) {
            assertTrue(Files.isRegularFile(root.resolve(module).resolve("pom.xml")), () -> "Missing module POM: " + module);
        }

        assertEquals("[21,22)", firstText(document.getDocumentElement(), "requireJavaVersion"));
        assertTrue(document.getElementsByTagName("dependencyConvergence").getLength() == 1);
    }

    private static String firstText(Element root, String tagName) {
        return root.getElementsByTagName(tagName).item(0).getTextContent().trim();
    }
}
