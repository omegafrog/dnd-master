package com.dndmaster.adventure.application.scenario.blueprint;

import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.CharacterInputTagCandidate;
import com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprintStatus;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compiles extracted, source-grounded character fields into a versioned contract. */
public final class CharacterCreationBlueprintCompiler {
    public CharacterCreationBlueprint compileAgent(long revision, List<CharacterInputTagCandidate> candidates) {
        Objects.requireNonNull(candidates, "character tag candidates must not be null");
        List<CharacterInputTagCandidate> grounded = candidates.stream()
                .filter(candidate -> candidate != null && !candidate.evidence().isEmpty())
                .toList();
        CharacterCreationBlueprint compiled = compile(revision, grounded.stream()
                .map(candidate -> new FieldCandidate(effectiveKey(candidate), candidate.options(), true, candidate.sourceType(),
                        candidate.evidence().getFirst(), candidate.sourceQuote(), candidate.inputMode(), candidate.suggestions(),
                        toFieldOptionDetails(candidate.optionDetails())))
                .toList());
        Map<String, CharacterInputTagCandidate> byKey = grounded.stream().collect(java.util.stream.Collectors.toMap(
                CharacterCreationBlueprintCompiler::effectiveKey, candidate -> candidate,
                (first, second) -> sourcePriority(first.sourceType()) >= sourcePriority(second.sourceType()) ? first : second));
        List<CharacterCreationBlueprint.Field> fields = new ArrayList<>(compiled.fields().stream().map(field -> {
            CharacterInputTagCandidate candidate = byKey.get(field.key());
            return candidate == null ? field : new CharacterCreationBlueprint.Field(field.key(), field.options(), candidate.required(),
                    field.sourceType(), candidate.evidence(), field.inputStatus(), field.diagnostics(), field.inputMode(),
                    field.suggestions(), field.sourceQuote(), candidate.label(), field.value(), field.nodeId(), field.parentNodeId(), candidate.confidence(),
                    toFieldOptionDetails(candidate.optionDetails()));
        }).toList());
        CharacterCreationBlueprintStatus status = fields.stream().anyMatch(field ->
                !field.diagnostics().isEmpty() || field.inputStatus().equals("MANUAL_INPUT_REQUIRED"))
                ? CharacterCreationBlueprintStatus.NEEDS_REVIEW : compiled.status();
        CharacterCreationBlueprint extracted = new CharacterCreationBlueprint(
                compiled.revision(), status, fields, compiled.diagnostics());
        return DndCharacterCreationTemplate.apply("DND_5E_2014", extracted);
    }

    private static String effectiveKey(CharacterInputTagCandidate candidate) {
        return candidate.parentKey() != null && !candidate.key().contains(".")
                ? candidate.parentKey() + "." + candidate.key() : candidate.key();
    }

    public CharacterCreationBlueprint compile(long revision, List<FieldCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Map<String, List<FieldCandidate>> byKey = new LinkedHashMap<>();
        for (FieldCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "field candidate must not be null");
            byKey.computeIfAbsent(candidate.key(), ignored -> new ArrayList<>()).add(candidate);
        }
        Map<String, String> nodeIds = new LinkedHashMap<>();
        for (String key : byKey.keySet()) {
            String path = "";
            for (String part : key.split("\\.")) {
                path = path.isEmpty() ? part : path + "." + part;
                nodeIds.computeIfAbsent(path, ignored -> java.util.UUID.randomUUID().toString());
            }
        }

