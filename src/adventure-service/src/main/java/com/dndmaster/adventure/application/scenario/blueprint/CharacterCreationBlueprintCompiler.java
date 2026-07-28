package com.dndmaster.adventure.application.scenario.blueprint;

import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Compiles extracted, source-grounded character fields into a versioned contract. */
public final class CharacterCreationBlueprintCompiler {
    private static final Set<String> SUPPORTED_FIELDS = Set.of(
            "name", "race", "class", "background", "starting_ability_scores", "level");

    public CharacterCreationBlueprint compile(long revision, List<FieldCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Map<String, List<FieldCandidate>> byKey = new LinkedHashMap<>();
        for (FieldCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "field candidate must not be null");
            if (!SUPPORTED_FIELDS.contains(candidate.key())) {
                throw new IllegalArgumentException("custom character field is not supported: " + candidate.key());
            }
            byKey.computeIfAbsent(candidate.key(), ignored -> new ArrayList<>()).add(candidate);
        }

        List<CharacterCreationBlueprint.Field> fields = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (Map.Entry<String, List<FieldCandidate>> entry : byKey.entrySet()) {
            List<FieldCandidate> values = entry.getValue();
            boolean hasExtractedHandout = values.stream()
                    .anyMatch(candidate -> candidate.sourceType().equals("HANDOUT") && candidate.extracted());
            boolean hasHandout = values.stream().anyMatch(candidate -> candidate.sourceType().equals("HANDOUT"));
            boolean hasExtractedStorybook = values.stream()
                    .anyMatch(candidate -> candidate.sourceType().equals("STORYBOOK") && candidate.extracted());
            boolean hasExtractedRulebook = values.stream()
                    .anyMatch(candidate -> candidate.sourceType().equals("RULEBOOK") && candidate.extracted());
            List<FieldCandidate> selected = values.stream()
                    .filter(candidate -> hasExtractedStorybook
                            ? candidate.sourceType().equals("STORYBOOK")
                            : hasExtractedHandout
                            ? candidate.sourceType().equals("HANDOUT")
                            : hasExtractedRulebook
                                    ? candidate.sourceType().equals("RULEBOOK")
                                    : !hasHandout || candidate.sourceType().equals("HANDOUT"))
                    .toList();
            LinkedHashSet<String> options = new LinkedHashSet<>();
            List<ScenarioSourceReference> evidence = new ArrayList<>();
            for (FieldCandidate candidate : selected) {
                options.addAll(candidate.options());
                evidence.add(candidate.evidence());
            }
            boolean missing = selected.stream().anyMatch(candidate -> !candidate.extracted());
            boolean conflict = hasExtractedHandout && selected.stream().map(FieldCandidate::options).distinct().count() > 1;
            List<String> fieldDiagnostics = new ArrayList<>();
            if (conflict) fieldDiagnostics.add("conflicting handout values");
            if (missing) fieldDiagnostics.add("manual input required");
            String inputStatus = missing ? "MANUAL_INPUT_REQUIRED" : "EXTRACTED";
            InputMode inputMode = selected.stream().map(FieldCandidate::inputMode).findFirst()
                    .orElse(InputMode.FREE_TEXT);
            List<String> suggestions = selected.stream().flatMap(candidate -> candidate.suggestions().stream()).distinct().toList();
            boolean modeConflict = selected.stream().map(FieldCandidate::inputMode).distinct().count() > 1;
            if (modeConflict) {
                inputMode = InputMode.FREE_TEXT;
                suggestions = java.util.stream.Stream.concat(suggestions.stream(), options.stream()).distinct().toList();
                options = new LinkedHashSet<>();
                fieldDiagnostics.add("conflicting input modes; manual input required");
            }
            String sourceQuote = selected.stream().map(FieldCandidate::sourceQuote)
                    .filter(quote -> !quote.isBlank()).findFirst().orElse("");
            CharacterCreationBlueprint.Field field = new CharacterCreationBlueprint.Field(
                    entry.getKey(), List.copyOf(options), values.stream().anyMatch(FieldCandidate::required),
                    selected.stream().anyMatch(candidate -> candidate.sourceType().equals("STORYBOOK"))
                            ? "STORYBOOK" : selected.stream().anyMatch(candidate -> candidate.sourceType().equals("HANDOUT"))
                                    ? "HANDOUT" : "RULEBOOK", evidence, inputStatus, fieldDiagnostics,
                    inputMode, suggestions, sourceQuote);
            fields.add(field);
            diagnostics.addAll(fieldDiagnostics.stream().map(message -> entry.getKey() + ": " + message).toList());
        }
        CharacterCreationBlueprintStatus status = diagnostics.isEmpty()
                ? CharacterCreationBlueprintStatus.READY : CharacterCreationBlueprintStatus.NEEDS_REVIEW;
        return new CharacterCreationBlueprint(revision, status, fields, diagnostics);
    }

    public record FieldCandidate(
            String key,
            List<String> options,
            boolean extracted,
            String sourceType,
            ScenarioSourceReference evidence,
            String sourceQuote,
            InputMode inputMode,
            List<String> suggestions) {
        public FieldCandidate(String key, List<String> options, boolean extracted, String sourceType,
                              ScenarioSourceReference evidence, String sourceQuote) {
            this(key, options, extracted, sourceType, evidence, sourceQuote,
                    options.isEmpty() ? InputMode.FREE_TEXT : InputMode.SINGLE_SELECT, List.of());
        }

        public FieldCandidate {
            key = Objects.requireNonNull(key, "field key must not be null").trim();
            options = List.copyOf(Objects.requireNonNull(options, "options must not be null"));
            sourceType = Objects.requireNonNull(sourceType, "source type must not be null").toUpperCase();
            if (!sourceType.equals("HANDOUT") && !sourceType.equals("STORYBOOK") && !sourceType.equals("RULEBOOK")) {
                throw new IllegalArgumentException("unsupported blueprint source type: " + sourceType);
            }
            evidence = Objects.requireNonNull(evidence, "evidence must not be null");
            sourceQuote = Objects.requireNonNull(sourceQuote, "source quote must not be null");
            inputMode = Objects.requireNonNull(inputMode, "input mode must not be null");
            suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions must not be null"));
            if (inputMode == InputMode.FREE_TEXT && !options.isEmpty()) {
                throw new IllegalArgumentException("free-text candidate cannot have options");
            }
        }

        public boolean required() { return true; }
    }
}
