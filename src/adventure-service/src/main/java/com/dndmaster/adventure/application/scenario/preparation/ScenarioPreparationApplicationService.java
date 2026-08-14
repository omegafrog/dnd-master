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
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.CharacterInputNode;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintRevisionConflictException;
import com.dndmaster.adventure.domain.scenario.BlueprintProvenance;
import com.dndmaster.adventure.application.runtime.GameSystemDefinitionPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
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
        this.packageRepository = Objects.requireNonNull(packageRepository, "package repository must not be null");
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.runtimeOptionCatalog = Objects.requireNonNull(runtimeOptionCatalog, "runtime option catalog must not be null");
        this.characterContextSearch = Objects.requireNonNull(characterContextSearch, "character context search must not be null");
        this.characterTagExtraction = Objects.requireNonNull(characterTagExtraction, "character tag extraction must not be null");
        this.blueprintCompiler = Objects.requireNonNull(blueprintCompiler, "blueprint compiler must not be null");
        this.gameSystemDefinitionPort = Objects.requireNonNull(gameSystemDefinitionPort, "game system definition port must not be null");
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
        blueprint = blueprint.withProvenance(new BlueprintProvenance(definitionVersion, bundle.currentRevision().revision(),
                sourceDocuments.stream().map(document -> document.documentType().toUpperCase()).distinct().toList(), edition));
        requireDefinitionProvenance(blueprint);
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
                                packageId + ":character-blueprint-refine:" + candidate.key() + ":" + UUID.randomUUID(),
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
                            packageId + ":character-blueprint-discovery:" + UUID.randomUUID(), excerpts,
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
                        packageId + ":storybook-character-sheet-additions:" + UUID.randomUUID(), excerpts,
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
                new CharacterInputTagExtractionPort.Request(packageId + ":class-equipment:" + UUID.randomUUID(), excerpts,
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

    public CharacterCreationBlueprint publishBlueprint(UUID packageId, OwnerPlayerId ownerPlayerId) {
        ScenarioPackage scenarioPackage = packageRepository.findById(packageId)
                .orElseThrow(ScenarioBundleNotFoundException::new);
        ScenarioSourceBundle bundle = bundleRepository.findById(scenarioPackage.bundleId())
                .orElseThrow(ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        requireCurrentBundleRevision(scenarioPackage, bundle);
        if (scenarioPackage.report().status() == ResolutionStatus.INVALID
                || (scenarioPackage.characterCreationBlueprint() == null
                && scenarioPackage.runtimeCandidates().isEmpty())) {
            throw new IllegalStateException("scenario package is not ready for blueprint publication");
        }
        CharacterCreationBlueprint current = requireBlueprint(scenarioPackage);
        requireDefinitionProvenance(current);
        CharacterCreationBlueprint published = current.publish();
        packageRepository.saveBlueprint(packageId, published);
        return published;
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
                                .toList(), optionDetails(field.optionDetails())))
                .toList();
        List<CharacterCreationBlueprintView.FieldView> baseFields = fields.stream()
                .filter(field -> "RULEBOOK".equalsIgnoreCase(field.sourceType())).toList();
        List<CharacterCreationBlueprintView.StorybookProposalView> proposals = fields.stream()
                .filter(field -> "STORYBOOK".equalsIgnoreCase(field.sourceType()))
                .map(field -> toProposal(field, documents)).toList();
        CharacterCreationBlueprintView.StorybookExtractionState extractionState = storybookExtractionState(documents, proposals);
        return new CharacterCreationBlueprintView(
                blueprint.status().name().equals("READY") || blueprint.status().name().equals("PUBLISHED"),
                "CharacterCreationBlueprint revision " + blueprint.revision(), (int) rulebooks, (int) storybooks,
                blueprint.diagnostics(), blueprint.revision(), fields, blueprint.status().name(),
                blueprint.roots().stream().map(ScenarioPreparationApplicationService::toNodeView).toList(),
                blueprint.provenance().edition(),
                new CharacterCreationBlueprintView.RulebookBaseSchemaView(blueprint.provenance().edition(), baseFields),
                proposals, extractionState);
    }

    private static CharacterCreationBlueprintView.StorybookProposalView toProposal(
            CharacterCreationBlueprintView.FieldView field, List<ScenarioBundleDocumentSelection> documents) {
        var evidence = field.evidence().stream()
                .map(reference -> new CharacterCreationBlueprintView.StorybookProposalView.SourceEvidence(
                        reference.locator(), field.sourceQuote()))
                .toList();
        var source = field.evidence().stream().findFirst().flatMap(reference -> documents.stream()
                .filter(document -> document.knowledgeDocumentId().value().toString().equals(reference.knowledgeDocumentId()))
                .findFirst().map(document -> new CharacterCreationBlueprintView.StorybookProposalView.SourceDocument(
                        reference.knowledgeDocumentId(), document.originalFilename(), reference.extractionVersion()))).orElse(null);
        boolean hasEvidence = source != null && !field.sourceQuote().isBlank() && !evidence.isEmpty();
        return new CharacterCreationBlueprintView.StorybookProposalView(
                UUID.nameUUIDFromBytes((field.key() + "|" + field.sourceQuote() + "|" + evidence).getBytes(StandardCharsets.UTF_8)).toString(),
                field.key(), field.key(), String.join(", ", field.options()), source, field.sourceQuote(), evidence,
                "UNDECIDED", hasEvidence ? "READY" : "INSUFFICIENT_EVIDENCE");
    }

    private static CharacterCreationBlueprintView.StorybookExtractionState storybookExtractionState(
            List<ScenarioBundleDocumentSelection> documents, List<CharacterCreationBlueprintView.StorybookProposalView> proposals) {
        boolean failed = documents.stream().anyMatch(document -> "STORYBOOK".equalsIgnoreCase(document.documentType())
                && "FAILED".equalsIgnoreCase(document.status().name()));
        if (failed) return CharacterCreationBlueprintView.StorybookExtractionState.EXTRACTION_FAILED;
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
}
