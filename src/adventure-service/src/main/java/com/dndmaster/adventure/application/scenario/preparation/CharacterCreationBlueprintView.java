package com.dndmaster.adventure.application.scenario.preparation;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        List<NodeView> roots,
        String edition) {
    public CharacterCreationBlueprintView(boolean available, String summary, int rulebookDocumentCount,
                                          int storybookDocumentCount, List<String> diagnostics, long revision,
                                          List<FieldView> fields, String status, List<NodeView> roots) {
        this(available, summary, rulebookDocumentCount, storybookDocumentCount, diagnostics, revision, fields,
                status, roots, "DND_5E_2014");
    }
    public CharacterCreationBlueprintView {
        diagnostics = List.copyOf(diagnostics);
        fields = List.copyOf(fields);
        status = status == null ? "DRAFT" : status;
        roots = List.copyOf(roots);
        edition = edition == null || edition.isBlank() ? "DND_5E_2014" : edition;
    }

    public CharacterCreationBlueprintView(boolean available, String summary, int rulebookDocumentCount,
                                          int storybookDocumentCount, List<String> diagnostics) {
        this(available, summary, rulebookDocumentCount, storybookDocumentCount, diagnostics, 1, List.of(), "READY", List.of());
    }

    public static CharacterCreationBlueprintView blocked(List<String> diagnostics) {
        return new CharacterCreationBlueprintView(false, null, 0, 0, diagnostics, 0, List.of(), "NEEDS_REVIEW", List.of());
    }

    @JsonProperty("characterSheetTree")
    public List<NodeView> characterSheetTree() {
        return roots;
    }

    public record FieldView(String key, List<String> options, boolean required, String sourceType,
                            String inputStatus, List<String> diagnostics, String inputMode, String value,
                            List<String> suggestions, String sourceQuote,
                            List<SourceReferenceView> evidence, List<OptionDetailView> optionDetails) {
        public FieldView(String key, List<String> options, boolean required, String sourceType,
                         String inputStatus, List<String> diagnostics, String inputMode, String value,
                         List<String> suggestions, String sourceQuote, List<SourceReferenceView> evidence) {
            this(key, options, required, sourceType, inputStatus, diagnostics, inputMode, value, suggestions,
                    sourceQuote, evidence, List.of());
        }
        public FieldView(String key, List<String> options, boolean required, String sourceType,
                         String inputStatus, List<String> diagnostics) {
            this(key, options, required, sourceType, inputStatus, diagnostics,
                    options.isEmpty() ? "FREE_TEXT" : "SINGLE_SELECT", null, List.of(), "", List.of());
        }

        public record SourceReferenceView(String knowledgeDocumentId, long extractionVersion, String locator) {}
        public record OptionDetailView(String value, String label, String description, String sourceQuote,
                                       List<SourceReferenceView> evidence) {
            public OptionDetailView { evidence = List.copyOf(evidence); }
        }

        public FieldView {
            options = List.copyOf(options);
            diagnostics = List.copyOf(diagnostics);
            suggestions = List.copyOf(suggestions);
            sourceQuote = sourceQuote == null ? "" : sourceQuote;
            evidence = List.copyOf(evidence);
            optionDetails = List.copyOf(optionDetails);
            value = value == null || value.isBlank() ? null : value;
        }
    }

    public record NodeView(String id, String parentId, String key, String label, String inputMode, String value,
                           List<String> options, List<String> suggestions, String status, boolean allowUserAddChild,
                           String confidence, String sourceQuote, List<String> diagnostics,
                           List<FieldView.SourceReferenceView> sourceEvidence, List<NodeView> children,
                           List<FieldView.OptionDetailView> optionDetails) {
        public NodeView(String id, String parentId, String key, String label, String inputMode, String value,
                        List<String> options, List<String> suggestions, String status, boolean allowUserAddChild,
                        String confidence, String sourceQuote, List<String> diagnostics,
                        List<FieldView.SourceReferenceView> sourceEvidence, List<NodeView> children) {
            this(id, parentId, key, label, inputMode, value, options, suggestions, status, allowUserAddChild,
                    confidence, sourceQuote, diagnostics, sourceEvidence, children, List.of());
        }
        public NodeView {
            options = List.copyOf(options);
            suggestions = List.copyOf(suggestions);
            diagnostics = List.copyOf(diagnostics);
            sourceEvidence = List.copyOf(sourceEvidence);
            children = List.copyOf(children);
            optionDetails = List.copyOf(optionDetails);
        }
    }
}
