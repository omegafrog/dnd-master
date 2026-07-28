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
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterInputNode;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintRevisionConflictException;
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
        List<ScenarioBundleDocumentSelection> storybookDocuments = revisionDocuments.stream()
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                .toList();
        boolean hasHandout = revisionDocuments.stream().anyMatch(document ->
                document.role() == ScenarioBundleDocumentRole.HANDOUT);
        if (storybookDocuments.isEmpty()) {
            blockers.add("STORYBOOK 문서가 없습니다.");
        }
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE || scenarioPackage.runtimeCandidates().isEmpty()) {
            blockers.add("CharacterCreationBlueprint를 만들 수 없습니다.");
        }
        CharacterCreationBlueprint compiledBlueprint = scenarioPackage.characterCreationBlueprint();
        if (compiledBlueprint != null && compiledBlueprint.status().name().equals("NEEDS_REVIEW")) {
            blockers.add("CharacterCreationBlueprint 검토가 필요합니다.");
        }

        CharacterCreationBlueprintView blueprint = compiledBlueprint != null
                ? toView(compiledBlueprint, revisionDocuments)
                : blockers.isEmpty() ? new CharacterCreationBlueprintView(
                        true,
                        "STORYBOOK " + storybookDocuments.size() + "개, RULEBOOK 런타임 세트 별도",
                        0,
                        storybookDocuments.size(),
                        diagnostics,
                        scenarioPackage.bundleRevision(),
                        blueprintFields(hasHandout), "READY", List.of())
                : CharacterCreationBlueprintView.blocked(diagnostics);

        return new PlayPreparationView(
                scenarioPackage.packageId(),
                scenarioPackage.bundleId().value(),
                scenarioPackage.bundleRevision(),
                blockers.isEmpty() ? PlayPreparationStatus.READY : PlayPreparationStatus.BLOCKED,
                blockers,
                blueprint,
                CharacterLimitView.from(scenarioPackage.characterLimit()));
    }

    public RuntimeOptionsView runtimeOptions(OwnerPlayerId ownerPlayerId) {
        return runtimeOptionCatalog.read(ownerPlayerId);
    }

    public CharacterCreationBlueprint resolveBlueprint(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                       String fieldKey, String value) {
        return resolveBlueprint(packageId, ownerPlayerId, 0, fieldKey, value);
    }

    public CharacterCreationBlueprint resolveBlueprint(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                       long expectedRevision, String fieldKey, String value) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        CharacterCreationBlueprint blueprint = requireBlueprint(scenarioPackage);
        requireBlueprintRevision(blueprint, expectedRevision);
        CharacterCreationBlueprint resolved = blueprint.resolve(fieldKey, value);
        packageRepository.saveBlueprint(packageId, resolved);
        return resolved;
    }

    public CharacterCreationBlueprint addBlueprintChild(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                         long expectedRevision, String parentId, String key, String label) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        CharacterCreationBlueprint blueprint = requireBlueprint(scenarioPackage);
        requireBlueprintRevision(blueprint, expectedRevision);
        CharacterCreationBlueprint updated = blueprint.addUserInputChild(parentId, key, label);
        packageRepository.saveBlueprint(packageId, updated);
        return updated;
    }

    public CharacterCreationBlueprint publishBlueprint(UUID packageId, OwnerPlayerId ownerPlayerId) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE || scenarioPackage.runtimeCandidates().isEmpty()) {
            throw new IllegalStateException("scenario package is not ready for blueprint publication");
        }
        CharacterCreationBlueprint published = requireBlueprint(scenarioPackage).publish();
        packageRepository.saveBlueprint(packageId, published);
        return published;
    }

    private static CharacterCreationBlueprint requireBlueprint(ScenarioPackage scenarioPackage) {
        if (scenarioPackage.characterCreationBlueprint() == null) {
            throw new IllegalStateException("character creation blueprint is unavailable");
        }
        return scenarioPackage.characterCreationBlueprint();
    }

    private static void requireCurrentBundleRevision(ScenarioPackage scenarioPackage, ScenarioSourceBundle bundle) {
        if (bundle.currentRevision().revision() != scenarioPackage.bundleRevision()) {
            throw new IllegalStateException("scenario package is stale for current bundle revision");
        }
    }

    private static void requireBlueprintRevision(CharacterCreationBlueprint blueprint, long expectedRevision) {
        if (expectedRevision > 0 && blueprint.revision() != expectedRevision) {
            throw new CharacterCreationBlueprintRevisionConflictException(expectedRevision, blueprint.revision());
        }
    }

    private static List<CharacterCreationBlueprintView.FieldView> blueprintFields(boolean hasHandout) {
        String source = hasHandout ? "HANDOUT" : "RULEBOOK";
        return List.of("name", "race", "class", "background", "starting_ability_scores", "level").stream()
                .map(key -> new CharacterCreationBlueprintView.FieldView(
                        key, List.of(), true, source, "MANUAL_INPUT_REQUIRED", List.of("extraction pending")))
                .toList();
    }

    private static CharacterCreationBlueprintView toView(CharacterCreationBlueprint blueprint,
                                                          List<ScenarioBundleDocumentSelection> documents) {
        long rulebooks = documents.stream().filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType())).count();
        long storybooks = documents.stream().filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType())).count();
        return new CharacterCreationBlueprintView(
                blueprint.status().name().equals("READY") || blueprint.status().name().equals("PUBLISHED"),
                "CharacterCreationBlueprint revision " + blueprint.revision(), (int) rulebooks, (int) storybooks,
                blueprint.diagnostics(), blueprint.revision(), blueprint.fields().stream()
                        .map(field -> new CharacterCreationBlueprintView.FieldView(field.key(), field.options(), field.required(),
                                field.sourceType(), field.inputStatus(), field.diagnostics(), field.inputMode().name(),
                                field.suggestions(), field.sourceQuote(), field.evidence().stream()
                                        .map(reference -> new CharacterCreationBlueprintView.FieldView.SourceReferenceView(
                                                reference.knowledgeDocumentId().value().toString(), reference.extractionVersion(), reference.locator()))
                                        .toList())).toList(), blueprint.status().name(),
                blueprint.roots().stream().map(ScenarioPreparationApplicationService::toNodeView).toList());
    }

    private static CharacterCreationBlueprintView.NodeView toNodeView(CharacterInputNode node) {
        return new CharacterCreationBlueprintView.NodeView(node.id(), node.parentId(), node.key(), node.label(),
                node.inputMode().name(), node.value(), node.options(), node.suggestions(), node.status().name(),
                node.allowUserAddChild(), node.confidence(), node.sourceQuote(), node.diagnostics(),
                node.sourceEvidence().stream().map(reference -> new CharacterCreationBlueprintView.FieldView.SourceReferenceView(
                        reference.knowledgeDocumentId().value().toString(), reference.extractionVersion(), reference.locator())).toList(),
                node.children().stream().map(ScenarioPreparationApplicationService::toNodeView).toList());
    }
}
