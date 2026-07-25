package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleNotFoundException;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ScenarioPreparationApplicationService {
    private final ScenarioPackageRepository packageRepository;
    private final ScenarioBundleRepository bundleRepository;
    private final RuntimeOptionCatalogPort runtimeOptionCatalog;

    public ScenarioPreparationApplicationService(
            ScenarioPackageRepository packageRepository,
            ScenarioBundleRepository bundleRepository,
            RuntimeOptionCatalogPort runtimeOptionCatalog) {
        this.packageRepository = Objects.requireNonNull(packageRepository, "package repository must not be null");
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.runtimeOptionCatalog = Objects.requireNonNull(runtimeOptionCatalog, "runtime option catalog must not be null");
    }

    public PlayPreparationView read(UUID scenarioPackageId, OwnerPlayerId ownerPlayerId) {
        ScenarioPackage scenarioPackage = packageRepository.findById(scenarioPackageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        ScenarioSourceBundleRevision currentRevision = bundle.currentRevision();

        List<String> blockers = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>(scenarioPackage.report().warnings());
        diagnostics.addAll(scenarioPackage.units().stream().flatMap(unit -> unit.validationMessages().stream()).toList());

        if (currentRevision.revision() != scenarioPackage.bundleRevision()) {
            blockers.add("번들 개정이 변경되었습니다.");
        }

        List<ScenarioBundleDocumentSelection> revisionDocuments = currentRevision.documents();
        List<ScenarioBundleDocumentSelection> rulebookDocuments = revisionDocuments.stream()
                .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType()))
                .toList();
        List<ScenarioBundleDocumentSelection> storybookDocuments = revisionDocuments.stream()
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                .toList();
        if (rulebookDocuments.isEmpty()) {
            blockers.add("RULEBOOK 문서가 없습니다.");
        }
        if (storybookDocuments.isEmpty()) {
            blockers.add("STORYBOOK 문서가 없습니다.");
        }
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE || scenarioPackage.runtimeCandidates().isEmpty()) {
            blockers.add("CharacterCreationBlueprint를 만들 수 없습니다.");
        }

        CharacterCreationBlueprintView blueprint = blockers.isEmpty()
                ? new CharacterCreationBlueprintView(
                        true,
                        "STORYBOOK " + storybookDocuments.size() + "개, RULEBOOK " + rulebookDocuments.size() + "개",
                        rulebookDocuments.size(),
                        storybookDocuments.size(),
                        diagnostics)
                : CharacterCreationBlueprintView.blocked(diagnostics);

        return new PlayPreparationView(
                scenarioPackage.packageId(),
                scenarioPackage.bundleId().value(),
                scenarioPackage.bundleRevision(),
                blockers.isEmpty() ? PlayPreparationStatus.READY : PlayPreparationStatus.BLOCKED,
                blockers,
                blueprint);
    }

    public RuntimeOptionsView runtimeOptions(OwnerPlayerId ownerPlayerId) {
        return runtimeOptionCatalog.read(ownerPlayerId);
    }
}
