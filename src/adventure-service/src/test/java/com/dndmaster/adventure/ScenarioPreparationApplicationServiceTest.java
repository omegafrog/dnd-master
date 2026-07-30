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
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler;
import com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

class ScenarioPreparationApplicationServiceTest {
    @Test
    void extractsRulebookBaseChoicesAndStorybookAdditionalInputsInSeparateScopes() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var search = mock(CharacterContextSearchPort.class);
        var tags = mock(CharacterInputTagExtractionPort.class);
        var scenarioPackage = withRulebookPackage();
        var rulebook = new KnowledgeDocumentId(rulebookDocumentId());
        var storybook = new KnowledgeDocumentId(storybookDocumentId());
        when(packages.findById(scenarioPackage.packageId())).thenReturn(Optional.of(scenarioPackage));
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundleWithRulebook()));
        when(search.search(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, CharacterContextSearchPort.Request.class);
            boolean storyScope = request.documents().stream().allMatch(scope -> scope.documentType().equals("STORYBOOK"));
            return storyScope
                    ? List.of(new CharacterContextSearchPort.Evidence(storybook, "STORYBOOK", 1, "page:8",
                            "Every hero records a campaign title chosen by player.", .97))
                    : List.of(new CharacterContextSearchPort.Evidence(rulebook, "RULEBOOK", 1, "page:8",
                            "Choose one race: Elf or Dwarf.", .97));
        });
        when(tags.extract(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, CharacterInputTagExtractionPort.Request.class);
            if (request.excerpts().stream().allMatch(excerpt -> excerpt.documentId().equals(storybook))) {
                return List.of(candidate("campaign_title", "캠페인 칭호", com.dndmaster.adventure.domain.scenario.InputMode.FREE_TEXT,
                        List.of(), storybook, "STORYBOOK", "Player chooses a campaign title."));
            }
            if (request.instruction().contains("key 'race'")) {
                return List.of(candidate("race", "종족", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT,
                        List.of("Elf", "Dwarf"), rulebook, "RULEBOOK", "Choose one race: Elf or Dwarf."));
            }
            return List.of();
        });

        var draft = new ScenarioPreparationApplicationService(packages, bundles, fixtureRuntimeOptions(), search, tags,
                new CharacterCreationBlueprintCompiler()).generateBlueprintDraft(scenarioPackage.packageId(), owner());

        var race = draft.fields().stream().filter(field -> field.key().equals("race")).findFirst().orElseThrow();
        var title = draft.fields().stream().filter(field -> field.key().equals("campaign_title")).findFirst()
                .orElseThrow(() -> new AssertionError(draft.fields().stream().map(field -> field.key() + ":" + field.sourceType()).toList()));
        assertEquals("RULEBOOK", race.sourceType());
        assertEquals(com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT.name(), race.inputMode());
        assertEquals(List.of("Elf", "Dwarf"), race.options());
        assertEquals("STORYBOOK", title.sourceType());
        assertEquals(com.dndmaster.adventure.domain.scenario.InputMode.FREE_TEXT.name(), title.inputMode());
        assertTrue(title.options().isEmpty());
        var searches = ArgumentCaptor.forClass(CharacterContextSearchPort.Request.class);
        verify(search, atLeast(4)).search(searches.capture());
        assertTrue(searches.getAllValues().stream().anyMatch(request -> request.documents().stream()
                .allMatch(scope -> scope.documentType().equals("RULEBOOK"))));
        assertTrue(searches.getAllValues().stream().anyMatch(request -> request.documents().stream()
                .allMatch(scope -> scope.documentType().equals("STORYBOOK"))));
    }

    @Test
    void enrichesTemplateWithStorybookChoicesOnly() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var search = mock(CharacterContextSearchPort.class);
        var tags = mock(CharacterInputTagExtractionPort.class);
        var scenarioPackage = withRulebookPackage();
        when(packages.findById(scenarioPackage.packageId())).thenReturn(Optional.of(scenarioPackage));
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundleWithRulebook()));
        var storybook = new KnowledgeDocumentId(storybookDocumentId());
        when(search.search(any())).thenReturn(List.of(new CharacterContextSearchPort.Evidence(
                storybook, "STORYBOOK", 1, "page:8", "The campaign permits only Eladrin and Shifter heroes.", .97)));
        when(tags.extract(any())).thenReturn(List.of(new CharacterInputTagExtractionPort.CharacterInputTagCandidate(
                "race", "Race", null, true, com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT,
                List.of("Eladrin", "Shifter"), List.of(), "HIGH",
                List.of(new ScenarioSourceReference(storybook, 1, "page:8")),
                "The campaign permits only Eladrin and Shifter heroes.", "STORYBOOK")));

        var service = new ScenarioPreparationApplicationService(packages, bundles, fixtureRuntimeOptions(), search, tags,
                new CharacterCreationBlueprintCompiler());

        var draft = service.generateBlueprintDraft(scenarioPackage.packageId(), owner());

        verify(search, atLeast(1)).search(any());
        verify(tags, atLeast(1)).extract(any());
        verify(packages).saveBlueprint(eq(scenarioPackage.packageId()), any());
        var race = draft.roots().stream().filter(node -> node.key().equals("race")).findFirst().orElseThrow();
        assertEquals(List.of("Eladrin", "Shifter"), race.options());
        assertEquals("STORYBOOK", draft.fields().stream().filter(field -> field.key().equals("race"))
                .findFirst().orElseThrow().sourceType());
        assertEquals("NEEDS_REVIEW", draft.status());
    }

    @Test
    void retrievesRequiredRulebookChoiceWithPerOptionDetails() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var search = mock(CharacterContextSearchPort.class);
        var tags = mock(CharacterInputTagExtractionPort.class);
        var scenarioPackage = withRulebookPackage();
        var rulebook = new KnowledgeDocumentId(rulebookDocumentId());
        when(packages.findById(scenarioPackage.packageId())).thenReturn(Optional.of(scenarioPackage));
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundleWithRulebook()));
        when(search.search(any())).thenReturn(List.of(new CharacterContextSearchPort.Evidence(
                rulebook, "RULEBOOK", 1, "page:8", "Choose a race and class. Dwarves and elves are available races.", .97)));
        when(tags.extract(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, CharacterInputTagExtractionPort.Request.class);
            String instruction = request.instruction();
            if (instruction.isBlank() || instruction.startsWith("Discover source-grounded")) return List.of(
                    rulebookChoice("race", "Race", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Elf", rulebook),
                    rulebookChoice("ideals", "Ideals", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Freedom", rulebook),
                    rulebookChoice("background", "Background", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Sage", rulebook),
                    rulebookChoice("class", "Class", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Wizard", rulebook),
                    rulebookChoice("class.startingEquipment", "Starting Equipment", com.dndmaster.adventure.domain.scenario.InputMode.MULTI_SELECT, "Explorer's Pack", rulebook));
            if (instruction.contains("class.startingEquipment")) return List.of(rulebookChoice(
                    "class.startingEquipment", "Starting Equipment", com.dndmaster.adventure.domain.scenario.InputMode.MULTI_SELECT,
                    "Explorer's Pack", rulebook));
            if (instruction.contains("'race'")) return List.of(rulebookChoice(
                    "race", "Race", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Elf", rulebook));
            if (instruction.contains("'ideals'")) return List.of(rulebookChoice(
                    "ideals", "Ideals", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Freedom", rulebook));
            if (instruction.contains("'background'")) return List.of(rulebookChoice(
                    "background", "Background", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Sage", rulebook));
            if (instruction.contains("'class'")) return List.of(rulebookChoice(
                    "class", "Class", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Wizard", rulebook));
            return List.of();
        });

        var service = new ScenarioPreparationApplicationService(packages, bundles, fixtureRuntimeOptions(), search, tags,
                new CharacterCreationBlueprintCompiler());

        var draft = service.generateBlueprintDraft(scenarioPackage.packageId(), owner());

        verify(search, atLeast(1)).search(any());
        verify(tags, atLeast(6)).extract(any());
        var extractions = ArgumentCaptor.forClass(CharacterInputTagExtractionPort.Request.class);
        verify(tags, atLeast(6)).extract(extractions.capture());
        var searches = ArgumentCaptor.forClass(CharacterContextSearchPort.Request.class);
        verify(search, atLeast(1)).search(searches.capture());
        assertTrue(searches.getAllValues().stream().anyMatch(request -> request.documents().stream()
                .allMatch(document -> document.documentType().equals("RULEBOOK"))));
        assertTrue(searches.getAllValues().stream().anyMatch(request -> request.documents().stream()
                .allMatch(document -> document.documentType().equals("STORYBOOK"))));
        for (String key : List.of("race", "background", "class")) {
            var field = draft.fields().stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
            assertEquals("RULEBOOK", field.sourceType());
            assertEquals(field.options().size(), field.optionDetails().size());
            assertTrue(field.optionDetails().stream().allMatch(detail -> !detail.evidence().isEmpty()));
        }
    }

    @Test
    void acceptsGroundedRulebookChoiceWhenModelUsesAnEquivalentFieldKey() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var search = mock(CharacterContextSearchPort.class);
        var tags = mock(CharacterInputTagExtractionPort.class);
        var scenarioPackage = withRulebookPackage();
        var rulebook = new KnowledgeDocumentId(rulebookDocumentId());
        when(packages.findById(scenarioPackage.packageId())).thenReturn(Optional.of(scenarioPackage));
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundleWithOnlyRulebook()));
        when(search.search(any())).thenReturn(List.of(new CharacterContextSearchPort.Evidence(
                rulebook, "RULEBOOK", 1, "page:8", "Choose a race. Elves are available.", .97)));
        when(tags.extract(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, CharacterInputTagExtractionPort.Request.class);
            return (request.instruction().isBlank() || request.instruction().startsWith("Discover source-grounded") || request.instruction().contains("'race'"))
                    ? List.of(rulebookChoice("race", "Race", com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, "Elf", rulebook))
                    : List.of();
        });

        var draft = new ScenarioPreparationApplicationService(packages, bundles, fixtureRuntimeOptions(), search, tags,
                new CharacterCreationBlueprintCompiler()).generateBlueprintDraft(scenarioPackage.packageId(), owner());

        var race = draft.fields().stream().filter(field -> field.key().equals("race")).findFirst().orElseThrow();
        assertEquals("RULEBOOK", race.sourceType());
        assertEquals(List.of("Elf"), race.options());
        assertEquals(1, race.optionDetails().size());
    }

    @Test
    void keepsOnlySystemAgnosticManualFieldsWhenSourcesHaveNoEvidence() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var search = mock(CharacterContextSearchPort.class);
        var tags = mock(CharacterInputTagExtractionPort.class);
        var scenarioPackage = withRulebookPackage();
        when(packages.findById(scenarioPackage.packageId())).thenReturn(Optional.of(scenarioPackage));
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundleWithOnlyRulebook()));
        var service = new ScenarioPreparationApplicationService(packages, bundles, fixtureRuntimeOptions(), search, tags,
                new CharacterCreationBlueprintCompiler());

        var draft = service.generateBlueprintDraft(scenarioPackage.packageId(), owner());

        for (String key : List.of("name", "race", "class", "level", "background",
                "starting_ability_scores.strength", "starting_ability_scores.charisma")) {
            var field = draft.fields().stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
            assertEquals("FREE_TEXT", field.inputMode());
            assertEquals("TEMPLATE", field.sourceType());
        }
        verify(search, atLeast(1)).search(any());
        verify(tags, never()).extract(any());
    }

    @Test
    void doesNotInjectEditionSpecificFieldsWithoutSourceEvidence() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var search = mock(CharacterContextSearchPort.class);
        var tags = mock(CharacterInputTagExtractionPort.class);
        var scenarioPackage = withRulebookPackage();
        when(packages.findById(scenarioPackage.packageId())).thenReturn(Optional.of(scenarioPackage));
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundleWithOnlyRulebook()));
        var service = new ScenarioPreparationApplicationService(packages, bundles, fixtureRuntimeOptions(), search, tags,
                new CharacterCreationBlueprintCompiler());

        var draft = service.generateBlueprintDraft(scenarioPackage.packageId(), owner(), "DND_4E");

        assertTrue(draft.fields().stream().anyMatch(field -> field.key().equals("name") && field.sourceType().equals("TEMPLATE")));
        assertTrue(draft.fields().stream().anyMatch(field -> field.key().equals("level") && field.sourceType().equals("TEMPLATE")));
        assertFalse(draft.fields().stream().anyMatch(field -> field.key().equals("powers") || field.key().equals("trainedSkills") || field.key().equals("ideals")));
        verify(search, atLeast(1)).search(any());
        verify(tags, never()).extract(any());
    }

    @Test
    void resolvingClassRetrievesItsStartingEquipmentFromTheRulebook() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var search = mock(CharacterContextSearchPort.class);
        var tags = mock(CharacterInputTagExtractionPort.class);
        var rulebook = new KnowledgeDocumentId(rulebookDocumentId());
        var evidence = new ScenarioSourceReference(rulebook, 1, "page:42");
        var blueprint = new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint(1,
                com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus.NEEDS_REVIEW, List.of(
                new com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint.Field("class", List.of("Fighter"), true,
                        "RULEBOOK", List.of(evidence), "EXTRACTED", List.of(), com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT,
                        List.of(), "Fighter", "Class", null, null, null, "HIGH")), List.of());
        var scenarioPackage = ScenarioPackage.publish(new ScenarioBundleId(bundleId()), 4, "fp-equipment",
                bundleWithOnlyRulebook().currentRevision().documents(), List.of(validUnit()),
                new ScenarioCompilationReport(ResolutionStatus.COMPLETE, List.of()),
                com.dndmaster.adventure.domain.scenario.CharacterLimit.defaultLimit(), blueprint);
        when(packages.findById(scenarioPackage.packageId())).thenReturn(Optional.of(scenarioPackage));
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundleWithOnlyRulebook()));
        when(search.search(any())).thenReturn(List.of(new CharacterContextSearchPort.Evidence(
                rulebook, "RULEBOOK", 1, "page:42", "Fighter starting equipment: chain mail or leather armor.", .97)));
        when(tags.extract(any())).thenReturn(List.of(rulebookChoice("class.startingEquipment", "Starting equipment",
                com.dndmaster.adventure.domain.scenario.InputMode.MULTI_SELECT, "Chain mail", rulebook)));

        var resolved = new ScenarioPreparationApplicationService(packages, bundles, fixtureRuntimeOptions(), search, tags,
                new CharacterCreationBlueprintCompiler()).resolveBlueprint(scenarioPackage.packageId(), owner(), "class", "Fighter");

        assertEquals("Fighter", resolved.field("class").value());
        assertEquals(List.of("Chain mail"), resolved.field("class.startingEquipment").options());
        assertEquals("RULEBOOK", resolved.field("class.startingEquipment").sourceType());
        assertEquals(2, resolved.revision());
        verify(search).search(any());
        verify(tags).extract(any());
        verify(packages).saveBlueprint(eq(scenarioPackage.packageId()), eq(resolved));
    }

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

    private static RuntimeOptionCatalogPort fixtureRuntimeOptions() {
        return new RuntimeOptionCatalogPort() {
            @Override public RuntimeOptionsView read(OwnerPlayerId ownerPlayerId) {
                return new RuntimeOptionsView("ollama", List.of(), List.of(), List.of());
            }
        };
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

    private static ScenarioSourceBundle bundleWithOnlyRulebook() {
        return ScenarioSourceBundle.create(
                new ScenarioBundleId(bundleId()),
                owner(),
                new ScenarioSourceBundleRevision(4, List.of(new ScenarioBundleDocumentSelection(
                        new KnowledgeDocumentId(rulebookDocumentId()),
                        ScenarioBundleDocumentRole.REFERENCE,
                        KnowledgeDocumentStatus.INDEXED,
                        "rules.pdf",
                        "RULEBOOK",
                        1))));
    }

    private static CharacterInputTagExtractionPort.CharacterInputTagCandidate rulebookChoice(
            String key, String label, com.dndmaster.adventure.domain.scenario.InputMode mode, String value,
            KnowledgeDocumentId rulebook) {
        ScenarioSourceReference evidence = new ScenarioSourceReference(rulebook, 1, "page:8");
        return new CharacterInputTagExtractionPort.CharacterInputTagCandidate(
                key, label, null, true, mode, List.of(value), List.of(), "HIGH", List.of(evidence),
                value + " is an available choice.", "RULEBOOK", List.of(
                        new CharacterInputTagExtractionPort.CharacterInputTagCandidate.OptionDetail(
                                value, value, "Rulebook choice for " + label + ".", value + " is an available choice.",
                                List.of(evidence))));
    }

    private static CharacterInputTagExtractionPort.CharacterInputTagCandidate candidate(
            String key, String label, com.dndmaster.adventure.domain.scenario.InputMode mode, List<String> options,
            KnowledgeDocumentId document, String sourceType, String quote) {
        ScenarioSourceReference evidence = new ScenarioSourceReference(document, 1, "page:8");
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate.OptionDetail> details = options.stream().map(option ->
                new CharacterInputTagExtractionPort.CharacterInputTagCandidate.OptionDetail(
                        option, option, "Available choice.", quote, List.of(evidence))).toList();
        return new CharacterInputTagExtractionPort.CharacterInputTagCandidate(key, label, null, true, mode, options,
                List.of(), "HIGH", List.of(evidence), quote, sourceType, details);
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
