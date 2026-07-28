package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

/** Immutable, versioned character-creation contract compiled with a scenario package. */
public record CharacterCreationBlueprint(
        long revision,
        CharacterCreationBlueprintStatus status,
        List<Field> fields,
        List<String> diagnostics) {
    public CharacterCreationBlueprint {
        if (revision <= 0) throw new IllegalArgumentException("blueprint revision must be positive");
        status = Objects.requireNonNull(status, "status must not be null");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields must not be null"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    }


    public Field field(String key) {
        return fields.stream().filter(field -> field.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown blueprint field: " + key));
    }

    public CharacterCreationBlueprint resolve(String key, String value) {
        if (status == CharacterCreationBlueprintStatus.PUBLISHED) {
            throw new IllegalStateException("published blueprint is immutable");
        }
        if (value == null || value.isBlank()) throw new IllegalArgumentException("blueprint value must not be blank");
        List<Field> next = new ArrayList<>();
        boolean found = false;
        for (Field field : fields) {
            if (!field.key().equals(key)) { next.add(field); continue; }
            if (!field.options().isEmpty() && !field.options().contains(value)) {
                throw new IllegalArgumentException("value is not a blueprint option: " + value);
            }
            next.add(new Field(field.key(), List.of(value), field.required(), field.sourceType(), field.evidence(),
                    "USER_CONFIRMED", List.of()));
            found = true;
        }
        if (!found) throw new IllegalArgumentException("unknown blueprint field: " + key);
        CharacterCreationBlueprintStatus nextStatus = next.stream().anyMatch(field ->
                !field.diagnostics().isEmpty() || field.inputStatus().equals("MANUAL_INPUT_REQUIRED"))
                ? CharacterCreationBlueprintStatus.NEEDS_REVIEW : CharacterCreationBlueprintStatus.READY;
        List<String> nextDiagnostics = diagnostics.stream()
                .filter(diagnostic -> !diagnostic.startsWith(key + ":"))
                .toList();
        return new CharacterCreationBlueprint(revision + 1, nextStatus, next, nextDiagnostics);
    }

    public CharacterCreationBlueprint publish() {
        if (status != CharacterCreationBlueprintStatus.READY) {
            throw new IllegalStateException("blueprint has unresolved review items");
        }
        return new CharacterCreationBlueprint(revision + 1, CharacterCreationBlueprintStatus.PUBLISHED, fields, diagnostics);
    }

    public record Field(String key, List<String> options, boolean required, String sourceType,
                        List<ScenarioSourceReference> evidence, String inputStatus, List<String> diagnostics) {
        public Field {
            key = Objects.requireNonNull(key, "field key must not be null");
            options = List.copyOf(Objects.requireNonNull(options, "options must not be null"));
            sourceType = Objects.requireNonNull(sourceType, "source type must not be null");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
            inputStatus = Objects.requireNonNull(inputStatus, "input status must not be null");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        }
    }
}
