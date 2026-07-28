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
        String status,
        List<NodeView> roots) {
    public CharacterCreationBlueprintView {
        diagnostics = List.copyOf(diagnostics);
        fields = List.copyOf(fields);
        status = status == null ? "DRAFT" : status;
        roots = List.copyOf(roots);
    }

    public CharacterCreationBlueprintView(boolean available, String summary, int rulebookDocumentCount,
                                          int storybookDocumentCount, List<String> diagnostics) {
        this(available, summary, rulebookDocumentCount, storybookDocumentCount, diagnostics, 1, List.of(), "READY", List.of());
    }

    public static CharacterCreationBlueprintView blocked(List<String> diagnostics) {
        return new CharacterCreationBlueprintView(false, null, 0, 0, diagnostics, 0, List.of(), "NEEDS_REVIEW", List.of());
    }

    public record FieldView(String key, List<String> options, boolean required, String sourceType,
                            String inputStatus, List<String> diagnostics, String inputMode,
                            List<String> suggestions, String sourceQuote,
                            List<SourceReferenceView> evidence) {
        public FieldView(String key, List<String> options, boolean required, String sourceType,
                         String inputStatus, List<String> diagnostics) {
            this(key, options, required, sourceType, inputStatus, diagnostics,
                    options.isEmpty() ? "FREE_TEXT" : "SINGLE_SELECT", List.of(), "", List.of());
        }

        public record SourceReferenceView(String knowledgeDocumentId, long extractionVersion, String locator) {}

        public FieldView {
            options = List.copyOf(options);
            diagnostics = List.copyOf(diagnostics);
            suggestions = List.copyOf(suggestions);
            sourceQuote = sourceQuote == null ? "" : sourceQuote;
            evidence = List.copyOf(evidence);
        }
    }

    public record NodeView(String id, String parentId, String key, String label, String inputMode, String value,
                           List<String> options, List<String> suggestions, String status, boolean allowUserAddChild,
                           String confidence, String sourceQuote, List<String> diagnostics,
                           List<FieldView.SourceReferenceView> sourceEvidence, List<NodeView> children) {
        public NodeView {
            options = List.copyOf(options);
            suggestions = List.copyOf(suggestions);
            diagnostics = List.copyOf(diagnostics);
            sourceEvidence = List.copyOf(sourceEvidence);
            children = List.copyOf(children);
        }
    }
}