        List<CharacterCreationBlueprint.Field> fields = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (Map.Entry<String, List<FieldCandidate>> entry : byKey.entrySet()) {
            List<FieldCandidate> values = entry.getValue();
            boolean hasExtractedHandout = values.stream()
                    .anyMatch(candidate -> candidate.sourceType().equals("HANDOUT") && candidate.extracted());
            boolean hasExtractedStorybook = values.stream()
                    .anyMatch(candidate -> candidate.sourceType().equals("STORYBOOK") && candidate.extracted());
            boolean hasExtractedRulebook = values.stream()
                    .anyMatch(candidate -> candidate.sourceType().equals("RULEBOOK") && candidate.extracted());
            String selectedSource = hasExtractedStorybook ? "STORYBOOK"
                    : hasExtractedHandout ? "HANDOUT"
                    : hasExtractedRulebook ? "RULEBOOK"
                    : values.stream().max(java.util.Comparator.comparingInt(candidate -> sourcePriority(candidate.sourceType())))
                            .map(FieldCandidate::sourceType).orElse("RULEBOOK");
            List<FieldCandidate> selected = values.stream()
                    .filter(candidate -> candidate.sourceType().equals(selectedSource)).toList();
            LinkedHashSet<String> options = new LinkedHashSet<>();
            for (FieldCandidate candidate : selected) {
                options.addAll(candidate.options());
            }
            List<CharacterCreationBlueprint.Field.OptionDetail> optionDetails = selected.stream()
                    .flatMap(candidate -> candidate.optionDetails().stream()).distinct().toList();
            List<FieldCandidate> extracted = values.stream().filter(FieldCandidate::extracted).toList();
            boolean crossSourceConflict = extracted.stream()
                    .map(FieldCandidate::sourceType).distinct().count() > 1
                    && extracted.stream().map(FieldCandidate::options).distinct().count() > 1;
            List<ScenarioSourceReference> evidence = (crossSourceConflict ? extracted : selected).stream()
                    .map(FieldCandidate::evidence).toList();
            boolean missing = selected.stream().anyMatch(candidate -> !candidate.extracted());
            boolean conflict = selected.stream().map(FieldCandidate::options).distinct().count() > 1;
            if (crossSourceConflict || conflict) {
                for (FieldCandidate candidate : extracted) options.addAll(candidate.options());
                optionDetails = extracted.stream().flatMap(candidate -> candidate.optionDetails().stream()).distinct().toList();
            }
            List<String> fieldDiagnostics = new ArrayList<>();
            if (crossSourceConflict) {
                List<String> sourceTypes = java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(selectedSource),
                                extracted.stream().map(FieldCandidate::sourceType)
                                        .filter(source -> !source.equals(selectedSource)))
                        .distinct().map(String::toLowerCase).toList();
                String sources = String.join("/", sourceTypes);
                fieldDiagnostics.add("conflicting " + sources + " values");
            }
            else if (conflict) fieldDiagnostics.add("conflicting " + selectedSource.toLowerCase() + " values");
            if (missing) fieldDiagnostics.add("manual input required");
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
            String inputStatus = (crossSourceConflict || conflict || modeConflict) ? "CONFLICT_REVIEW"
                    : missing ? "MANUAL_INPUT_REQUIRED" : "EXTRACTED";
            String sourceQuote = selected.stream().map(FieldCandidate::sourceQuote)
                    .filter(quote -> !quote.isBlank()).findFirst().orElse("");
            String fixedValue = inputMode == InputMode.FIXED_VALUE && options.size() == 1
                    ? options.iterator().next() : null;
            CharacterCreationBlueprint.Field field = new CharacterCreationBlueprint.Field(
                    entry.getKey(), List.copyOf(options), values.stream().anyMatch(FieldCandidate::required),
                    selected.stream().anyMatch(candidate -> candidate.sourceType().equals("STORYBOOK"))
                            ? "STORYBOOK" : selected.stream().anyMatch(candidate -> candidate.sourceType().equals("HANDOUT"))
                                    ? "HANDOUT" : "RULEBOOK", evidence, inputStatus, fieldDiagnostics,
                    inputMode, suggestions, sourceQuote, entry.getKey(), fixedValue, nodeIds.get(entry.getKey()),
                    parentNodeId(entry.getKey(), nodeIds), "HIGH", optionDetails);
            fields.add(field);
            diagnostics.addAll(fieldDiagnostics.stream().map(message -> entry.getKey() + ": " + message).toList());
        }
        CharacterCreationBlueprintStatus status = diagnostics.isEmpty()
                ? CharacterCreationBlueprintStatus.READY : CharacterCreationBlueprintStatus.NEEDS_REVIEW;
        return new CharacterCreationBlueprint(revision, status, fields, diagnostics);
    }

    private static int sourcePriority(String sourceType) {
        return switch (sourceType) {
            case "STORYBOOK" -> 3;
            case "HANDOUT" -> 2;
            case "RULEBOOK" -> 1;
            default -> 0;
        };
    }

    private static List<CharacterCreationBlueprint.Field.OptionDetail> toFieldOptionDetails(
            List<CharacterInputTagCandidate.OptionDetail> details) {
        return (details == null ? List.<CharacterInputTagCandidate.OptionDetail>of() : details).stream()
                .map(detail -> new CharacterCreationBlueprint.Field.OptionDetail(detail.value(), detail.label(), detail.description(),
                        detail.sourceQuote(), detail.evidence())).toList();
    }

    private static String parentNodeId(String key, Map<String, String> nodeIds) {
        int separator = key.lastIndexOf('.');
        return separator < 0 ? null : nodeIds.get(key.substring(0, separator));
    }

    public record FieldCandidate(
            String key,
            List<String> options,
            boolean extracted,
            String sourceType,
            ScenarioSourceReference evidence,
            String sourceQuote,
            InputMode inputMode,
            List<String> suggestions,
            List<CharacterCreationBlueprint.Field.OptionDetail> optionDetails) {
        public FieldCandidate(String key, List<String> options, boolean extracted, String sourceType,
                              ScenarioSourceReference evidence, String sourceQuote) {
            this(key, options, extracted, sourceType, evidence, sourceQuote,
                    options.isEmpty() ? InputMode.FREE_TEXT : InputMode.SINGLE_SELECT, List.of(), List.of());
        }

        public FieldCandidate(String key, List<String> options, boolean extracted, String sourceType,
                              ScenarioSourceReference evidence, String sourceQuote, InputMode inputMode,
                              List<String> suggestions) {
            this(key, options, extracted, sourceType, evidence, sourceQuote, inputMode, suggestions, List.of());
        }

        public FieldCandidate {
            key = Objects.requireNonNull(key, "field key must not be null").trim();
            if (key.isBlank()) throw new IllegalArgumentException("field key must not be blank");
            options = List.copyOf(Objects.requireNonNull(options, "options must not be null"));
            sourceType = Objects.requireNonNull(sourceType, "source type must not be null").toUpperCase();
            if (!sourceType.equals("HANDOUT") && !sourceType.equals("STORYBOOK") && !sourceType.equals("RULEBOOK")) {
                throw new IllegalArgumentException("unsupported blueprint source type: " + sourceType);
            }
            evidence = Objects.requireNonNull(evidence, "evidence must not be null");
            sourceQuote = Objects.requireNonNull(sourceQuote, "source quote must not be null");
            inputMode = Objects.requireNonNull(inputMode, "input mode must not be null");
            suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions must not be null"));
            optionDetails = List.copyOf(Objects.requireNonNull(optionDetails, "option details must not be null"));
            if (inputMode == InputMode.FREE_TEXT && !options.isEmpty()) {
                throw new IllegalArgumentException("free-text candidate cannot have options");
            }
            for (CharacterCreationBlueprint.Field.OptionDetail detail : optionDetails) {
                if (!options.contains(detail.value())) throw new IllegalArgumentException("option detail is not a candidate option");
            }
        }

        public boolean required() { return true; }
    }
}
