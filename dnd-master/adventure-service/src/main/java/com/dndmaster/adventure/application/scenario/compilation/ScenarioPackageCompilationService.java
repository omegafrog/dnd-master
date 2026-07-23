package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScenarioPackageCompilationService {
    private static final String DICE_PATTERN = "(?i)\\d+d\\d+(?:\\s*[+-]\\s*\\d+)?";
    private final ScenarioPackageRepository repository;

    public ScenarioPackageCompilationService(ScenarioPackageRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public ScenarioPackage compile(ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates) {
        Objects.requireNonNull(bundle, "bundle must not be null");
        List<ResolutionCandidate> requested = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
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
        ScenarioPackage scenarioPackage = ScenarioPackage.publish(
                bundle.id(), bundle.currentRevision().revision(), fingerprint, units);
        repository.save(scenarioPackage);
        return scenarioPackage;
    }

    private static ScenarioResolutionUnit validate(
            ResolutionCandidate candidate, Map<String, ScenarioBundleDocumentSelection> documents) {
        List<String> invalid = new ArrayList<>();
        List<String> incomplete = new ArrayList<>();
        if (candidate.kind() == null) invalid.add("resolution kind is missing");
        if (candidate.visibility() == null) incomplete.add("visibility is missing");
        if (candidate.provenance() == null || candidate.provenance().isBlank()) incomplete.add("provenance is missing");
        if (candidate.sourceQuote() == null || candidate.sourceQuote().isBlank()) incomplete.add("source quote is missing");
        if (candidate.sourceRefs() == null || candidate.sourceRefs().isEmpty()) {
            invalid.add("source reference is missing");
        } else {
            for (ScenarioSourceReference ref : candidate.sourceRefs()) {
                if (!documents.containsKey(key(ref.knowledgeDocumentId(), ref.extractionVersion()))) {
                    invalid.add("source reference is outside bundle revision");
                }
            }
        }
        if (candidate.kind() == ResolutionKind.DICE_ROLL) {
            if (candidate.diceExpression() == null || !candidate.diceExpression().matches(DICE_PATTERN)) {
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
                status,
                messages);
    }

    private static String fingerprint(ScenarioSourceBundle bundle, List<ResolutionCandidate> candidates) {
        StringBuilder value = new StringBuilder(bundle.id().value().toString())
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
