package com.dndmaster.gmeval;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.gmeval.registry.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptRegistryTest {
    @Test
    void rolesCanReuseVersionNamesButCannotCrossParentLineage() {
        PromptRegistry registry = new PromptRegistry();
        PromptArtifact planner = artifact(PromptRole.PLANNER, "1.0.0", null, false);
        PromptArtifact writer = artifact(PromptRole.WRITER, "1.0.0", null, false);

        registry.register(planner);
        registry.register(writer);

        assertDoesNotThrow(() -> registry.register(artifact(
                PromptRole.PLANNER, "1.1.0", new PromptVersion(PromptRole.PLANNER, "1.0.0"), false)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(artifact(
                PromptRole.WRITER, "1.1.0", new PromptVersion(PromptRole.PLANNER, "1.0.0"), false)));
    }

    @Test
    void versionIsImmutableAndOneActiveBaselineExistsPerRole() {
        PromptRegistry registry = new PromptRegistry();
        PromptArtifact planner = artifact(PromptRole.PLANNER, "1.0.0", null, true);
        PromptArtifact writer = artifact(PromptRole.WRITER, "1.0.0", null, true);
        registry.registerBaseline(planner);
        registry.registerBaseline(writer);

        assertEquals(planner.promptVersion(), registry.active(PromptRole.PLANNER).promptVersion());
        assertEquals(writer.promptVersion(), registry.active(PromptRole.WRITER).promptVersion());
        assertEquals(PromptArtifactStatus.ACTIVE, registry.list().stream()
                .filter(value -> value.promptVersion().equals(planner.promptVersion()))
                .findFirst().orElseThrow().status());

        assertThrows(IllegalArgumentException.class, () -> registry.register(
                artifact(PromptRole.PLANNER, "1.0.0", null, false)));
        assertThrows(IllegalStateException.class, () -> registry.activate(
                artifact(PromptRole.PLANNER, "1.1.0", null, false).promptVersion()));

        PromptArtifact next = artifact(PromptRole.PLANNER, "1.1.0", planner.promptVersion(), true);
        registry.register(next);
        registry.approve(next.promptVersion());
        registry.activate(next.promptVersion());
        assertEquals(next.promptVersion(), registry.active(PromptRole.PLANNER).promptVersion());
        assertEquals(writer.promptVersion(), registry.active(PromptRole.WRITER).promptVersion());
    }

    @Test
    void activeReadEntryPointExposesOnlyApprovedRuntimeConfiguration() {
        PromptRegistry registry = new PromptRegistry();
        PromptRegistryReadPort read = new PromptRegistryReadService(registry);
        assertThrows(IllegalStateException.class, () -> read.active(PromptRole.JUDGE));

        PromptArtifact artifact = artifact(PromptRole.JUDGE, "2.0.0", null, true);
        registry.registerBaseline(artifact);
        PromptRuntimeConfiguration configuration = read.active(PromptRole.JUDGE);
        assertEquals(artifact.promptVersion(), configuration.promptVersion());
        assertEquals(artifact.modelVersion(), configuration.modelVersion());
        assertEquals(artifact.promptContent(), configuration.promptContent());
        assertEquals(artifact.contextOrdering(), configuration.contextOrdering());
        assertEquals(artifact.exemplarPlacement(), configuration.exemplarPlacement());
    }

    @Test
    void jsonStoreRestoresRegisteredBaselineAndRejectsInlineUnregisteredUse() throws Exception {
        Path file = Files.createTempFile("prompt-registry", ".json");
        PromptArtifact artifact = artifact(PromptRole.VERIFIER, "1.0.0", null, true);
        PromptRegistry first = new PromptRegistry(new JsonPromptRegistryStore(file));
        first.registerBaseline(artifact);

        PromptRegistry restored = new PromptRegistry(new JsonPromptRegistryStore(file));
        assertEquals(artifact.promptVersion(), restored.active(PromptRole.VERIFIER).promptVersion());
        assertThrows(IllegalStateException.class, () -> restored.resolveInline(PromptRole.VERIFIER, "inline prompt"));
    }

    @Test
    void rejectsHoldoutUsageAndSceneOrAdventureLeakageAcrossSplits() {
        DatasetCaseRef train = ref("train", DatasetSplit.TRAIN, "adventure-a", "scene-1");
        DatasetCaseRef dev = ref("dev", DatasetSplit.DEV, "adventure-b", "scene-2");
        DatasetCaseRef holdout = ref("holdout", DatasetSplit.HOLDOUT, "adventure-c", "scene-3");

        assertDoesNotThrow(() -> DatasetSplitPolicy.validate(List.of(train, dev, holdout)));
        assertThrows(IllegalArgumentException.class, () -> DatasetSplitPolicy.validateForUsage(
                List.of(holdout), DatasetUsage.CANDIDATE));
        assertThrows(IllegalArgumentException.class, () -> DatasetSplitPolicy.validate(List.of(
                train, ref("leak", DatasetSplit.DEV, "adventure-a", "scene-9"))));
        assertThrows(IllegalArgumentException.class, () -> DatasetSplitPolicy.validate(List.of(
                train, ref("leak", DatasetSplit.DEV, "adventure-z", "scene-1"))));
    }

    private static PromptArtifact artifact(PromptRole role, String version, PromptVersion parent, boolean baseline) {
        return new PromptArtifact(new PromptVersion(role, version), parent,
                "You are the " + role.name().toLowerCase() + " role.",
                "schema-1", List.of("resolved-turn", "writer-context", "exemplars"),
                "after-context", "model-" + role.name().toLowerCase(), "config-1",
                "gm-turn-v1", "eval-v1", baseline, PromptArtifactStatus.DRAFT);
    }

    private static DatasetCaseRef ref(String id, DatasetSplit split, String adventure, String scene) {
        return new DatasetCaseRef(id, "dataset-v1", split, adventure, scene);
    }
}
