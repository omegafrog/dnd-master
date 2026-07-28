package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ResolutionFingerprint;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.ResolutionOverride;
import com.dndmaster.adventure.domain.scenario.ResolutionOverrideStatus;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterCreationBlueprintCompiler;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScenarioPackageCompilationService {
    private static final String COMPILER_VERSION = "resolution-compiler-v1";
    private static final String DICE_PATTERN = "(?i)\\d+d\\d+(?:\\s*[+-]\\s*\\d+)?";
    private static final Pattern CHARACTER_LIMIT_PATTERN = Pattern.compile(
            "(?i)(?:최대|up\\s+to|maximum(?:\\s+of)?|max)\\s*(\\d+)\\s*(?:명|players?|users?)");
    private final ScenarioPackageRepository repository;
    private final ResolutionOverrideRepository overrideRepository;
    private final CharacterCreationBlueprintCompiler blueprintCompiler = new CharacterCreationBlueprintCompiler();

    public ScenarioPackageCompilationService(ScenarioPackageRepository repository) {
        this(repository, new NoopResolutionOverrideRepository());
    }

    public ScenarioPackageCompilationService(
            ScenarioPackageRepository repository,
            ResolutionOverrideRepository overrideRepository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.overrideRepository = Objects.requireNonNull(overrideRepository, "override repository must not be null");
    }

    public ScenarioPackage compile(ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates) {
        return compileInternal(bundle, candidates, List.of(), false, List.of());
    }

    public ScenarioPackage compile(
            ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts) {
        return compileInternal(bundle, candidates, excerpts, true, List.of());
    }

    public ScenarioPackage compile(
            ScenarioSourceBundle bundle,
            List<ResolutionCandidate> candidates,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts,
            List<ResolutionOverride> overrides) {
        List<ResolutionOverride> requestedOverrides = List.copyOf(Objects.requireNonNull(overrides, "overrides must not be null"));
        if (!requestedOverrides.isEmpty()) {
            overrideRepository.saveAll(requestedOverrides);
        }
        return compileInternal(bundle, candidates, excerpts, true, requestedOverrides);
    }

    private ScenarioPackage compileInternal(
            ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts, boolean verifyEvidence,
            List<ResolutionOverride> requestedOverrides) {
        Objects.requireNonNull(bundle, "bundle must not be null");
        List<ResolutionCandidate> requested = new ArrayList<>(Objects.requireNonNull(candidates, "candidates must not be null"));
        List<ResolutionExtractionPort.SourceExcerpt> availableExcerpts =
                List.copyOf(Objects.requireNonNull(excerpts, "excerpts must not be null"));
        List<ResolutionOverride> storedOverrides = overrideRepository.findByBundleId(bundle.id());
        List<ResolutionOverride> allOverrides = mergeOverrides(storedOverrides, requestedOverrides);
        OverrideApplicationResult overrideResult = applyOverrides(requested, allOverrides);
        String fingerprint = fingerprint(bundle, overrideResult.effectiveCandidates(), overrideResult.overrides());
        var existing = repository.findByInputFingerprint(fingerprint);
        if (existing.isPresent()) {
            return existing.get();
        }

        Map<String, ScenarioBundleDocumentSelection> documents = new HashMap<>();
        for (ScenarioBundleDocumentSelection document : bundle.currentRevision().documents()) {
            documents.put(key(document.knowledgeDocumentId(), document.extractionVersion()), document);
        }
        List<ScenarioResolutionUnit> units = overrideResult.effectiveCandidates().stream()
                .map(candidate -> validate(candidate, documents, availableExcerpts, verifyEvidence))
                .toList();
        List<String> warnings = units.stream()
                .flatMap(unit -> unit.validationMessages().stream())
                .toList();
        warnings = new ArrayList<>(warnings);
        warnings.addAll(overrideResult.warnings());
        if (units.isEmpty()) {
            warnings = new ArrayList<>();
            warnings.add("no resolution candidates were produced");
            warnings.addAll(overrideResult.warnings());
        }
        ResolutionStatus reportStatus = units.stream().anyMatch(unit -> unit.status() == ResolutionStatus.INVALID)
                ? ResolutionStatus.INVALID
                : units.stream().anyMatch(unit -> unit.status() == ResolutionStatus.PARTIAL)
                        || units.isEmpty() ? ResolutionStatus.PARTIAL : ResolutionStatus.COMPLETE;
        if (!overrideResult.overrides().isEmpty()) {
            overrideRepository.saveAll(overrideResult.overrides());
        }
        ScenarioPackage scenarioPackage = ScenarioPackage.publish(
                bundle.id(), bundle.currentRevision().revision(), fingerprint,
                bundle.currentRevision().documents(), units,
                new ScenarioCompilationReport(reportStatus, warnings),
                characterLimit(bundle, availableExcerpts),
                blueprintCompiler.compile(bundle.currentRevision().revision(), blueprintCandidates(bundle, availableExcerpts)));
        repository.save(scenarioPackage);
        return scenarioPackage;
    }

    private static List<CharacterCreationBlueprintCompiler.FieldCandidate> blueprintCandidates(
            ScenarioSourceBundle bundle, List<ResolutionExtractionPort.SourceExcerpt> excerpts) {
        boolean hasHandout = bundle.currentRevision().documents().stream()
                .anyMatch(document -> document.role() == ScenarioBundleDocumentRole.HANDOUT);
        String sourceType = hasHandout ? "HANDOUT" : "RULEBOOK";
        List<CharacterCreationBlueprintCompiler.FieldCandidate> candidates = new ArrayList<>();
        for (String key : List.of("name", "race", "class", "background", "starting_ability_scores", "level")) {
            boolean extracted = false;
            List<String> options = List.of();
            ResolutionExtractionPort.SourceExcerpt evidence = null;
            String evidenceSourceType = null;
            String sourceQuote = "";
            for (ResolutionExtractionPort.SourceExcerpt excerpt : excerpts.stream()
                    .sorted(Comparator.comparing((ResolutionExtractionPort.SourceExcerpt excerpt) -> isStorybook(bundle, excerpt)).reversed())
                    .toList()) {
                String label = switch (key) {
                    case "starting_ability_scores" -> "(?:starting\\s+ability\\s+scores|능력치)";
                    default -> key;
                };
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "(?i)" + label + "\\s*[:：-]\\s*([^\\n\\r.;]+)").matcher(excerpt.text() == null ? "" : excerpt.text());
                if (matcher.find()) {
                    options = java.util.Arrays.stream(matcher.group(1).split("[,/|]"))
                            .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
                    extracted = !options.isEmpty();
                    evidence = excerpt;
                    sourceQuote = matcher.group(0);
                    evidenceSourceType = isStorybook(bundle, excerpt) ? "STORYBOOK"
                            : isHandoutExcerpt(bundle, excerpt) ? "HANDOUT" : "RULEBOOK";
                    break;
                }
            }
            if (evidence == null) {
                var document = bundle.currentRevision().documents().stream()
                        .filter(candidate -> sourceType.equalsIgnoreCase(candidate.documentType())
                                || (sourceType.equals("HANDOUT") && candidate.role() == ScenarioBundleDocumentRole.HANDOUT))
                        .findFirst().orElse(null);
                if (document != null) evidence = new ResolutionExtractionPort.SourceExcerpt(
                        document.knowledgeDocumentId(), document.extractionVersion(), "document", "");
                evidenceSourceType = sourceType;
            }
            if (evidence != null) candidates.add(new CharacterCreationBlueprintCompiler.FieldCandidate(
                    key, inputOptions(key, options), extracted, evidenceSourceType,
                    new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(
                            evidence.documentId(), evidence.extractionVersion(), evidence.locator()),
                    sourceQuote, inputMode(key, options),
                    inputSuggestions(key, options)));
        }
        return candidates;
    }

    private static InputMode inputMode(String key, List<String> values) {
        return switch (key) {
            case "name" -> InputMode.FREE_TEXT;
            case "starting_ability_scores", "background" -> InputMode.FREE_TEXT;
            default -> values.isEmpty() ? InputMode.FREE_TEXT : InputMode.SINGLE_SELECT;
        };
    }

    private static List<String> inputOptions(String key, List<String> values) {
        return inputMode(key, values) == InputMode.FREE_TEXT ? List.of() : values;
    }

    private static List<String> inputSuggestions(String key, List<String> values) {
        return inputMode(key, values) == InputMode.FREE_TEXT ? values : List.of();
    }

    private static boolean isHandoutExcerpt(
            ScenarioSourceBundle bundle, ResolutionExtractionPort.SourceExcerpt excerpt) {
        return bundle.currentRevision().documents().stream().anyMatch(document ->
                document.role() == ScenarioBundleDocumentRole.HANDOUT
                        && document.knowledgeDocumentId().equals(excerpt.documentId())
                        && document.extractionVersion() == excerpt.extractionVersion());
    }

    private static CharacterLimit characterLimit(
            ScenarioSourceBundle bundle, List<ResolutionExtractionPort.SourceExcerpt> excerpts) {
        return excerpts.stream()
                .filter(excerpt -> isStorybook(bundle, excerpt))
                .flatMap(excerpt -> matches(excerpt).stream())
                .max(Comparator.comparingInt(CharacterLimit::maximumCharacters))
                .orElseGet(CharacterLimit::defaultLimit);
    }

    private static boolean isStorybook(ScenarioSourceBundle bundle, ResolutionExtractionPort.SourceExcerpt excerpt) {
        return bundle.currentRevision().documents().stream().anyMatch(document ->
                document.knowledgeDocumentId().equals(excerpt.documentId())
                        && document.extractionVersion() == excerpt.extractionVersion()
                        && "STORYBOOK".equalsIgnoreCase(document.documentType()));
    }

    private static List<CharacterLimit> matches(ResolutionExtractionPort.SourceExcerpt excerpt) {
        if (excerpt.text() == null) return List.of();
        Matcher matcher = CHARACTER_LIMIT_PATTERN.matcher(excerpt.text());
        List<CharacterLimit> limits = new ArrayList<>();
        while (matcher.find()) {
            int maximum = Integer.parseInt(matcher.group(1));
            if (maximum > 0) limits.add(new CharacterLimit(maximum,
                    new ScenarioSourceReference(excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator()),
                    matcher.group()));
        }
        return limits;
    }

    private static boolean containsEvidenceQuote(String excerptText, String sourceQuote) {
        return normalizeEvidenceText(excerptText).contains(normalizeEvidenceText(sourceQuote));
    }

    private static String normalizeEvidenceText(String value) {
        return value == null ? "" : value.strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static ScenarioResolutionUnit validate(
            ResolutionCandidate candidate, Map<String, ScenarioBundleDocumentSelection> documents,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts, boolean verifyEvidence) {
        List<String> invalid = new ArrayList<>();
        List<String> incomplete = new ArrayList<>();
        if (candidate == null) {
            return new ScenarioResolutionUnit(
                    null, null, null, null, ResolutionVisibility.GM_REFERENCE,
                    "", List.of(), "", ScenarioResolutionDetail.empty(), ResolutionStatus.INVALID, List.of("candidate is null"));
        }
        if (candidate.kind() == null) invalid.add("resolution kind is missing");
        if (candidate.visibility() == null) incomplete.add("visibility is missing");
        if (candidate.provenance() == null || candidate.provenance().isBlank()) incomplete.add("provenance is missing");
        if (candidate.sourceQuote() == null || candidate.sourceQuote().isBlank()) incomplete.add("source quote is missing");
        ScenarioResolutionDetail detail = candidate.detail() == null ? ScenarioResolutionDetail.empty() : candidate.detail();
        if (candidate.sourceRefs() == null || candidate.sourceRefs().isEmpty()) {
            invalid.add("source reference is missing");
        } else {
            for (ScenarioSourceReference ref : candidate.sourceRefs()) {
                ScenarioBundleDocumentSelection sourceDocument = ref == null
                        ? null
                        : documents.get(key(ref.knowledgeDocumentId(), ref.extractionVersion()));
                if (sourceDocument == null || ref.locator().isBlank()) {
                    invalid.add("source reference is outside bundle revision");
                } else if (verifyEvidence && excerpts.stream().noneMatch(excerpt ->
                        ref.knowledgeDocumentId().equals(excerpt.documentId())
                                && ref.extractionVersion() == excerpt.extractionVersion()
                                && ref.locator().equals(excerpt.locator()))) {
                    invalid.add("source excerpt is unavailable");
                } else if (candidate.visibility() == ResolutionVisibility.PLAYER_SAFE
                        && sourceDocument.role() != com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.HANDOUT
                        && sourceDocument.role() != com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.CHARACTER_SHEET) {
                    invalid.add("GM reference cannot be player safe");
                }
            }
        }
        if (verifyEvidence) {
            boolean quoteVerified = candidate.sourceQuote() != null && !candidate.sourceQuote().isBlank()
                    && candidate.sourceRefs() != null && !candidate.sourceRefs().isEmpty()
                    && excerpts.stream().anyMatch(excerpt -> candidate.sourceRefs().stream().anyMatch(ref ->
                            ref != null && ref.knowledgeDocumentId().equals(excerpt.documentId())
                                    && ref.extractionVersion() == excerpt.extractionVersion()
                                    && ref.locator().equals(excerpt.locator())
                                    && excerpt.text() != null
                                    && containsEvidenceQuote(excerpt.text(), candidate.sourceQuote())));
            if (!quoteVerified) invalid.add("source quote cannot be verified against referenced excerpt");
        }
        if (!detail.isEmpty()) {
            if (detail.actor() == null || detail.actor().isBlank()) incomplete.add("actor is missing");
            if (detail.roller() == null || detail.roller().isBlank()) incomplete.add("roller is missing");
            if (detail.instructionVisibility() == null || detail.instructionVisibility().isBlank()) {
                incomplete.add("instruction visibility is missing");
            }
        }
        if (candidate.kind() != null) {
            validateByKind(candidate, detail, invalid, incomplete);
        }
        validateDetail(detail, invalid, incomplete);
        ResolutionStatus status = !invalid.isEmpty()
                ? ResolutionStatus.INVALID
                : incomplete.isEmpty() ? ResolutionStatus.COMPLETE : ResolutionStatus.PARTIAL;
        List<String> messages = new ArrayList<>(invalid);
        messages.addAll(incomplete);
        return new ScenarioResolutionUnit(
                candidate.kind() == null ? ResolutionKind.DICE_ROLL : candidate.kind(),
                candidate.abilityOrSkill(),
                candidate.dc(),
                candidate.diceExpression(),
                candidate.visibility() == null
                        ? com.dndmaster.adventure.domain.scenario.ResolutionVisibility.GM_REFERENCE
                        : candidate.visibility(),
                candidate.sourceQuote() == null ? "" : candidate.sourceQuote(),
                candidate.sourceRefs() == null
                        ? List.of()
                        : candidate.sourceRefs().stream().filter(Objects::nonNull).toList(),
                candidate.provenance() == null ? "" : candidate.provenance(),
                detail,
                status,
                messages);
    }

    private static void validateByKind(
            ResolutionCandidate candidate,
            ScenarioResolutionDetail detail,
            List<String> invalid,
            List<String> incomplete) {
        switch (candidate.kind()) {
            case SKILL_ABILITY_CHECK, SAVING_THROW, PASSIVE_THRESHOLD -> {
                if (candidate.abilityOrSkill() == null || candidate.abilityOrSkill().isBlank()) {
                    incomplete.add("ability or skill is missing");
                }
                validateDc(candidate.dc(), invalid, incomplete);
            }
            case ATTACK_ROLL -> {
                if (candidate.abilityOrSkill() == null || candidate.abilityOrSkill().isBlank()) {
                    incomplete.add("ability or skill is missing");
                }
                if (!validDice(candidate.diceExpression())) invalid.add("dice expression is invalid");
            }
            case OPPOSED_CHECK -> {
                if (candidate.abilityOrSkill() == null || candidate.abilityOrSkill().isBlank()) {
                    incomplete.add("ability or skill is missing");
                }
                if (candidate.dc() != null && (candidate.dc() < 0 || candidate.dc() > 100)) {
                    invalid.add("DC is outside supported range");
                }
            }
            case DICE_ROLL, DAMAGE_ROLL, HEALING_ROLL, INITIATIVE_ROLL, RECHARGE_ROLL -> {
                if (!validDice(candidate.diceExpression())) invalid.add("dice expression is invalid");
            }
            case RANDOM_TABLE -> {
                if (!validDice(candidate.diceExpression())) invalid.add("dice expression is invalid");
                if (detail.randomTable().isEmpty()) invalid.add("random table entries are missing");
                if ("PARTIAL".equalsIgnoreCase(detail.tableCoverage())) incomplete.add("random table coverage is PARTIAL");
            }
            case SPECIAL_ROLL -> incomplete.add("special roll requires manual runtime support");
        }
    }

    private static void validateDetail(ScenarioResolutionDetail detail, List<String> invalid, List<String> incomplete) {
        for (ScenarioResolutionDetail.Step step : detail.steps()) {
            if (step.id() == null || step.id().isBlank()) invalid.add("step id is missing");
            if (step.kind() == null) invalid.add("step kind is missing");
            if (step.sourceRefs().isEmpty()) invalid.add("step source reference is missing");
        }
        for (ScenarioResolutionDetail.Outcome outcome : detail.outcomes()) {
            if (outcome.id() == null || outcome.id().isBlank()) invalid.add("outcome id is missing");
            if (outcome.description() == null || outcome.description().isBlank()) invalid.add("outcome description is missing");
            if (outcome.sourceRefs().isEmpty()) invalid.add("outcome source reference is missing");
        }
        for (ScenarioResolutionDetail.TableEntry entry : detail.randomTable()) {
            if (entry.range() == null || entry.range().isBlank()) invalid.add("random table range is missing");
            if (entry.outcome() == null || entry.outcome().isBlank()) invalid.add("random table outcome is missing");
            if (entry.sourceRefs().isEmpty()) invalid.add("random table source reference is missing");
        }
    }

    private static void validateDc(Integer dc, List<String> invalid, List<String> incomplete) {
        if (dc == null) {
            incomplete.add("DC is missing");
        } else if (dc < 0 || dc > 100) {
            invalid.add("DC is outside supported range");
        }
    }

    private static boolean validDice(String expression) {
        if (expression == null || !expression.matches(DICE_PATTERN)) return false;
        String[] parts = expression.toLowerCase().replace(" ", "").split("d");
        int dice = Integer.parseInt(parts[0]);
        int sides = Integer.parseInt(parts[1].replaceFirst("[+-].*", ""));
        return dice > 0 && sides > 0 && dice <= 100 && sides <= 1000;
    }

    private static String fingerprint(ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates) {
        StringBuilder value = new StringBuilder(COMPILER_VERSION).append('|')
                .append(bundle.id().value().toString())
                .append(':').append(bundle.currentRevision().revision());
        for (ResolutionCandidate candidate : candidates) {
            value.append('|').append(candidate == null ? "null" : candidate);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String fingerprint(
            ScenarioSourceBundle bundle,
            List<ResolutionCandidate> candidates,
            List<ResolutionOverride> overrides) {
        StringBuilder value = new StringBuilder(COMPILER_VERSION).append('|')
                .append(bundle.id().value().toString())
                .append(':').append(bundle.currentRevision().revision());
        for (ResolutionCandidate candidate : candidates) {
            value.append('|').append(candidate == null ? "null" : candidate);
        }
        for (ResolutionOverride override : overrides.stream()
                .sorted(Comparator.comparing(ResolutionOverride::overrideId).thenComparingLong(ResolutionOverride::revision))
                .toList()) {
            value.append('|').append(override.overrideId())
                    .append(':').append(override.revision())
                    .append(':').append(override.anchorFingerprint())
                    .append(':').append(override.status())
                    .append(':').append(override.replacementCandidate());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static List<ResolutionOverride> mergeOverrides(
            List<ResolutionOverride> storedOverrides,
            List<ResolutionOverride> requestedOverrides) {
        if (requestedOverrides.isEmpty()) {
            return storedOverrides;
        }
        Map<java.util.UUID, ResolutionOverride> overrides = new HashMap<>();
        for (ResolutionOverride override : storedOverrides) {
            overrides.put(override.overrideId(), override);
        }
        for (ResolutionOverride override : requestedOverrides) {
            overrides.put(override.overrideId(), override);
        }
        return overrides.values().stream().toList();
    }

    private static OverrideApplicationResult applyOverrides(
            List<ResolutionCandidate> candidates,
            List<ResolutionOverride> overrides) {
        List<ResolutionCandidate> effective = new ArrayList<>(candidates);
        List<String> warnings = new ArrayList<>();
        List<ResolutionOverride> resolvedOverrides = new ArrayList<>();
        Map<String, List<Integer>> candidateIndexesByAnchor = new HashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            ResolutionCandidate candidate = candidates.get(index);
            if (candidate == null) {
                continue;
            }
            String anchor = ResolutionFingerprint.candidateAnchorFingerprint(
                    candidate.kind(),
                    candidate.abilityOrSkill(),
                    candidate.dc(),
                    candidate.diceExpression(),
                    candidate.visibility(),
                    candidate.sourceQuote(),
                    candidate.sourceRefs(),
                    candidate.detail());
            candidateIndexesByAnchor.computeIfAbsent(anchor, ignored -> new ArrayList<>()).add(index);
        }
        for (ResolutionOverride override : overrides) {
            List<Integer> matches = candidateIndexesByAnchor.getOrDefault(override.anchorFingerprint(), List.of());
            if (matches.size() == 1) {
                int index = matches.get(0);
                effective.set(index, (ResolutionCandidate) override.replacementCandidate());
                resolvedOverrides.add(override.withStatus(
                        override.reason(), override.updatedAt(), ResolutionOverrideStatus.APPLIED,
                        override.replacementCandidate()));
            } else {
                String reason = matches.isEmpty()
                        ? "override conflict: no exact candidate match"
                        : "override conflict: multiple candidates match the same anchor";
                warnings.add(reason);
                resolvedOverrides.add(override.withStatus(
                        reason, override.updatedAt(), ResolutionOverrideStatus.CONFLICT, override.replacementCandidate()));
            }
        }
        return new OverrideApplicationResult(effective, warnings, resolvedOverrides);
    }

    private record OverrideApplicationResult(
            List<ResolutionCandidate> effectiveCandidates,
            List<String> warnings,
            List<ResolutionOverride> overrides) {}

    private static String key(Object documentId, long extractionVersion) {
        return documentId + ":" + extractionVersion;
    }
}
