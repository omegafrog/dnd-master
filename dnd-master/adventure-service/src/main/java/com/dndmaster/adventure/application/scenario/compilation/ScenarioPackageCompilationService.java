package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScenarioPackageCompilationService {
    private static final String COMPILER_VERSION = "resolution-compiler-v1";
    private static final String DICE_PATTERN = "(?i)\\d+d\\d+(?:\\s*[+-]\\s*\\d+)?";
    private final ScenarioPackageRepository repository;

    public ScenarioPackageCompilationService(ScenarioPackageRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public ScenarioPackage compile(ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates) {
        return compileInternal(bundle, candidates, List.of(), false);
    }

    public ScenarioPackage compile(
            ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts) {
        return compileInternal(bundle, candidates, excerpts, true);
    }

    private ScenarioPackage compileInternal(
            ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts, boolean verifyEvidence) {
        Objects.requireNonNull(bundle, "bundle must not be null");
        List<ResolutionCandidate> requested = new ArrayList<>(Objects.requireNonNull(candidates, "candidates must not be null"));
        List<ResolutionExtractionPort.SourceExcerpt> availableExcerpts =
                List.copyOf(Objects.requireNonNull(excerpts, "excerpts must not be null"));
        String fingerprint = fingerprint(bundle, requested);
        var existing = repository.findByInputFingerprint(fingerprint);
        if (existing.isPresent()) {
            return existing.get();
        }

        Map<String, ScenarioBundleDocumentSelection> documents = new HashMap<>();
        for (ScenarioBundleDocumentSelection document : bundle.currentRevision().documents()) {
            documents.put(key(document.knowledgeDocumentId(), document.extractionVersion()), document);
        }
        List<ScenarioResolutionUnit> units = requested.stream()
                .map(candidate -> validate(candidate, documents, availableExcerpts, verifyEvidence))
                .toList();
        List<String> warnings = units.stream()
                .flatMap(unit -> unit.validationMessages().stream())
                .toList();
        if (units.isEmpty()) warnings = List.of("no resolution candidates were produced");
        ResolutionStatus reportStatus = units.stream().anyMatch(unit -> unit.status() == ResolutionStatus.INVALID)
                ? ResolutionStatus.INVALID
                : units.stream().anyMatch(unit -> unit.status() == ResolutionStatus.PARTIAL)
                        || units.isEmpty() ? ResolutionStatus.PARTIAL : ResolutionStatus.COMPLETE;
        ScenarioPackage scenarioPackage = ScenarioPackage.publish(
                bundle.id(), bundle.currentRevision().revision(), fingerprint,
                bundle.currentRevision().documents(), units,
                new ScenarioCompilationReport(reportStatus, warnings));
        repository.save(scenarioPackage);
        return scenarioPackage;
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
                                    && excerpt.text().toLowerCase().contains(candidate.sourceQuote().toLowerCase())));
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
            case SKILL_ABILITY_CHECK, SAVING_THROW, PASSIVE_THRESHOLD, ATTACK_ROLL, OPPOSED_CHECK -> {
                if (candidate.abilityOrSkill() == null || candidate.abilityOrSkill().isBlank()) {
                    incomplete.add("ability or skill is missing");
                }
                validateDc(candidate.dc(), invalid, incomplete);
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
            value.append('|').append(candidate);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String key(Object documentId, long extractionVersion) {
        return documentId + ":" + extractionVersion;
    }
}
