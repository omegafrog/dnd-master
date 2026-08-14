package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionCatalogPort;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioPreparationApplicationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.BlueprintProvenance;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintRevisionConflictException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.StorybookProposalEvidenceRequiredException;
import com.dndmaster.adventure.domain.scenario.StorybookProposalNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StorybookProposalDecisionApplicationServiceTest {
    @Test
    void persists_use_decision_and_returns_the_updated_summary() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var scenarioPackage = packageWithEvidence();
        var latestBlueprint = new AtomicReference<>(scenarioPackage.characterCreationBlueprint());
        when(packages.findById(scenarioPackage.packageId())).thenAnswer(invocation -> Optional.of(ScenarioPackage.rehydrate(
                scenarioPackage.packageId(), scenarioPackage.bundleId(), scenarioPackage.bundleRevision(),
                scenarioPackage.inputFingerprint(), scenarioPackage.documents(), scenarioPackage.units(),
                scenarioPackage.report(), scenarioPackage.characterLimit(), latestBlueprint.get())));
        doAnswer(invocation -> {
            latestBlueprint.set(invocation.getArgument(1));
            return null;
        }).when(packages).saveBlueprint(eq(scenarioPackage.packageId()), any());
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundle(scenarioPackage)));
        var service = new ScenarioPreparationApplicationService(packages, bundles, runtimeOptions());
        String proposalId = service.read(scenarioPackage.packageId(), owner()).characterCreationBlueprint()
                .storybookProposals().get(0).proposalId();

        var result = service.useStorybookProposal(scenarioPackage.packageId(), owner(), 1, proposalId);

        assertEquals(2, result.revision());
        assertEquals(List.of(proposalId), result.appliedSettingsSummary().appliedProposalIds());
        assertEquals(0, result.appliedSettingsSummary().unresolvedProposalCount());
        var saved = ArgumentCaptor.forClass(CharacterCreationBlueprint.class);
        verify(packages).saveBlueprint(eq(scenarioPackage.packageId()), saved.capture());
        assertEquals("APPLIED", saved.getValue().proposalDecisions().get(0).state().name());
    }

    @Test
    void rejects_stale_revision_and_missing_evidence_before_persisting() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var scenarioPackage = packageWithEvidence();
        when(packages.findById(scenarioPackage.packageId())).thenReturn(Optional.of(scenarioPackage));
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundle(scenarioPackage)));
        var service = new ScenarioPreparationApplicationService(packages, bundles, runtimeOptions());
        String proposalId = service.read(scenarioPackage.packageId(), owner()).characterCreationBlueprint()
                .storybookProposals().get(0).proposalId();

        assertThrows(CharacterCreationBlueprintRevisionConflictException.class,
                () -> service.useStorybookProposal(scenarioPackage.packageId(), owner(), 2, proposalId));

        var noEvidence = packageWithoutEvidence();
        when(packages.findById(noEvidence.packageId())).thenReturn(Optional.of(noEvidence));
        when(bundles.findById(noEvidence.bundleId())).thenReturn(Optional.of(bundle(noEvidence)));
        String unresolvedId = service.read(noEvidence.packageId(), owner()).characterCreationBlueprint()
                .storybookProposals().get(0).proposalId();
        assertThrows(StorybookProposalEvidenceRequiredException.class,
                () -> service.useStorybookProposal(noEvidence.packageId(), owner(), 1, unresolvedId));

        assertThrows(StorybookProposalNotFoundException.class,
                () -> service.useStorybookProposal(scenarioPackage.packageId(), owner(), 1, "unknown-proposal"));
    }

    @Test
    void confirms_only_after_all_decisions_and_returns_applied_projection_summary() {
        var packages = mock(ScenarioPackageRepository.class);
        var bundles = mock(ScenarioBundleRepository.class);
        var scenarioPackage = packageWithEvidence();
        var latestBlueprint = new AtomicReference<>(scenarioPackage.characterCreationBlueprint());
        when(packages.findById(scenarioPackage.packageId())).thenAnswer(invocation -> Optional.of(ScenarioPackage.rehydrate(
                scenarioPackage.packageId(), scenarioPackage.bundleId(), scenarioPackage.bundleRevision(),
                scenarioPackage.inputFingerprint(), scenarioPackage.documents(), scenarioPackage.units(),
                scenarioPackage.report(), scenarioPackage.characterLimit(), latestBlueprint.get())));
        doAnswer(invocation -> {
            latestBlueprint.set(invocation.getArgument(1));
            return null;
        }).when(packages).saveBlueprint(eq(scenarioPackage.packageId()), any());
        when(bundles.findById(scenarioPackage.bundleId())).thenReturn(Optional.of(bundle(scenarioPackage)));
        var service = new ScenarioPreparationApplicationService(packages, bundles, runtimeOptions());
        String proposalId = service.read(scenarioPackage.packageId(), owner()).characterCreationBlueprint()
                .storybookProposals().get(0).proposalId();

        assertThrows(IllegalStateException.class,
                () -> service.publishBlueprint(scenarioPackage.packageId(), owner()));

        service.useStorybookProposal(scenarioPackage.packageId(), owner(), 1, proposalId);
        var result = service.publishBlueprint(scenarioPackage.packageId(), owner());

        assertEquals(3, result.publishedRevision());
        assertEquals(List.of(proposalId), result.appliedSettingsSummary().appliedProposalIds());
        assertEquals(0, result.appliedSettingsSummary().unresolvedProposalCount());
        var saved = ArgumentCaptor.forClass(CharacterCreationBlueprint.class);
        verify(packages, atLeastOnce()).saveBlueprint(eq(scenarioPackage.packageId()), saved.capture());
        assertEquals(List.of("race", "alignment"), saved.getValue().fields().stream()
                .map(CharacterCreationBlueprint.Field::key).toList());
    }

    private static ScenarioPackage packageWithEvidence() {
        var document = new KnowledgeDocumentId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        var field = new CharacterCreationBlueprint.Field("alignment", List.of("Lawful Good"), true, "STORYBOOK",
                List.of(new ScenarioSourceReference(document, 1, "page:4")), "EXTRACTED", List.of(),
                com.dndmaster.adventure.domain.scenario.InputMode.SINGLE_SELECT, List.of(), "Only elves.",
                "성향", null, "alignment-node", null, "HIGH");
        var base = new CharacterCreationBlueprint.Field("race", List.of("Elf"), true, "RULEBOOK",
                List.of(), "EXTRACTED", List.of());
        return ScenarioPackage.publish(new ScenarioBundleId(bundleId()), 1, "proposal-decision-evidence",
                List.of(documentSelection(document)), List.of(),
                new ScenarioCompilationReport(com.dndmaster.adventure.domain.scenario.ResolutionStatus.COMPLETE, List.of()),
                com.dndmaster.adventure.domain.scenario.CharacterLimit.defaultLimit(),
                new CharacterCreationBlueprint(1, CharacterCreationBlueprintStatus.NEEDS_REVIEW, List.of(base, field), List.of(),
                        new BlueprintProvenance(1, 1, List.of("RULEBOOK", "STORYBOOK"))));
    }

    private static ScenarioPackage packageWithoutEvidence() {
        var document = new KnowledgeDocumentId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        var field = new CharacterCreationBlueprint.Field("alignment", List.of("Lawful Good"), true, "STORYBOOK",
                List.of(), "EXTRACTED", List.of());
        var base = new CharacterCreationBlueprint.Field("race", List.of("Elf"), true, "RULEBOOK",
                List.of(), "EXTRACTED", List.of());
        return ScenarioPackage.publish(new ScenarioBundleId(bundleId()), 1, "proposal-decision-no-evidence",
                List.of(documentSelection(document)), List.of(),
                new ScenarioCompilationReport(com.dndmaster.adventure.domain.scenario.ResolutionStatus.COMPLETE, List.of()),
                com.dndmaster.adventure.domain.scenario.CharacterLimit.defaultLimit(),
                new CharacterCreationBlueprint(1, CharacterCreationBlueprintStatus.NEEDS_REVIEW, List.of(base, field), List.of(),
                        new BlueprintProvenance(1, 1, List.of("RULEBOOK", "STORYBOOK"))));
    }

    private static com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection documentSelection(KnowledgeDocumentId id) {
        return new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(id,
                com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAIN_SCENARIO,
                com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                "story.pdf", "STORYBOOK", 1);
    }

    private static com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle bundle(ScenarioPackage scenarioPackage) {
        return com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle.create(scenarioPackage.bundleId(), owner(),
                new com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision(1, scenarioPackage.documents()));
    }

    private static RuntimeOptionCatalogPort runtimeOptions() { return ignored -> new com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionsView("engine", List.of(), List.of(), List.of()); }
    private static com.dndmaster.adventure.domain.scenario.OwnerPlayerId owner() { return new com.dndmaster.adventure.domain.scenario.OwnerPlayerId(UUID.fromString("33333333-3333-3333-3333-333333333333")); }
    private static UUID bundleId() { return UUID.fromString("44444444-4444-4444-4444-444444444444"); }
}
