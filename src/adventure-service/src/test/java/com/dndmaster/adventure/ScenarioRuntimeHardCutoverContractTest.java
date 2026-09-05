package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ScenarioRuntimeHardCutoverContractTest {
    private static final List<String> RUNTIME_ROOTS = List.of(
            "src/adventure-service/src/main/java",
            "src/ai-game-master-service/src/main",
            "src/contracts",
            "src/app-all/src/main/resources",
            "src/web-ui/src",
            "src/web-ui/e2e");
    private static final List<String> FORBIDDEN_TERMS = List.of(
            "Adventure" + "Story" + "Plan",
            "advance" + "Story" + "Plan",
            "Story" + "Plan",
            "story_" + "plan",
            "story" + "Plan");

    @Test
    void legacyRuntimeArtifactsAreAbsentFromRuntimeAndPublicContracts() throws IOException {
        Path repository = repositoryRoot();
        try (Stream<Path> files = RUNTIME_ROOTS.stream()
                .map(repository::resolve)
                .flatMap(this::walkIfPresent)) {
            files.forEach(path -> {
                String fileName = path.getFileName().toString();
                FORBIDDEN_TERMS.forEach(term -> assertFalse(fileName.contains(term), path.toString()));
                try {
                    String content = Files.readString(path);
                    FORBIDDEN_TERMS.forEach(term -> assertFalse(content.contains(term), path.toString()));
                } catch (IOException exception) {
                    throw new IllegalStateException("could not inspect " + path, exception);
                }
            });
        }
    }

    @Test
    void targetRuntimeTurnContractRemainsPublished() throws IOException {
        Path repository = repositoryRoot();
        Path openApi = repository.resolve("src/contracts/adventure/openapi.yaml");
        String contract = Files.readString(openApi);
        assertTrue(contract.contains("/api/v1/adventures/{adventureId}/turns"));
        assertTrue(contract.contains("Idempotency-Key"));
        assertTrue(contract.contains("If-Match-Version"));
    }

    @Test
    void hardCutoverMigrationDropsLegacyRuntimeStructures() throws IOException {
        String migration = Files.readString(repositoryRoot().resolve(
                "src/adventure-service/src/main/resources/db/migration/V59__scenario_model_runtime_hard_cutover.sql"));
        assertTrue(migration.contains("DROP TABLE IF EXISTS adventure_" + "story_" + "plan CASCADE"));
        assertTrue(migration.contains("DROP TABLE IF EXISTS adventure_" + "story_" + "plan_history CASCADE"));
        assertTrue(migration.contains("DROP TABLE IF EXISTS adventure_" + "story_" + "plan_revision CASCADE"));
        assertTrue(migration.contains("DROP TABLE IF EXISTS adventure_" + "story_" + "plan_current CASCADE"));
        assertTrue(migration.contains("DROP TABLE IF EXISTS gm_context_checkpoint CASCADE"));
        assertFalse(migration.contains("CREATE TABLE"));
    }

    private Stream<Path> walkIfPresent(Path root) {
        if (!Files.exists(root)) return Stream.empty();
        try {
            return Files.walk(root).filter(Files::isRegularFile);
        } catch (IOException exception) {
            throw new IllegalStateException("could not inspect " + root, exception);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("src/adventure-service/src/main"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
