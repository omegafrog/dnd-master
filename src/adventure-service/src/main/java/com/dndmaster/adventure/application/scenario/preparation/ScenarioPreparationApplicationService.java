package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler;
import com.dndmaster.adventure.application.scenario.blueprint.DndCharacterCreationTemplate;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleNotFoundException;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.RulebookEdition;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.CharacterInputNode;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintRevisionConflictException;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintPublicationBlockedException;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.domain.scenario.BlueprintProvenance;
import com.dndmaster.adventure.domain.scenario.ProposalDecisionState;
import com.dndmaster.adventure.domain.scenario.StorybookProposalDecision;
import com.dndmaster.adventure.domain.scenario.StorybookProposalNotFoundException;
import com.dndmaster.adventure.application.runtime.GameSystemDefinitionPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScenarioPreparationApplicationService {
    private static final List<CharacterFieldSpec> CHARACTER_FIELD_SPECS = List.of(
            new CharacterFieldSpec("race", "종족",
                    "D&D 캐릭터 생성에서 플레이어가 최초로 선택하는 기본 종족 목록.",
                    "Return exactly one field with key 'race' and label '종족'. Extract only first-choice base races. Exclude subraces, human ethnicities, regional origins, headings, and book titles."),
            new CharacterFieldSpec("class", "직업",
                    "D&D 캐릭터 생성에서 플레이어가 선택하는 기본 직업 목록.",
                    "Return exactly one field with key 'class' and label '직업'. Extract only base classes. Exclude subclasses, class features, headings, and book titles."),
            new CharacterFieldSpec("background", "배경",
                    "D&D 캐릭터 생성에서 플레이어가 선택하는 배경 목록.",
                    "Return exactly one field with key 'background' and label '배경'. Extract only selectable backgrounds. Exclude personality tables, headings, and book titles."));
    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioPreparationApplicationService.class);
    private final ScenarioPackageRepository packageRepository;
    private final ScenarioBundleRepository bundleRepository;
    private final RuntimeOptionCatalogPort runtimeOptionCatalog;
    private final CharacterContextSearchPort characterContextSearch;
    private final CharacterInputTagExtractionPort characterTagExtraction;
    private final CharacterCreationBlueprintCompiler blueprintCompiler;
    private final GameSystemDefinitionPort gameSystemDefinitionPort;
    private final KnowledgeDocumentLookupPort knowledgeDocumentLookupPort;
    private final com.dndmaster.adventure.application.knowledge.RulebookActionDefinitionLookupPort actionDefinitionLookup;

    public ScenarioPreparationApplicationService(
            ScenarioPackageRepository packageRepository,
            ScenarioBundleRepository bundleRepository,
            RuntimeOptionCatalogPort runtimeOptionCatalog) {
        this(packageRepository, bundleRepository, runtimeOptionCatalog,
                request -> List.of(), request -> List.of(), new CharacterCreationBlueprintCompiler());
    }

    public ScenarioPreparationApplicationService(
            ScenarioPackageRepository packageRepository,
            ScenarioBundleRepository bundleRepository,
            RuntimeOptionCatalogPort runtimeOptionCatalog,
            CharacterContextSearchPort characterContextSearch,
            CharacterInputTagExtractionPort characterTagExtraction,
            CharacterCreationBlueprintCompiler blueprintCompiler) {
        this(packageRepository, bundleRepository, runtimeOptionCatalog, characterContextSearch, characterTagExtraction,
                blueprintCompiler, rulebookId -> java.util.Optional.empty());
    }

    public ScenarioPreparationApplicationService(
            ScenarioPackageRepository packageRepository, ScenarioBundleRepository bundleRepository,
            RuntimeOptionCatalogPort runtimeOptionCatalog, CharacterContextSearchPort characterContextSearch,
            CharacterInputTagExtractionPort characterTagExtraction, CharacterCreationBlueprintCompiler blueprintCompiler,
            GameSystemDefinitionPort gameSystemDefinitionPort) {
        this(packageRepository, bundleRepository, runtimeOptionCatalog, characterContextSearch, characterTagExtraction,
                blueprintCompiler, gameSystemDefinitionPort, emptyKnowledgeDocumentLookup());
    }

    public ScenarioPreparationApplicationService(
            ScenarioPackageRepository packageRepository, ScenarioBundleRepository bundleRepository,
            RuntimeOptionCatalogPort runtimeOptionCatalog, CharacterContextSearchPort characterContextSearch,
            CharacterInputTagExtractionPort characterTagExtraction, CharacterCreationBlueprintCompiler blueprintCompiler,
            GameSystemDefinitionPort gameSystemDefinitionPort, KnowledgeDocumentLookupPort knowledgeDocumentLookupPort) {
        this(packageRepository, bundleRepository, runtimeOptionCatalog, characterContextSearch, characterTagExtraction,
                blueprintCompiler, gameSystemDefinitionPort, knowledgeDocumentLookupPort, (documentId, version) -> List.of());
    }

    public ScenarioPreparationApplicationService(
            ScenarioPackageRepository packageRepository, ScenarioBundleRepository bundleRepository,
            RuntimeOptionCatalogPort runtimeOptionCatalog, CharacterContextSearchPort characterContextSearch,
            CharacterInputTagExtractionPort characterTagExtraction, CharacterCreationBlueprintCompiler blueprintCompiler,
            GameSystemDefinitionPort gameSystemDefinitionPort, KnowledgeDocumentLookupPort knowledgeDocumentLookupPort,
            com.dndmaster.adventure.application.knowledge.RulebookActionDefinitionLookupPort actionDefinitionLookup) {
        this.packageRepository = Objects.requireNonNull(packageRepository, "package repository must not be null");
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.runtimeOptionCatalog = Objects.requireNonNull(runtimeOptionCatalog, "runtime option catalog must not be null");
        this.characterContextSearch = Objects.requireNonNull(characterContextSearch, "character context search must not be null");
        this.characterTagExtraction = Objects.requireNonNull(characterTagExtraction, "character tag extraction must not be null");
        this.blueprintCompiler = Objects.requireNonNull(blueprintCompiler, "blueprint compiler must not be null");
        this.gameSystemDefinitionPort = Objects.requireNonNull(gameSystemDefinitionPort, "game system definition port must not be null");
        this.knowledgeDocumentLookupPort = Objects.requireNonNull(knowledgeDocumentLookupPort, "knowledge document lookup must not be null");
        this.actionDefinitionLookup = Objects.requireNonNull(actionDefinitionLookup, "action definition lookup must not be null");
    }

    public PlayPreparationView read(UUID scenarioPackageId, OwnerPlayerId ownerPlayerId) {
        ScenarioPackage scenarioPackage = packageRepository.findById(scenarioPackageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        List<String> blockers = new ArrayList<>();
        List<ScenarioBundleDocumentSelection> basicRulesSources = scenarioPackage.documents().stream()
                .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType()))
                .filter(document -> document.status() == KnowledgeDocumentStatus.INDEXED
                        || document.status() == KnowledgeDocumentStatus.INDEXED_WITH_WARNINGS)
                .toList();
        var provenance = scenarioPackage.characterCreationBlueprint() == null
                ? com.dndmaster.adventure.domain.scenario.BlueprintProvenance.empty()
                : scenarioPackage.characterCreationBlueprint().provenance();
        if (provenance.selectedRulebookId() != null) {
            basicRulesSources = List.of(new ScenarioBundleDocumentSelection(
                    new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(provenance.selectedRulebookId()),
                    ScenarioBundleDocumentRole.REFERENCE, KnowledgeDocumentStatus.INDEXED,
                    "shared-catalog-rulebook", "RULEBOOK", provenance.selectedRulebookExtractionVersion()));
        }
        List<StructuredSpellDefinition> spellDefinitions = scenarioPackage.spellDefinitions();
        List<StructuredSpellDefinition> sourceSpellDefinitions = List.of();
        if (basicRulesSources.size() != 1) {
            spellDefinitions = List.of();
            if (!basicRulesSources.isEmpty()) blockers.add("선택된 기본 룰북의 주문·행동 정의 출처가 하나로 확정되지 않았습니다.");
        } else {
            var source = basicRulesSources.getFirst();
            boolean sourceMatches = hasApprovedRulebookSource(source);
            var existingById = new LinkedHashMap<String, StructuredSpellDefinition>();
            spellDefinitions.forEach(spell -> existingById.putIfAbsent(spell.id(), spell));
            try {
                sourceSpellDefinitions = actionDefinitionLookup.find(source.knowledgeDocumentId(), source.extractionVersion());
            } catch (RuntimeException extractionUnavailable) {
                blockers.add("선택된 기본 룰북의 주문·행동 정의 추출이 완료되지 않았습니다.");
            }
            if (sourceMatches && !sourceSpellDefinitions.isEmpty()) {
                var completeDefinitions = sourceSpellDefinitions.stream()
                        .map(spell -> {
                            StructuredSpellDefinition existing = existingById.get(spell.id());
                            boolean sameSource = existing != null
                                    && existing.sourceDocumentId().equals(spell.sourceDocumentId())
                                    && existing.extractionVersion() == spell.extractionVersion()
                                    && existing.sourceVersion().equals(spell.sourceVersion());
                            return (sameSource ? existing : spell).withSourceLocator(spell.sourceLocator());
                        })
                        .toList();
                spellDefinitions = completeDefinitions;
            } else if (sourceMatches) {
                spellDefinitions = List.of(); // A completed source with zero definitions is valid.
            } else {
                blockers.add("선택된 기본 룰북 주문·행동 정의 출처 버전이 게시된 자료와 일치하지 않습니다.");
                spellDefinitions = List.of();
            }
        }
        ScenarioSourceBundleRevision currentRevision = bundle.currentRevision();

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
        if (scenarioPackage.report().status() == ResolutionStatus.INVALID
                || (scenarioPackage.characterCreationBlueprint() == null
                && scenarioPackage.runtimeCandidates().isEmpty())) {
            blockers.add("CharacterCreationBlueprint를 만들 수 없습니다.");
        }
        CharacterCreationBlueprint compiledBlueprint = scenarioPackage.characterCreationBlueprint();
        CharacterCreationBlueprintView blueprint = compiledBlueprint != null
                ? toView(compiledBlueprint, revisionDocuments)
                : blockers.isEmpty() ? new CharacterCreationBlueprintView(
                        true,
                        "STORYBOOK " + storybookDocuments.size() + "개, RULEBOOK 런타임 세트 별도",
                        0,
                        storybookDocuments.size(),
                        diagnostics,
                        scenarioPackage.bundleRevision(),
                        blueprintFields(hasHandout), "READY", List.of(), "DND_5E_2014",
                        new CharacterCreationBlueprintView.RulebookBaseSchemaView("DND_5E_2014", blueprintFields(hasHandout)),
                        List.of(), CharacterCreationBlueprintView.StorybookExtractionState.NO_PROPOSALS)
                : CharacterCreationBlueprintView.blocked(diagnostics, storybookExtractionState(storybookDocuments, List.of()));

        return new PlayPreparationView(
                null,
                scenarioPackage.packageId(),
                scenarioPackage.bundleId().value(),
                scenarioPackage.bundleRevision(),
                blockers.isEmpty() ? PlayPreparationStatus.READY : PlayPreparationStatus.BLOCKED,
                blockers,
                blueprint,
                CharacterLimitView.from(scenarioPackage.characterLimit()),
                spellDefinitions,
                sourceSpellDefinitions,
                packageRepository.spellDefinitionRevision(scenarioPackageId),
                packageRepository.spellDefinitionHistory(scenarioPackageId),
                scenarioPackage.documents().stream().filter(document -> isSearchable(document.status()))
                        .map(document -> new PreparationSourceView(document.knowledgeDocumentId().value(), document.originalFilename(), document.extractionVersion())).toList());
    }

    public PlayPreparationView createPlayPreparation(UUID packageId, OwnerPlayerId owner, String requestKey) {
        if (requestKey == null || requestKey.isBlank()) throw new IllegalArgumentException("preparation request key is required");
        PlayPreparationView base = read(packageId, owner);
        if (base.blockers().stream().anyMatch(blocker -> blocker.contains("주문·행동 정의")))
            throw new IllegalStateException("selected rulebook action definition extraction and source verification must complete before review");
        var created = packageRepository.createSpellDefinitionPreparation(packageId, owner.value(), requestKey,
                base.spellDefinitions());
        return readPlayPreparation(created.preparationId(), owner);
    }

    public PlayPreparationView readPlayPreparation(UUID preparationId, OwnerPlayerId owner) {
        var review = packageRepository.findSpellDefinitionPreparation(preparationId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        if (!review.ownerId().equals(owner.value())) throw new com.dndmaster.adventure.domain.scenario.ScenarioBundleAccessDeniedException();
        PlayPreparationView base = read(review.packageId(), owner);
        return new PlayPreparationView(preparationId, base.scenarioPackageId(), base.bundleId(), base.bundleRevision(),
                base.status(), base.blockers(), base.characterCreationBlueprint(), base.characterLimit(),
                review.definitions(), base.sourceSpellDefinitions(), review.revision(),
                packageRepository.spellDefinitionPreparationHistory(preparationId), base.packageSources());
    }

    public PlayPreparationView editSpellDefinition(UUID preparationId, OwnerPlayerId owner, String definitionId,
            SpellDefinitionEditRequest edit) {
        Objects.requireNonNull(edit, "spell definition edit must not be null");
        return changeSpellDefinition(preparationId, owner, definitionId, edit.expectedRevision(), current -> {
            StructuredSpellDefinition edited = current.edit(edit.level(), edit.castingTime(), edit.rangeArea(), edit.components(), edit.duration(),
                    edit.school(), edit.attackSave(), edit.damageEffect());
            return edited.reviewStructure(edit.required() == null ? current.required() : edit.required(),
                    edit.actionKind() == null ? current.actionKind() : edit.actionKind(),
                    edit.rangeProfile() == null ? current.rangeProfile() : edit.rangeProfile(),
                    edit.targetingProfile() == null ? current.targetingProfile() : edit.targetingProfile(),
                    edit.active() == null ? current.active() : edit.active());
        });
    }

    public PlayPreparationView approveSpellDefinition(UUID preparationId, OwnerPlayerId owner, String definitionId,
            long expectedRevision) {
        return changeSpellDefinition(preparationId, owner, definitionId, expectedRevision, StructuredSpellDefinition::approve);
    }

    public PlayPreparationView addActionDefinition(UUID preparationId, OwnerPlayerId owner, AddActionDefinitionRequest request) {
        long startedAt = System.nanoTime();
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_flow operation=spell_action_definition_add outcome=started preparationId={} expectedRevision={} sourceDocumentId={} expected=package_indexed_source actual=not_checked",
                    preparationId, request.expectedRevision(), request.sourceDocumentId());
        }
        var review = packageRepository.findSpellDefinitionPreparation(preparationId).orElseThrow(ScenarioBundleNotFoundException::new);
        requirePreparationOwner(review.ownerId(), owner);
        ScenarioPackage scenarioPackage = packageRepository.findById(review.packageId()).orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId()).orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        var source = scenarioPackage.documents().stream()
                .filter(document -> document.knowledgeDocumentId().value().equals(request.sourceDocumentId()))
                .findFirst().orElse(null);
        if (source == null || !isSearchable(source.status())) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=spell_action_definition_add outcome=blocked preparationId={} expected=package_indexed_source actualDocumentCount={} actualIndexedSourceCount={} reason={}",
                        preparationId, scenarioPackage.documents().size(),
                        scenarioPackage.documents().stream().filter(document -> isSearchable(document.status())).count(),
                        source == null ? "source_not_in_package" : "source_not_indexed");
            }
            throw new IllegalArgumentException("source document must be indexed and part of this package");
        }
        if (review.revision() != request.expectedRevision()) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=spell_action_definition_add outcome=conflict preparationId={} expectedRevision={} actualRevision={} reason=stale_revision",
                        preparationId, request.expectedRevision(), review.revision());
            }
            throw new com.dndmaster.adventure.domain.scenario.SpellDefinitionRevisionConflictException(request.expectedRevision(), review.revision());
        }
        String id = "user-action-" + UUID.randomUUID();
        String rangeArea = request.rangeArea() == null || request.rangeArea().isBlank()
                ? formatRange(request.rangeProfile()) : request.rangeArea();
        String sourceVersion = "package-document:" + source.knowledgeDocumentId().value() + ":" + source.extractionVersion();
        var definition = new StructuredSpellDefinition(id, request.name(), source.knowledgeDocumentId().value(),
                request.sourceLocator(), sourceVersion, source.extractionVersion(), request.level(), request.castingTime(),
                rangeArea, request.components(), request.duration(), request.school(), request.attackSave(),
                request.damageEffect(), List.of(368), "사용자가 패키지 문서의 출처 위치를 확인해 추가함", false,
                StructuredSpellDefinition.ReviewStatus.PENDING, request.required(), request.actionKind(),
                request.rangeProfile(), request.targetingProfile(), true);
        List<StructuredSpellDefinition> definitions = new ArrayList<>(review.definitions());
        definitions.add(definition);
        var history = new com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory(request.expectedRevision() + 1,
                id, owner.value(), java.time.Instant.now(), definition, definition);
        packageRepository.updateSpellDefinitionPreparation(preparationId, request.expectedRevision(), definitions, history);
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_flow operation=spell_action_definition_add outcome=completed preparationId={} definitionId={} expectedRevision={} actualRevision={} actualDefinitionCount={} durationMs={}",
                    preparationId, id, request.expectedRevision(), request.expectedRevision() + 1, definitions.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
        }
        return readPlayPreparation(preparationId, owner);
    }

    public PlayPreparationView removeUserAddedActionDefinition(UUID preparationId, OwnerPlayerId owner,
            String definitionId, long expectedRevision) {
        long startedAt = System.nanoTime();
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_flow operation=spell_action_definition_remove outcome=started preparationId={} definitionId={} expectedRevision={} expected=user_added actual=not_checked",
                    preparationId, definitionId, expectedRevision);
        }
        var review = packageRepository.findSpellDefinitionPreparation(preparationId).orElseThrow(ScenarioBundleNotFoundException::new);
        requirePreparationOwner(review.ownerId(), owner);
        ScenarioPackage scenarioPackage = packageRepository.findById(review.packageId()).orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId()).orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        if (expectedRevision != review.revision()) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=spell_action_definition_remove outcome=conflict preparationId={} definitionId={} expectedRevision={} actualRevision={} reason=stale_revision",
                        preparationId, definitionId, expectedRevision, review.revision());
            }
            throw new com.dndmaster.adventure.domain.scenario.SpellDefinitionRevisionConflictException(expectedRevision, review.revision());
        }
        StructuredSpellDefinition previous = review.definitions().stream().filter(item -> item.id().equals(definitionId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("action definition not found"));
        if (!previous.userAdded()) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=spell_action_definition_remove outcome=blocked preparationId={} definitionId={} expected=user_added actual=source_definition reason=source_definition_immutable",
                        preparationId, definitionId);
            }
            throw new IllegalArgumentException("source action definitions can only be deactivated");
        }
        var removed = previous.reviewStructure(previous.required(), previous.actionKind(), previous.rangeProfile(), previous.targetingProfile(), false);
        var history = new com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory(expectedRevision + 1,
                definitionId, owner.value(), java.time.Instant.now(), previous, removed);
        packageRepository.updateSpellDefinitionPreparation(preparationId, expectedRevision,
                review.definitions().stream().filter(item -> !item.id().equals(definitionId)).toList(), history);
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            LOGGER.info("dev_flow operation=spell_action_definition_remove outcome=completed preparationId={} definitionId={} expectedRevision={} actualRevision={} actualDefinitionCount={} durationMs={}",
                    preparationId, definitionId, expectedRevision, expectedRevision + 1, review.definitions().size() - 1,
                    (System.nanoTime() - startedAt) / 1_000_000);
        }
        return readPlayPreparation(preparationId, owner);
    }

    private static void requirePreparationOwner(UUID reviewOwnerId, OwnerPlayerId owner) {
        if (!reviewOwnerId.equals(owner.value())) throw new com.dndmaster.adventure.domain.scenario.ScenarioBundleAccessDeniedException();
    }

    private static String formatRange(StructuredSpellDefinition.RangeProfile range) {
        if (range.form() == StructuredSpellDefinition.RangeForm.SINGLE) return range.normalRangeFeet() + " ft.";
        if (range.form() == StructuredSpellDefinition.RangeForm.NORMAL_AND_LONG) {
            return range.normalRangeFeet() + "/" + range.longRangeFeet() + " ft.";
        }
        return "사거리 지원 미확인";
    }

    private PlayPreparationView changeSpellDefinition(UUID preparationId, OwnerPlayerId owner, String definitionId,
            long expectedRevision, java.util.function.UnaryOperator<StructuredSpellDefinition> change) {
        String context = "preparationId=" + preparationId + " definitionId=" + definitionId + " expectedRevision=" + expectedRevision;
        return com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.measure(LOGGER,
                "spell_definition_review", context, () -> changeSpellDefinitionInternal(preparationId, owner,
                        definitionId, expectedRevision, change));
    }

    private PlayPreparationView changeSpellDefinitionInternal(UUID preparationId, OwnerPlayerId owner, String definitionId,
            long expectedRevision, java.util.function.UnaryOperator<StructuredSpellDefinition> change) {
        if (expectedRevision < 0 || definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("spell definition revision and identifier are required");
        }
        var review = packageRepository.findSpellDefinitionPreparation(preparationId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        if (!review.ownerId().equals(owner.value())) throw new com.dndmaster.adventure.domain.scenario.ScenarioBundleAccessDeniedException();
        PlayPreparationView current = readPlayPreparation(preparationId, owner);
        if (current.spellDefinitions().isEmpty()) throw new IllegalStateException("spell definitions are unavailable for review");
        if (current.spellDefinitionRevision() != expectedRevision) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=spell_definition_review outcome=conflict packageId={} definitionId={} expectedRevision={} actualRevision={}",
                        preparationId, definitionId, expectedRevision, current.spellDefinitionRevision());
            }
            throw new com.dndmaster.adventure.domain.scenario.SpellDefinitionRevisionConflictException(
                    expectedRevision, current.spellDefinitionRevision());
        }
        ScenarioPackage scenarioPackage = packageRepository.findById(review.packageId()).orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId()).orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(owner);
        long actualBundleRevision = bundle.currentRevision().revision();
        if (actualBundleRevision != scenarioPackage.bundleRevision()) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=spell_definition_review outcome=conflict preparationId={} packageId={} expectedBundleRevision={} actualBundleRevision={} reason=stale_bundle",
                        preparationId, review.packageId(), scenarioPackage.bundleRevision(), actualBundleRevision);
            }
            throw new IllegalStateException("scenario package bundle revision is stale");
        }
        List<StructuredSpellDefinition> definitions = current.spellDefinitions();
        StructuredSpellDefinition previous = definitions.stream().filter(item -> item.id().equals(definitionId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("spell definition not found"));
        StructuredSpellDefinition next = change.apply(previous);
        List<StructuredSpellDefinition> updated = definitions.stream()
                .map(item -> item.id().equals(definitionId) ? next : item).toList();
        var history = new com.dndmaster.adventure.domain.scenario.SpellDefinitionEditHistory(expectedRevision + 1,
                definitionId, owner.value(), java.time.Instant.now(), previous, next);
        try {
            packageRepository.updateSpellDefinitionPreparation(preparationId, expectedRevision, updated, history);
        } catch (com.dndmaster.adventure.domain.scenario.SpellDefinitionRevisionConflictException conflict) {
            if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                LOGGER.info("dev_flow operation=spell_definition_review outcome=conflict packageId={} definitionId={} expectedRevision={} actualRevision={}",
                        preparationId, definitionId, conflict.expectedRevision(), conflict.actualRevision());
            }
            throw conflict;
        }
        return readPlayPreparation(preparationId, owner);
    }

    private boolean hasApprovedRulebookSource(ScenarioBundleDocumentSelection source) {
        var documents = knowledgeDocumentLookupPort.findPublishedSharedCatalogDocuments();
        return documents.stream().anyMatch(document -> document.knowledgeDocumentId().equals(source.knowledgeDocumentId())
                && isSearchable(document.status())
                && "RULEBOOK".equalsIgnoreCase(document.documentType())
                && document.extractionVersion() == source.extractionVersion());
    }

    private static KnowledgeDocumentLookupPort emptyKnowledgeDocumentLookup() {
        return ownerId -> List.of();
    }

    public RuntimeOptionsView runtimeOptions(OwnerPlayerId ownerPlayerId) {
        return runtimeOptionCatalog.read(ownerPlayerId);
    }

    public CharacterCreationBlueprintView generateBlueprintDraft(UUID packageId, OwnerPlayerId ownerPlayerId) {
        return generateBlueprintDraft(packageId, ownerPlayerId, "DND_5E");
    }

    public CharacterCreationBlueprintView generateBlueprintDraft(UUID packageId, OwnerPlayerId ownerPlayerId, String edition) {
        return generateBlueprintDraft(packageId, ownerPlayerId, edition, null, 0);
    }

    /**
     * Builds a creation schema from a shared catalog rulebook plus the package's storybooks.
     * Catalog rulebooks deliberately do not become bundle documents: they are global sources
     * selected at setup time, while storybooks remain private bundle content.
     */
    public CharacterCreationBlueprintView generateBlueprintDraft(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                                  String edition, UUID catalogRulebookId,
                                                                  long catalogExtractionVersion) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        if ("DND_5E_2024".equalsIgnoreCase(edition)) {
            return new CharacterCreationBlueprintView(false, "D&D 5.5e (2024) rulebook contract unavailable",
                    0, 0, List.of("DND_5E_2024 rulebook contract is not published"), 0, List.of(), "UNAVAILABLE", List.of(),
                    "DND_5E_2024");
        }
        List<ScenarioBundleDocumentSelection> storybooks = bundle.currentRevision().documents().stream()
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                .toList();
        List<ScenarioBundleDocumentSelection> rulebooks = bundle.currentRevision().documents().stream()
                .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType()))
                .toList();
        if (catalogRulebookId != null) {
            if (catalogExtractionVersion <= 0) {
                throw new IllegalArgumentException("catalog rulebook extraction version must be positive");
            }
            rulebooks = List.of(new ScenarioBundleDocumentSelection(
                    new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(catalogRulebookId),
                    ScenarioBundleDocumentRole.REFERENCE,
                    com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                    "shared-catalog-rulebook", "RULEBOOK", catalogExtractionVersion));
        }

        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates = new ArrayList<>();
        // RULEBOOK supplies base-sheet choices. STORYBOOK independently supplies campaign additions
        // and restrictions, so a long adventure text cannot hide rulebook choices or be ignored.
        candidates.addAll(discoverCharacterFields(packageId, ownerPlayerId, rulebooks));
        candidates.addAll(discoverStorybookFields(packageId, ownerPlayerId, storybooks));
        List<ScenarioBundleDocumentSelection> sourceDocuments = new ArrayList<>(rulebooks);
        sourceDocuments.addAll(storybooks);
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> directInputs = candidates.stream()
                .filter(candidate -> candidate.inputMode() == com.dndmaster.adventure.domain.scenario.InputMode.FREE_TEXT
                        && candidate.options().isEmpty()).toList();
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> refinedChoices = refineChoiceFields(
                packageId, ownerPlayerId, sourceDocuments, candidates.stream()
                        .filter(candidate -> candidate.inputMode() != com.dndmaster.adventure.domain.scenario.InputMode.FREE_TEXT
                                || !candidate.options().isEmpty()).toList());
        candidates = new ArrayList<>(directInputs);
        candidates.addAll(refinedChoices);
        long nextBlueprintRevision = scenarioPackage.characterCreationBlueprint() == null
                ? 1 : scenarioPackage.characterCreationBlueprint().revision() + 1;
        CharacterCreationBlueprint blueprint = DndCharacterCreationTemplate.apply(edition,
                blueprintCompiler.compileAgent(nextBlueprintRevision, candidates));
        blueprint = normalizeSystemAgnosticManualFields(blueprint, edition, candidates);
        blueprint = restoreGroundedCandidates(blueprint, edition, candidates);
        long definitionVersion = rulebooks.stream().map(document -> gameSystemDefinitionPort.findByRulebook(document.knowledgeDocumentId().value()))
                .flatMap(java.util.Optional::stream).map(GameSystemDefinitionPort.Definition::version).findFirst().orElse(0L);
        if (definitionVersion < 1) {
            throw new IllegalStateException("published game system definition is required before character blueprint generation");
        }
        ScenarioBundleDocumentSelection selectedRulebook = catalogRulebookId != null ? rulebooks.getFirst()
                : rulebooks.size() == 1 ? rulebooks.getFirst() : null;
        blueprint = blueprint.withProvenance(new BlueprintProvenance(definitionVersion, bundle.currentRevision().revision(),
                sourceDocuments.stream().map(document -> document.documentType().toUpperCase()).distinct().toList(), edition,
                selectedRulebook == null ? null : selectedRulebook.knowledgeDocumentId().value(),
                selectedRulebook == null ? 0 : selectedRulebook.extractionVersion()));
        requireDefinitionProvenance(blueprint);
        blueprint = synchronizeProposalDecisions(blueprint, sourceDocuments);
        packageRepository.saveBlueprint(packageId, blueprint);
        return toView(blueprint, sourceDocuments);
    }

    private static CharacterCreationBlueprint normalizeSystemAgnosticManualFields(
            CharacterCreationBlueprint blueprint, String edition,
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates) {
        if (!"DND_5E".equalsIgnoreCase(edition) || candidates.stream().anyMatch(candidate -> candidate != null
                && !candidate.evidence().isEmpty())) return blueprint;
        List<String> keys = List.of("name", "level", "starting_ability_scores.strength",
                "starting_ability_scores.dexterity", "starting_ability_scores.constitution",
                "starting_ability_scores.intelligence", "starting_ability_scores.wisdom",
                "starting_ability_scores.charisma");
        List<CharacterCreationBlueprint.Field> fields = new ArrayList<>(blueprint.fields());
        for (int index = 0; index < fields.size(); index++) {
            CharacterCreationBlueprint.Field field = fields.get(index);
            if (!keys.contains(field.key())) continue;
            fields.set(index, new CharacterCreationBlueprint.Field(field.key(), List.of(), field.required(),
                    field.sourceType(), field.evidence(), field.inputStatus(), field.diagnostics(),
                    com.dndmaster.adventure.domain.scenario.InputMode.FREE_TEXT, field.suggestions(),
                    field.sourceQuote(), field.label(), field.value(), field.nodeId(), field.parentNodeId(),
                    field.confidence(), List.of()));
        }
        return new CharacterCreationBlueprint(blueprint.revision(), blueprint.status(), fields, blueprint.diagnostics(), blueprint.provenance());
    }

    private static CharacterCreationBlueprint restoreGroundedCandidates(
            CharacterCreationBlueprint blueprint,
            String edition,
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates) {
        if ("DND_5E_2014".equalsIgnoreCase(edition)) return blueprint;
        Map<String, CharacterInputTagExtractionPort.CharacterInputTagCandidate> selected = new LinkedHashMap<>();
        for (CharacterInputTagExtractionPort.CharacterInputTagCandidate candidate : candidates) {
            if (candidate == null || candidate.evidence().isEmpty()) continue;
            String key = effectiveKey(candidate);
            var prior = selected.get(key);
            if (prior == null || sourcePriority(candidate.sourceType()) >= sourcePriority(prior.sourceType())) {
                selected.put(key, candidate);
            }
        }
        if (selected.isEmpty()) return blueprint;

        boolean reviewRequired = false;
        List<CharacterCreationBlueprint.Field> fields = new ArrayList<>(blueprint.fields());
        for (Map.Entry<String, CharacterInputTagExtractionPort.CharacterInputTagCandidate> entry : selected.entrySet()) {
            CharacterCreationBlueprint.Field templateField = fields.stream()
                    .filter(field -> field.key().equals(entry.getKey())).findFirst().orElse(null);
            if (templateField == null) continue;
            var candidate = entry.getValue();
            String status = "STORYBOOK".equals(candidate.sourceType()) ? "CONFLICT_REVIEW" : "EXTRACTED";
            List<String> diagnostics = "STORYBOOK".equals(candidate.sourceType())
                    ? List.of("스토리북 제안: 적용 여부를 검토하세요") : List.of();
            fields.set(fields.indexOf(templateField), new CharacterCreationBlueprint.Field(
                    templateField.key(), candidate.options(), candidate.required(), candidate.sourceType(),
                    candidate.evidence(), status, diagnostics, candidate.inputMode(), candidate.suggestions(),
                    candidate.sourceQuote(), candidate.label(), templateField.value(), templateField.nodeId(),
                    templateField.parentNodeId(), candidate.confidence(), candidateOptionDetails(candidate.optionDetails())));
            reviewRequired |= "STORYBOOK".equals(candidate.sourceType());
        }
        CharacterCreationBlueprintStatus status = reviewRequired
                ? CharacterCreationBlueprintStatus.NEEDS_REVIEW : blueprint.status();
        return new CharacterCreationBlueprint(blueprint.revision(), status, fields, blueprint.diagnostics(), blueprint.provenance());
    }

    private static String effectiveKey(CharacterInputTagExtractionPort.CharacterInputTagCandidate candidate) {
        return candidate.parentKey() != null && !candidate.key().contains(".")
                ? candidate.parentKey() + "." + candidate.key() : candidate.key();
    }

    private static int sourcePriority(String sourceType) {
        return switch (sourceType) {
            case "STORYBOOK" -> 3;
            case "HANDOUT" -> 2;
            case "RULEBOOK" -> 1;
            default -> 0;
        };
    }

    private static List<CharacterCreationBlueprint.Field.OptionDetail> candidateOptionDetails(
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate.OptionDetail> details) {
        return details.stream().map(detail -> new CharacterCreationBlueprint.Field.OptionDetail(
                detail.value(), detail.label(), detail.description(), detail.sourceQuote(), detail.evidence())).toList();
    }

    private List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> refineChoiceFields(
            UUID packageId, OwnerPlayerId ownerPlayerId, List<ScenarioBundleDocumentSelection> documents,
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> refined = new ArrayList<>();
        for (CharacterInputTagExtractionPort.CharacterInputTagCandidate candidate : candidates) {
            CharacterFieldSpec spec = fieldSpec(candidate.key());
            List<ScenarioBundleDocumentSelection> candidateDocuments = documents.stream()
                    .filter(document -> document.documentType().equalsIgnoreCase(candidate.sourceType())).toList();
            if (candidateDocuments.isEmpty()) candidateDocuments = documents;
            String sourceType = candidateDocuments.getFirst().documentType().toUpperCase(java.util.Locale.ROOT);
            List<CharacterContextSearchPort.DocumentScope> scopes = candidateDocuments.stream()
                    .map(document -> new CharacterContextSearchPort.DocumentScope(
                            document.knowledgeDocumentId(), document.documentType(), document.extractionVersion()))
                    .toList();
            try {
                long startedAt = System.nanoTime();
                LOGGER.info("character_blueprint_refine_started packageId={} field={} initialMode={} initialOptions={}",
                        packageId, candidate.key(), candidate.inputMode(), candidate.options().size());
                List<CharacterContextSearchPort.Evidence> evidence = characterContextSearch.search(
                        new CharacterContextSearchPort.Request(ownerPlayerId.value(), scopes,
                                spec == null
                                        ? "Find character-sheet value choices or player-authored input requirements for '" + candidate.label() + "'."
                                        : spec.retrievalQuery(),
                                java.util.Map.of(sourceType, .25), 0));
                List<CharacterInputTagExtractionPort.SourceExcerpt> topicExcerpts = excerptList(evidence);
                if (topicExcerpts.isEmpty()) throw new IllegalStateException("character blueprint extraction evidence is missing for field " + candidate.key());
                List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> result = characterTagExtraction.extract(
                        new CharacterInputTagExtractionPort.Request(
                                ownerPlayerId.value(), packageId + ":character-blueprint-refine:" + candidate.key() + ":" + UUID.randomUUID(),
                                topicExcerpts, "character-input-tag-v1", "character-input-tag-prompt-v1",
                                (spec == null
                                        ? "Keep key exactly '" + candidate.key() + "'."
                                        : spec.extractionPolicy() + " Keep key exactly '" + spec.key() + "'.")
                                        + " Decide FREE_TEXT, SINGLE_SELECT, or MULTI_SELECT from excerpts. If selectable, include one optionDetails object per option with description, sourceQuote, and evidence."));
                if (result == null) throw new IllegalStateException("character blueprint extraction returned no result for field " + candidate.key());
                var selected = result.stream()
                        .filter(item -> item.key().equals(candidate.key()))
                        .findFirst().map(item -> withSourceType(item, sourceType))
                        .orElseGet(() -> withSourceType(candidate, sourceType));
                LOGGER.info("character_blueprint_refine_finished packageId={} field={} excerpts={} mode={} options={} optionDetails={} elapsedMs={}",
                        packageId, candidate.key(), topicExcerpts.size(), selected.inputMode(), selected.options().size(),
                        selected.optionDetails().size(), (System.nanoTime() - startedAt) / 1_000_000);
                refined.add(selected);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("character blueprint extraction failed for field " + candidate.key(), exception);
            }
        }
        return List.copyOf(refined);
    }

    private static boolean completeSelectableChoice(CharacterInputTagExtractionPort.CharacterInputTagCandidate candidate) {
        return candidate.inputMode() != com.dndmaster.adventure.domain.scenario.InputMode.FREE_TEXT
                && !candidate.options().isEmpty()
                && candidate.optionDetails().size() == candidate.options().size()
                && candidate.optionDetails().stream().allMatch(detail -> candidate.options().contains(detail.value())
                        && !detail.evidence().isEmpty());
    }

    private List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> discoverCharacterFields(
            UUID packageId, OwnerPlayerId ownerPlayerId, List<ScenarioBundleDocumentSelection> documents) {
        if (documents.isEmpty()) return List.of();
        List<CharacterContextSearchPort.DocumentScope> scopes = documents.stream().map(document ->
                new CharacterContextSearchPort.DocumentScope(document.knowledgeDocumentId(), document.documentType(), document.extractionVersion())).toList();
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates = new ArrayList<>();
        for (CharacterFieldSpec spec : CHARACTER_FIELD_SPECS) {
            List<CharacterInputTagExtractionPort.SourceExcerpt> excerpts = excerptList(characterContextSearch.search(
                    new CharacterContextSearchPort.Request(ownerPlayerId.value(), scopes, spec.retrievalQuery(),
                            java.util.Map.of("RULEBOOK", .50), 0)));
            if (excerpts.isEmpty()) continue;
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> extracted = characterTagExtraction.extract(
                    new CharacterInputTagExtractionPort.Request(
                            ownerPlayerId.value(), packageId + ":character-blueprint-discovery:" + UUID.randomUUID(), excerpts,
                            "character-input-tag-v1", "character-input-tag-prompt-v1",
                            "Discover source-grounded character-creation fields and directly visible selectable values for this retrieval topic: "
                                    + spec.extractionPolicy() + " A partial list is valid. Treat explicit choose/select language as evidence. "
                                    + "Do not infer another edition."));
            if (extracted != null) extracted.stream().findFirst()
                    .map(candidate -> canonicalize(candidate, spec))
                    .ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    private List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> discoverStorybookFields(
            UUID packageId, OwnerPlayerId ownerPlayerId, List<ScenarioBundleDocumentSelection> storybooks) {
        if (storybooks.isEmpty()) return List.of();
        List<CharacterContextSearchPort.DocumentScope> scopes = storybooks.stream().map(document ->
                new CharacterContextSearchPort.DocumentScope(document.knowledgeDocumentId(), document.documentType(), document.extractionVersion())).toList();
        List<CharacterInputTagExtractionPort.SourceExcerpt> excerpts = excerptList(characterContextSearch.search(
                new CharacterContextSearchPort.Request(ownerPlayerId.value(), scopes,
                        "Find only campaign-specific facts that add, constrain, or prefill a D&D character-sheet field. "
                                + "Ignore plot lore with no player-character sheet effect.",
                        java.util.Map.of("STORYBOOK", .20), 0)));
        if (excerpts.isEmpty()) return List.of();
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> extracted = characterTagExtraction.extract(
                new CharacterInputTagExtractionPort.Request(
                        ownerPlayerId.value(), packageId + ":storybook-character-sheet-additions:" + UUID.randomUUID(), excerpts,
                        "character-input-tag-v1", "character-input-tag-prompt-v1",
                        "Extract only character-sheet fields affected by this storybook. For a finite list from which player must choose exactly one, emit SINGLE_SELECT with every allowed option. "
                                + "For a value player must author, emit FREE_TEXT with empty options. Do not emit MULTI_SELECT unless storybook explicitly allows multiple choices. "
                                + "Return no field when text does not affect character creation."));
        return (extracted == null ? List.<CharacterInputTagExtractionPort.CharacterInputTagCandidate>of() : extracted).stream()
                .map(candidate -> withSourceType(candidate, "STORYBOOK")).toList();
    }

    private static CharacterFieldSpec fieldSpec(String key) {
        return CHARACTER_FIELD_SPECS.stream().filter(spec -> spec.key().equals(key)).findFirst().orElse(null);
    }

    // Discovery runs once per known input slot. The model identifies values, not
    // API keys; bind its grounded result to the slot that initiated the search.
    private static CharacterInputTagExtractionPort.CharacterInputTagCandidate canonicalize(
            CharacterInputTagExtractionPort.CharacterInputTagCandidate candidate, CharacterFieldSpec spec) {
        return new CharacterInputTagExtractionPort.CharacterInputTagCandidate(spec.key(), spec.label(),
                candidate.parentKey(), candidate.required(), candidate.inputMode(), candidate.options(),
                candidate.suggestions(), candidate.confidence(), candidate.evidence(), candidate.sourceQuote(),
                candidate.sourceType(), candidate.optionDetails());
    }

    private static CharacterInputTagExtractionPort.CharacterInputTagCandidate withSourceType(
            CharacterInputTagExtractionPort.CharacterInputTagCandidate candidate, String sourceType) {
        return new CharacterInputTagExtractionPort.CharacterInputTagCandidate(candidate.key(), candidate.label(),
                candidate.parentKey(), candidate.required(), candidate.inputMode(), candidate.options(),
                candidate.suggestions(), candidate.confidence(), candidate.evidence(), candidate.sourceQuote(),
                sourceType, candidate.optionDetails());
    }

    private record CharacterFieldSpec(String key, String label, String retrievalQuery, String extractionPolicy) {}

    private static List<CharacterInputTagExtractionPort.SourceExcerpt> excerptList(
            List<CharacterContextSearchPort.Evidence> evidence) {
        return (evidence == null ? List.<CharacterContextSearchPort.Evidence>of() : evidence).stream()
                .sorted(java.util.Comparator.comparingDouble(CharacterContextSearchPort.Evidence::similarity).reversed())
                .map(item -> new CharacterInputTagExtractionPort.SourceExcerpt(
                        item.documentId(), item.extractionVersion(), item.locator(), item.excerpt()))
                .toList();
    }

    public CharacterCreationBlueprint resolveBlueprint(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                       String fieldKey, String value) {
        return resolveBlueprint(packageId, ownerPlayerId, 0, fieldKey, value);
    }

    public CharacterCreationBlueprintView useStorybookProposal(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                                long expectedRevision, String proposalId) {
        return decideStorybookProposal(packageId, ownerPlayerId, expectedRevision, proposalId,
                ProposalDecisionState.APPLIED);
    }

    public CharacterCreationBlueprintView excludeStorybookProposal(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                                   long expectedRevision, String proposalId) {
        return decideStorybookProposal(packageId, ownerPlayerId, expectedRevision, proposalId,
                ProposalDecisionState.EXCLUDED);
    }

    private CharacterCreationBlueprintView decideStorybookProposal(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                                    long expectedRevision, String proposalId,
                                                                    ProposalDecisionState state) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        CharacterCreationBlueprint current = requireBlueprint(scenarioPackage);
        requireBlueprintRevision(current, expectedRevision);
        List<ScenarioBundleDocumentSelection> documents = bundle.currentRevision().documents();
        CharacterCreationBlueprintView currentView = toView(current, documents);
        CharacterCreationBlueprintView.StorybookProposalView proposal = currentView.storybookProposals().stream()
                .filter(item -> item.proposalId().equals(proposalId)).findFirst()
                .orElseThrow(() -> new StorybookProposalNotFoundException(proposalId));
        CharacterCreationBlueprint synchronizedBlueprint = synchronizeProposalDecisions(current, documents);
        CharacterCreationBlueprint updated = synchronizedBlueprint.decideProposal(proposalId, state,
                "READY".equals(proposal.readinessState()));
        packageRepository.saveBlueprint(packageId, updated);
        return toView(updated, documents);
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
        CharacterCreationBlueprint resolved = blueprint.resolveNode(fieldKey, value);
        if ("class".equals(fieldKey)) resolved = enrichStartingEquipment(packageId, ownerPlayerId, bundle, resolved, value);
        packageRepository.saveBlueprint(packageId, resolved);
        return resolved;
    }

    private CharacterCreationBlueprint enrichStartingEquipment(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                                ScenarioSourceBundle bundle, CharacterCreationBlueprint blueprint,
                                                                String selectedClass) {
        List<ScenarioBundleDocumentSelection> rulebooks = bundle.currentRevision().documents().stream()
                .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType())).toList();
        if (rulebooks.isEmpty()) return blueprint;
        List<CharacterContextSearchPort.DocumentScope> scopes = rulebooks.stream().map(document ->
                new CharacterContextSearchPort.DocumentScope(document.knowledgeDocumentId(), document.documentType(), document.extractionVersion())).toList();
        List<CharacterInputTagExtractionPort.SourceExcerpt> excerpts = excerptList(characterContextSearch.search(
                new CharacterContextSearchPort.Request(ownerPlayerId.value(), scopes,
                        "Character creation class " + selectedClass + " starting equipment selectable choices and choose-one packages.",
                        java.util.Map.of("RULEBOOK", .50), 0)));
        if (excerpts.isEmpty()) return blueprint;
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates = characterTagExtraction.extract(
                new CharacterInputTagExtractionPort.Request(ownerPlayerId.value(), packageId + ":class-equipment:" + UUID.randomUUID(), excerpts,
                        "character-input-tag-v1", "character-input-tag-prompt-v1",
                        "Extract only field 'class.startingEquipment' for selected class '" + selectedClass + "'. "
                                + "Return selectable starting-equipment choices directly supported by excerpts, with one optionDetails item and exact evidence per option."));
        var candidate = (candidates == null ? List.<CharacterInputTagExtractionPort.CharacterInputTagCandidate>of() : candidates).stream()
                .filter(ScenarioPreparationApplicationService::completeSelectableChoice)
                .filter(item -> "RULEBOOK".equalsIgnoreCase(item.sourceType())).findFirst().orElse(null);
        if (candidate == null) return blueprint;
        CharacterCreationBlueprint.Field prior = blueprint.fields().stream()
                .filter(field -> field.key().equals("class.startingEquipment")).findFirst().orElse(null);
        List<CharacterCreationBlueprint.Field.OptionDetail> details = candidate.optionDetails().stream().map(detail ->
                new CharacterCreationBlueprint.Field.OptionDetail(detail.value(), detail.label(), detail.description(), detail.sourceQuote(), detail.evidence())).toList();
        return blueprint.enrichField(new CharacterCreationBlueprint.Field("class.startingEquipment", candidate.options(), true,
                "RULEBOOK", candidate.evidence(), "EXTRACTED", List.of(), candidate.inputMode(), candidate.suggestions(),
                candidate.sourceQuote(), prior == null ? "Starting equipment" : prior.label(), null,
                prior == null ? null : prior.nodeId(), prior == null ? null : prior.parentNodeId(), candidate.confidence(), details));
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

    public CharacterCreationBlueprint addBlueprintOption(UUID packageId, OwnerPlayerId ownerPlayerId,
                                                          long expectedRevision, String fieldKey, String option) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        CharacterCreationBlueprint blueprint = requireBlueprint(scenarioPackage);
        requireBlueprintRevision(blueprint, expectedRevision);
        CharacterCreationBlueprint updated = blueprint.addOption(fieldKey, option);
        packageRepository.saveBlueprint(packageId, updated);
        return updated;
    }

    public BlueprintPublicationResult publishBlueprint(UUID packageId, OwnerPlayerId ownerPlayerId) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        if (scenarioPackage.report().status() != ResolutionStatus.COMPLETE
                || scenarioPackage.characterCreationBlueprint() == null) {
            throw new CharacterCreationBlueprintPublicationBlockedException(
                    "scenario package compilation must be complete before blueprint publication");
        }
        CharacterCreationBlueprint current = requireBlueprint(scenarioPackage);
        requireDefinitionProvenance(current);
        List<ScenarioBundleDocumentSelection> documents = bundle.currentRevision().documents();
        CharacterCreationBlueprint synchronizedBlueprint = synchronizeProposalDecisions(current, documents);
        CharacterCreationBlueprintView review = toView(synchronizedBlueprint, documents);
        CharacterCreationBlueprint published = synchronizedBlueprint.publish();
        packageRepository.saveBlueprint(packageId, published);
        return new BlueprintPublicationResult(published.revision(), review.appliedSettingsSummary());
    }

    private static CharacterCreationBlueprint requireBlueprint(ScenarioPackage scenarioPackage) {
        if (scenarioPackage.characterCreationBlueprint() == null) {
            throw new IllegalStateException("character creation blueprint is unavailable");
        }
        return scenarioPackage.characterCreationBlueprint();
    }

    private static void requireDefinitionProvenance(CharacterCreationBlueprint blueprint) {
        var provenance = blueprint.provenance();
        if (provenance == null || provenance.gameSystemDefinitionVersion() < 1
                || provenance.sourceRevision() < 1 || provenance.sourceTypes().isEmpty()) {
            throw new IllegalStateException("character blueprint definition provenance is required");
        }
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
        // Shared catalog rulebooks are intentionally outside the private bundle. Once their
        // grounded fields are persisted, include those evidence documents in the read model too.
        rulebooks = Math.max(rulebooks, blueprint.fields().stream()
                .filter(field -> "RULEBOOK".equalsIgnoreCase(field.sourceType()))
                .flatMap(field -> field.evidence().stream())
                .map(reference -> reference.knowledgeDocumentId().value())
                .distinct().count());
        long storybooks = documents.stream().filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType())).count();
        List<CharacterCreationBlueprintView.FieldView> fields = blueprint.fields().stream()
                .map(field -> new CharacterCreationBlueprintView.FieldView(field.key(), field.options(), field.required(),
                        field.sourceType(), field.inputStatus(), field.diagnostics(), field.inputMode().name(), field.value(),
                        field.suggestions(), field.sourceQuote(), field.evidence().stream()
                                .map(reference -> new CharacterCreationBlueprintView.FieldView.SourceReferenceView(
                                        reference.knowledgeDocumentId().value().toString(), reference.extractionVersion(), reference.locator()))
                                .toList(), optionDetails(field.optionDetails()), field.label()))
                .toList();
        List<CharacterCreationBlueprintView.FieldView> baseFields = fields.stream()
                .filter(field -> "RULEBOOK".equalsIgnoreCase(field.sourceType())
                        || "TEMPLATE".equalsIgnoreCase(field.sourceType())).toList();
        List<CharacterCreationBlueprintView.FieldView> storybookFields = fields.stream()
                .filter(field -> "STORYBOOK".equalsIgnoreCase(field.sourceType()))
                .toList();
        Map<String, CharacterCreationBlueprintView.FieldView> uniqueStorybookFields = new java.util.TreeMap<>();
        for (CharacterCreationBlueprintView.FieldView field : storybookFields) {
            uniqueStorybookFields.merge(proposalSourceIdentity(field), field,
                    ScenarioPreparationApplicationService::canonicalRepresentative);
        }
        List<CharacterCreationBlueprintView.StorybookProposalView> proposals = uniqueStorybookFields.values().stream()
                .map(field -> toProposal(blueprint, field, documents)).toList();
        CharacterCreationBlueprintView.StorybookExtractionState extractionState = storybookExtractionState(documents, proposals);
        List<String> appliedProposalIds = proposals.stream()
                .filter(proposal -> "APPLIED".equals(proposal.decisionState()))
                .map(CharacterCreationBlueprintView.StorybookProposalView::proposalId).toList();
        List<String> excludedProposalIds = proposals.stream()
                .filter(proposal -> "EXCLUDED".equals(proposal.decisionState()))
                .map(CharacterCreationBlueprintView.StorybookProposalView::proposalId).toList();
        var appliedSummary = new CharacterCreationBlueprintView.AppliedSettingsSummaryView(
                true, appliedProposalIds, excludedProposalIds,
                proposals.size() - appliedProposalIds.size() - excludedProposalIds.size());
        return new CharacterCreationBlueprintView(
                true,
                "CharacterCreationBlueprint revision " + blueprint.revision(), (int) rulebooks, (int) storybooks,
                blueprint.diagnostics(), blueprint.revision(), fields, blueprint.status().name(),
                blueprint.roots().stream().map(ScenarioPreparationApplicationService::toNodeView).toList(),
                blueprint.provenance().edition(),
                new CharacterCreationBlueprintView.RulebookBaseSchemaView(blueprint.provenance().edition(), baseFields),
                proposals, extractionState, appliedSummary);
    }

    private static CharacterCreationBlueprintView.StorybookProposalView toProposal(
            CharacterCreationBlueprint blueprint, CharacterCreationBlueprintView.FieldView field,
            List<ScenarioBundleDocumentSelection> documents) {
        var evidence = field.evidence().stream()
                .map(reference -> new CharacterCreationBlueprintView.StorybookProposalView.SourceEvidence(
                        reference.locator(), evidenceQuote(field, reference)))
                .toList();
        var sourceReference = canonicalSourceReference(field);
        var source = sourceReference.flatMap(reference -> documents.stream()
                .filter(document -> document.knowledgeDocumentId().value().toString().equals(reference.knowledgeDocumentId()))
                .findFirst().map(document -> new CharacterCreationBlueprintView.StorybookProposalView.SourceDocument(
                        reference.knowledgeDocumentId(), document.originalFilename(), reference.extractionVersion()))).orElse(null);
        boolean hasEvidence = source != null && !field.sourceQuote().isBlank() && !evidence.isEmpty();
        String proposalId = StorybookProposalId.stableId(field, source);
        String decision = blueprint.proposalDecisions().stream().filter(item -> item.proposalId().equals(proposalId))
                .map(item -> item.state().name()).findFirst().orElse(hasEvidence ? "UNDECIDED" : "NEEDS_EVIDENCE");
        return new CharacterCreationBlueprintView.StorybookProposalView(
                proposalId, field.key(), field.label(), proposalDescription(field), source, field.sourceQuote(), evidence,
                decision, hasEvidence ? "READY" : "INSUFFICIENT_EVIDENCE");
    }

    private static CharacterCreationBlueprint synchronizeProposalDecisions(
            CharacterCreationBlueprint blueprint, List<ScenarioBundleDocumentSelection> documents) {
        CharacterCreationBlueprintView view = toView(blueprint, documents);
        List<StorybookProposalDecision> decisions = view.storybookProposals().stream()
                .map(proposal -> new StorybookProposalDecision(proposal.proposalId(), proposal.key(),
                        ProposalDecisionState.valueOf(proposal.decisionState())))
                .toList();
        return blueprint.withProposalDecisions(decisions);
    }

    private static String proposalDescription(CharacterCreationBlueprintView.FieldView field) {
        return field.optionDetails().stream().map(CharacterCreationBlueprintView.FieldView.OptionDetailView::description)
                .filter(description -> description != null && !description.isBlank()).distinct().findFirst().orElse("");
    }

    private static String evidenceQuote(CharacterCreationBlueprintView.FieldView field,
                                        CharacterCreationBlueprintView.FieldView.SourceReferenceView reference) {
        return field.optionDetails().stream().flatMap(detail -> detail.evidence().stream()
                        .filter(candidate -> candidate.knowledgeDocumentId().equals(reference.knowledgeDocumentId())
                                && candidate.extractionVersion() == reference.extractionVersion()
                                && candidate.locator().equals(reference.locator()))
                        .map(ignored -> detail.sourceQuote()))
                .filter(quote -> quote != null && !quote.isBlank()).findFirst().orElse(field.sourceQuote());
    }

    private static final class StorybookProposalId {
        private StorybookProposalId() {}

        private static String stableId(CharacterCreationBlueprintView.FieldView field,
                                       CharacterCreationBlueprintView.StorybookProposalView.SourceDocument source) {
            return CharacterCreationBlueprintView.StorybookProposalView.stableId(
                    source == null ? "UNRESOLVED" : source.knowledgeDocumentId(),
                    source == null ? 0 : source.extractionVersion(), field.key(), candidateIdentity(field));
        }
    }

    private static CharacterCreationBlueprintView.FieldView canonicalRepresentative(
            CharacterCreationBlueprintView.FieldView first, CharacterCreationBlueprintView.FieldView second) {
        return canonicalFieldFingerprint(first).compareTo(canonicalFieldFingerprint(second)) <= 0 ? first : second;
    }

    private static String proposalSourceIdentity(CharacterCreationBlueprintView.FieldView field) {
        var reference = canonicalSourceReference(field);
        String source = reference.map(value -> value.knowledgeDocumentId() + "|" + value.extractionVersion())
                .orElse("UNRESOLVED|0");
        return source + "|" + candidateIdentity(field);
    }

    private static java.util.Optional<CharacterCreationBlueprintView.FieldView.SourceReferenceView> canonicalSourceReference(
            CharacterCreationBlueprintView.FieldView field) {
        return field.evidence().stream().sorted(java.util.Comparator.comparing(
                        reference -> reference.knowledgeDocumentId() + "|" + reference.extractionVersion() + "|" + reference.locator()))
                .findFirst();
    }

    private static String canonicalFieldFingerprint(CharacterCreationBlueprintView.FieldView field) {
        return candidateIdentity(field) + "|" + field.sourceQuote();
    }

    private static String candidateIdentity(CharacterCreationBlueprintView.FieldView field) {
        return field.key() + "|" + field.required() + "|" + field.inputMode() + "|"
                + String.join("\u0001", field.options().stream().sorted().toList()) + "|"
                + String.join("\u0001", field.suggestions().stream().sorted().toList());
    }

    private static CharacterCreationBlueprintView.StorybookExtractionState storybookExtractionState(
            List<ScenarioBundleDocumentSelection> documents, List<CharacterCreationBlueprintView.StorybookProposalView> proposals) {
        java.util.Set<String> statuses = documents.stream()
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                .map(document -> document.status().name()).collect(java.util.stream.Collectors.toSet());
        boolean failed = statuses.contains("FAILED");
        if (failed) return CharacterCreationBlueprintView.StorybookExtractionState.EXTRACTION_FAILED;
        boolean partialAwaiting = statuses.contains("PARTIAL_AWAITING_CONFIRMATION");
        boolean partialConfirmed = statuses.contains("PARTIAL_CONFIRMED");
        if (partialAwaiting && partialConfirmed) return CharacterCreationBlueprintView.StorybookExtractionState.EXTRACTION_MIXED;
        if (partialAwaiting) return CharacterCreationBlueprintView.StorybookExtractionState.EXTRACTION_PARTIAL_AWAITING_CONFIRMATION;
        if (partialConfirmed) return CharacterCreationBlueprintView.StorybookExtractionState.EXTRACTION_PARTIAL_CONFIRMED;
        if (proposals.isEmpty()) return CharacterCreationBlueprintView.StorybookExtractionState.NO_PROPOSALS;
        if (proposals.stream().anyMatch(proposal -> "INSUFFICIENT_EVIDENCE".equals(proposal.readinessState()))) {
            return CharacterCreationBlueprintView.StorybookExtractionState.INSUFFICIENT_EVIDENCE;
        }
        return CharacterCreationBlueprintView.StorybookExtractionState.PROPOSALS_AVAILABLE;
    }

    private static CharacterCreationBlueprintView.NodeView toNodeView(CharacterInputNode node) {
        return new CharacterCreationBlueprintView.NodeView(node.id(), node.parentId(), node.key(), node.label(),
                node.inputMode().name(), node.value(), node.options(), node.suggestions(), node.status().name(),
                node.allowUserAddChild(), node.confidence(), node.sourceQuote(), node.diagnostics(),
                node.sourceEvidence().stream().map(reference -> new CharacterCreationBlueprintView.FieldView.SourceReferenceView(
                        reference.knowledgeDocumentId().value().toString(), reference.extractionVersion(), reference.locator())).toList(),
                node.children().stream().map(ScenarioPreparationApplicationService::toNodeView).toList(), optionDetails(node.optionDetails()));
    }

    private static List<CharacterCreationBlueprintView.FieldView.OptionDetailView> optionDetails(
            List<CharacterCreationBlueprint.Field.OptionDetail> details) {
        return details.stream().map(detail -> new CharacterCreationBlueprintView.FieldView.OptionDetailView(
                detail.value(), detail.label(), detail.description(), detail.sourceQuote(), detail.evidence().stream()
                        .map(reference -> new CharacterCreationBlueprintView.FieldView.SourceReferenceView(
                                reference.knowledgeDocumentId().value().toString(), reference.extractionVersion(), reference.locator()))
                        .toList())).toList();
    }

    private static boolean isSearchable(com.dndmaster.adventure.domain.scenario.KnowledgeDocumentStatus status) {
        return status != null && ("INDEXED".equals(status.name())
                || "INDEXED_WITH_WARNINGS".equals(status.name()));
    }
}
