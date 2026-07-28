package com.dndmaster.adventure.application.scenario.preparation;

import java.util.List;

public record CharacterCreationBlueprintView(
        boolean available,
        String summary,
        int rulebookDocumentCount,
        int storybookDocumentCount,
        List<String> diagnostics,
        long revision,
        List<FieldView> fields,
        String status) {
    public CharacterCreationBlueprintView {
        diagnostics = List.copyOf(diagnostics);
        fields = List.copyOf(fields);
        status = status == null ? "DRAFT" : status;
    }

    public CharacterCreationBlueprintView(boolean available, String summary, int rulebookDocumentCount,
                                          int storybookDocumentCount, List<String> diagnostics) {
        this(available, summary, rulebookDocumentCount, storybookDocumentCount, diagnostics, 1, List.of(), "READY");
    }

    public static CharacterCreationBlueprintView blocked(List<String> diagnostics) {
        return new CharacterCreationBlueprintView(false, null, 0, 0, diagnostics, 0, List.of(), "NEEDS_REVIEW");
    }

    public record FieldView(String key, List<String> options, boolean required, String sourceType,
                            String inputStatus, List<String> diagnostics, String inputMode,
                            List<String> suggestions, String sourceQuote,
                            List<com.dndmaster.adventure.domain.scenario.ScenarioSourceReference> evidence) {
        public FieldView(String key, List<String> options, boolean required, String sourceType,
                         String inputStatus, List<String> diagnostics) {
            this(key, options, required, sourceType, inputStatus, diagnostics,
                    options.isEmpty() ? "FREE_TEXT" : "SINGLE_SELECT", List.of(), "", List.of());
        }

        public FieldView {
            options = List.copyOf(options);
            diagnostics = List.copyOf(diagnostics);
            suggestions = List.copyOf(suggestions);
            sourceQuote = sourceQuote == null ? "" : sourceQuote;
            evidence = List.copyOf(evidence);
        }
    }
}
