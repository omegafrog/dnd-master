package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

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
