package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView;
import com.dndmaster.adventure.application.scenario.preparation.PlayPreparationStatus;
import com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionCatalogPort;
import com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionsView;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioPreparationApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioPreparationApplicationServiceTest {
    @Test
    void allowsPreparationWhenRulebookIsSeparateFromScenarioBundle() {
        TestFixture fixture = bundle(withoutRulebookPackage(), bundleWithoutRulebook());
        ScenarioPreparationApplicationService service = service(fixture);

        var preparation = service.read(fixture.packageId(), owner());

        assertEquals(PlayPreparationStatus.READY, preparation.status());
        assertTrue(preparation.blockers().isEmpty());
        assertTrue(preparation.characterCreationBlueprint().available());
        assertEquals(0, preparation.characterCreationBlueprint().rulebookDocumentCount());
        assertEquals(1, preparation.characterCreationBlueprint().storybookDocumentCount());
    }

    @Test
    void exposesBlueprintAndRuntimeDefaultsWhenRulebookAndResolutionExist() {
        TestFixture fixture = bundle(withRulebookPackage(), bundleWithRulebook());
        ScenarioPreparationApplicationService service = service(fixture);

        var preparation = service.read(fixture.packageId(), owner());
        RuntimeOptionsView options = service.runtimeOptions(owner());

        assertEquals(PlayPreparationStatus.READY, preparation.status());
        CharacterCreationBlueprintView blueprint = preparation.characterCreationBlueprint();
        assertTrue(blueprint.available());
        assertEquals("STORYBOOK 1개, RULEBOOK 런타임 세트 별도", blueprint.summary());
        assertEquals(0, blueprint.rulebookDocumentCount());
        assertEquals(1, blueprint.storybookDocumentCount());
        assertTrue(blueprint.diagnostics().isEmpty());
        assertEquals(1, preparation.characterLimit().maximumCharacters());

        assertEquals("ollama", options.defaultEngineId());
        assertEquals(List.of("search", "move"), options.defaultToolIds());
        assertTrue(options.engines().stream().anyMatch(option -> option.id().equals("ollama") && option.selectedByDefault()));
        assertTrue(options.tools().stream().anyMatch(option -> option.id().equals("search") && option.selectedByDefault()));
    }

    @Test
    void blocksCharacterCreationWhenPackageRevisionIsStale() {
        ScenarioSourceBundle staleBundle = ScenarioSourceBundle.create(
                new ScenarioBundleId(bundleId()), owner(), new ScenarioSourceBundleRevision(5, bundleWithRulebook().currentRevision().documents()));
        TestFixture fixture = bundle(withRulebookPackage(), staleBundle);

        var preparation = service(fixture).read(fixture.packageId(), owner());

        assertEquals(PlayPreparationStatus.BLOCKED, preparation.status());
        assertTrue(preparation.blockers().stream().anyMatch(message -> message.contains("개정")));
        assertFalse(preparation.characterCreationBlueprint().available());
    }

    private static ScenarioPreparationApplicationService service(TestFixture fixture) {
        return new ScenarioPreparationApplicationService(
                fixture.packages(), fixture.bundles(), fixture.runtimeOptions());
    }

    private static TestFixture bundle(ScenarioPackage scenarioPackage, ScenarioSourceBundle bundle) {
        return new TestFixture(scenarioPackage, bundle);
    }

    private static ScenarioPackage withoutRulebookPackage() {
        return ScenarioPackage.publish(
                new ScenarioBundleId(bundleId()),
                3,
                "fp-no-rulebook",
                List.of(new ScenarioBundleDocumentSelection(
                        new KnowledgeDocumentId(storybookDocumentId()),
                        ScenarioBundleDocumentRole.MAIN_SCENARIO,
                        KnowledgeDocumentStatus.INDEXED,
                        "story.pdf",
                        "STORYBOOK",
                        1)),
                List.of(validUnit()),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()));
    }

    private static ScenarioSourceBundle bundleWithoutRulebook() {
        return ScenarioSourceBundle.create(
                new ScenarioBundleId(bundleId()),
                owner(),
                new ScenarioSourceBundleRevision(3, List.of(new ScenarioBundleDocumentSelection(
                        new KnowledgeDocumentId(storybookDocumentId()),
                        ScenarioBundleDocumentRole.MAIN_SCENARIO,
                        KnowledgeDocumentStatus.INDEXED,
                        "story.pdf",
                        "STORYBOOK",
                        1))));
    }

    private static ScenarioPackage withRulebookPackage() {
        return ScenarioPackage.publish(
                new ScenarioBundleId(bundleId()),
                4,
                "fp-ready",
                List.of(
                        new ScenarioBundleDocumentSelection(
                                new KnowledgeDocumentId(storybookDocumentId()),
                                ScenarioBundleDocumentRole.MAIN_SCENARIO,
                                KnowledgeDocumentStatus.INDEXED,
                                "story.pdf",
                                "STORYBOOK",
                                1),
                        new ScenarioBundleDocumentSelection(
                                new KnowledgeDocumentId(rulebookDocumentId()),
                                ScenarioBundleDocumentRole.REFERENCE,
                                KnowledgeDocumentStatus.INDEXED,
                                "rules.pdf",
                                "RULEBOOK",
                                1)),
                List.of(validUnit()),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()));
    }

    private static ScenarioSourceBundle bundleWithRulebook() {
        return ScenarioSourceBundle.create(
                new ScenarioBundleId(bundleId()),
                owner(),
                new ScenarioSourceBundleRevision(4, List.of(
                        new ScenarioBundleDocumentSelection(
                                new KnowledgeDocumentId(storybookDocumentId()),
                                ScenarioBundleDocumentRole.MAIN_SCENARIO,
                                KnowledgeDocumentStatus.INDEXED,
                                "story.pdf",
                                "STORYBOOK",
                                1),
                        new ScenarioBundleDocumentSelection(
                                new KnowledgeDocumentId(rulebookDocumentId()),
                                ScenarioBundleDocumentRole.REFERENCE,
                                KnowledgeDocumentStatus.INDEXED,
                                "rules.pdf",
                                "RULEBOOK",
                                1))));
    }

    private static ScenarioResolutionUnit validUnit() {
        return new ScenarioResolutionUnit(
                ResolutionKind.SAVING_THROW,
                "Dexterity",
                15,
                null,
                ResolutionVisibility.GM_REFERENCE,
                "Dexterity save DC 15",
                List.of(new ScenarioSourceReference(new KnowledgeDocumentId(storybookDocumentId()), 1, "page:4")),
                "ai",
                ScenarioResolutionDetail.empty(),
                ResolutionStatus.COMPLETE,
                List.of());
    }

    private static UUID bundleId() {
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    private static UUID storybookDocumentId() {
        return UUID.fromString("22222222-2222-2222-2222-222222222222");
    }

    private static UUID rulebookDocumentId() {
        return UUID.fromString("33333333-3333-3333-3333-333333333333");
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    }

    private record TestFixture(
            ScenarioPackage scenarioPackage,
            ScenarioSourceBundle bundle) {
        UUID packageId() {
            return scenarioPackage.packageId();
        }

        ScenarioPackageRepository packages() {
            return new ScenarioPackageRepository() {
                private final Map<UUID, ScenarioPackage> byId = Map.of(scenarioPackage.packageId(), scenarioPackage);

                @Override
                public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) {
                    return byId.values().stream().filter(value -> value.inputFingerprint().equals(fingerprint)).findFirst();
                }

                @Override
                public Optional<ScenarioPackage> findById(UUID id) {
                    return Optional.ofNullable(byId.get(id));
                }

                @Override
                public void save(ScenarioPackage value) {}
            };
        }

        ScenarioBundleRepository bundles() {
            return new ScenarioBundleRepository() {
                @Override
                public Optional<ScenarioSourceBundle> findById(ScenarioBundleId id) {
                    return id.equals(bundle.id()) ? Optional.of(bundle) : Optional.empty();
                }

                @Override
                public void save(ScenarioSourceBundle value) {}
            };
        }

        RuntimeOptionCatalogPort runtimeOptions() {
            return new RuntimeOptionCatalogPort() {
                @Override
                public RuntimeOptionsView read(OwnerPlayerId ownerPlayerId) {
                    return RuntimeOptionsView.defaults();
                }
            };
        }
    }
}
