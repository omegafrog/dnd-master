package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
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
        Objects.requireNonNull(bundle, "bundle must not be null");
        List<ResolutionCandidate> requested = new ArrayList<>(Objects.requireNonNull(candidates, "candidates must not be null"));
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
                .map(candidate -> validate(candidate, documents))
                .toList();
        List<String> warnings = units.stream()
                .flatMap(unit -> unit.validationMessages().stream())
                .toList();
        ResolutionStatus reportStatus = units.stream().anyMatch(unit -> unit.status() == ResolutionStatus.INVALID)
                ? ResolutionStatus.INVALID
                : units.stream().anyMatch(unit -> unit.status() == ResolutionStatus.PARTIAL)
                        ? ResolutionStatus.PARTIAL : ResolutionStatus.COMPLETE;
        ScenarioPackage scenarioPackage = ScenarioPackage.publish(
                bundle.id(), bundle.currentRevision().revision(), fingerprint,
                bundle.currentRevision().documents(), units,
                new ScenarioCompilationReport(reportStatus, warnings));
        repository.save(scenarioPackage);
        return scenarioPackage;
    }

    private static ScenarioResolutionUnit validate(
            ResolutionCandidate candidate, Map<String, ScenarioBundleDocumentSelection> documents) {
        List<String> invalid = new ArrayList<>();
        List<String> incomplete = new ArrayList<>();
        if (candidate == null) {
            return new ScenarioResolutionUnit(
                    null, null, null, null, ResolutionVisibility.GM_REFERENCE,
                    "", List.of(), "", ResolutionStatus.INVALID, List.of("candidate is null"));
        }
        if (candidate.kind() == null) invalid.add("resolution kind is missing");
        if (candidate.visibility() == null) incomplete.add("visibility is missing");
        if (candidate.provenance() == null || candidate.provenance().isBlank()) incomplete.add("provenance is missing");
        if (candidate.sourceQuote() == null || candidate.sourceQuote().isBlank()) incomplete.add("source quote is missing");
        if (candidate.sourceRefs() == null || candidate.sourceRefs().isEmpty()) {
            invalid.add("source reference is missing");
        } else {
            for (ScenarioSourceReference ref : candidate.sourceRefs()) {
                ScenarioBundleDocumentSelection sourceDocument = ref == null
                        ? null
                        : documents.get(key(ref.knowledgeDocumentId(), ref.extractionVersion()));
                if (sourceDocument == null || ref.locator().isBlank()) {
                    invalid.add("source reference is outside bundle revision");
                } else if (candidate.visibility() == ResolutionVisibility.PLAYER_SAFE
                        && sourceDocument.role() != com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.HANDOUT
                        && sourceDocument.role() != com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.CHARACTER_SHEET) {
                    invalid.add("GM reference cannot be player safe");
                }
            }
        }
        if (candidate.kind() == ResolutionKind.DICE_ROLL) {
            if (!validDice(candidate.diceExpression())) {
                invalid.add("dice expression is invalid");
            }
        } else if (candidate.kind() != null) {
            if (candidate.abilityOrSkill() == null || candidate.abilityOrSkill().isBlank()) {
                incomplete.add("ability or skill is missing");
            }
            if (candidate.dc() == null) {
                incomplete.add("DC is missing");
            } else if (candidate.dc() < 0 || candidate.dc() > 100) {
                invalid.add("DC is outside supported range");
            }
        }
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
                candidate.sourceRefs() == null ? List.of() : candidate.sourceRefs(),
                candidate.provenance() == null ? "" : candidate.provenance(),
                status,
                messages);
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
