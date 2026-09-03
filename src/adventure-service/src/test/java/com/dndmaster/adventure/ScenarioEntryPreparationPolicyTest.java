package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioEntryPreparationPolicy;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.RulebookEdition;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioEntryPreparationPolicyTest {
    @Test
    void selects_explicit_opening_with_its_published_evidence() {
        var id = new KnowledgeDocumentId(UUID.randomUUID());
        var result = new ScenarioEntryPreparationPolicy().prepare(bundle(id), List.of(
                new ResolutionExtractionPort.SourceExcerpt("STORYBOOK", id, 1, "page:2", "Opening: You arrive at the old mill.")));

        assertEquals("EXPLICIT_SOURCE", result.decision().name());
        assertEquals("page:2", result.sourceAnchor());
        assertEquals(1, result.evidence().size());
    }

    @Test
    void rejects_future_event_and_unmade_choice_as_entry_and_falls_back_to_minimal_prologue() {
        var id = new KnowledgeDocumentId(UUID.randomUUID());
        var result = new ScenarioEntryPreparationPolicy().prepare(bundle(id), List.of(
                new ResolutionExtractionPort.SourceExcerpt("STORYBOOK", id, 1, "page:3", "Later, the villain will attack if you decide to open the gate.")));

        assertTrue(result.requiresPrologue());
        assertTrue(result.evidence().isEmpty());
    }

    @Test
    void sparse_source_still_produces_a_safe_start() {
        var id = new KnowledgeDocumentId(UUID.randomUUID());
        var result = new ScenarioEntryPreparationPolicy().prepare(bundle(id), List.of());
        assertEquals("MINIMAL_PROLOGUE", result.decision().name());
        assertTrue(result.entryPoint().contains("safe"));
    }

    @Test
    void compilation_owns_the_prepared_entry_result_in_the_package() {
        var id = new KnowledgeDocumentId(UUID.randomUUID());
        var packageVersion = new ScenarioPackageCompilationService(new InMemoryPackages()).compile(bundle(id), List.of(),
                List.of(new ResolutionExtractionPort.SourceExcerpt("STORYBOOK", id, 1, "page:1", "Opening: You arrive at the old mill.")));
        assertEquals("EXPLICIT_SOURCE", packageVersion.entryResult().decision().name());
    }

    private static ScenarioSourceBundle bundle(KnowledgeDocumentId id) {
        return ScenarioSourceBundle.create(new ScenarioBundleId(UUID.randomUUID()), new OwnerPlayerId(UUID.randomUUID()),
                "entry", RulebookEdition.DND_5E_2014,
                new ScenarioSourceBundleRevision(1, List.of(new ScenarioBundleDocumentSelection(
                        id, ScenarioBundleDocumentRole.MAIN_SCENARIO, KnowledgeDocumentStatus.EXTRACTED,
                        "story.md", "STORYBOOK", 1))));
    }

    private static final class InMemoryPackages implements ScenarioPackageRepository {
        private ScenarioPackage value;
        @Override public java.util.Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<ScenarioPackage> findById(UUID id) { return java.util.Optional.ofNullable(value); }
        @Override public List<ScenarioPackage> findByBundleId(UUID id) { return value == null ? List.of() : List.of(value); }
        @Override public void save(ScenarioPackage scenarioPackage) { value = scenarioPackage; }
        @Override public void saveBlueprint(UUID packageId, com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint blueprint) { }
    }
}
