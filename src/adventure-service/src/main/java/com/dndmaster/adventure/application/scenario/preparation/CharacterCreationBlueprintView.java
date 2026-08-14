package com.dndmaster.adventure.application.scenario.preparation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

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
        String edition,
        RulebookBaseSchemaView baseSchema,
        List<StorybookProposalView> storybookProposals,
        StorybookExtractionState storybookExtractionState) {
    public CharacterCreationBlueprintView(boolean available, String summary, int rulebookDocumentCount,
                                          int storybookDocumentCount, List<String> diagnostics, long revision,
                                          List<FieldView> fields, String status, List<NodeView> roots) {
        this(available, summary, rulebookDocumentCount, storybookDocumentCount, diagnostics, revision, fields,
                status, roots, "DND_5E_2014", RulebookBaseSchemaView.from(fields), List.of(), StorybookExtractionState.NO_PROPOSALS);
    }
    public CharacterCreationBlueprintView(boolean available, String summary, int rulebookDocumentCount,
                                          int storybookDocumentCount, List<String> diagnostics, long revision,
                                          List<FieldView> fields, String status, List<NodeView> roots, String edition) {
        this(available, summary, rulebookDocumentCount, storybookDocumentCount, diagnostics, revision, fields,
                status, roots, edition, new RulebookBaseSchemaView(edition, RulebookBaseSchemaView.from(fields).fields()),
                List.of(), StorybookExtractionState.NO_PROPOSALS);
    }
    public CharacterCreationBlueprintView {
        diagnostics = List.copyOf(diagnostics);
        fields = List.copyOf(fields);
        status = status == null ? "DRAFT" : status;
        roots = List.copyOf(roots);
        edition = edition == null || edition.isBlank() ? "DND_5E_2014" : edition;
        baseSchema = Objects.requireNonNull(baseSchema, "base schema must not be null");
        storybookProposals = List.copyOf(storybookProposals);
        storybookExtractionState = Objects.requireNonNull(storybookExtractionState, "storybook extraction state must not be null");
    }

    public CharacterCreationBlueprintView(boolean available, String summary, int rulebookDocumentCount,
                                          int storybookDocumentCount, List<String> diagnostics) {
        this(available, summary, rulebookDocumentCount, storybookDocumentCount, diagnostics, 1, List.of(), "READY", List.of());
    }

    public static CharacterCreationBlueprintView blocked(List<String> diagnostics) {
        return blocked(diagnostics, StorybookExtractionState.NO_PROPOSALS);
    }

    public static CharacterCreationBlueprintView blocked(List<String> diagnostics, StorybookExtractionState extractionState) {
        return new CharacterCreationBlueprintView(false, null, 0, 0, diagnostics, 0, List.of(), "NEEDS_REVIEW", List.of(),
                "DND_5E_2014", RulebookBaseSchemaView.from(List.of()), List.of(), extractionState);
    }

    public record RulebookBaseSchemaView(String edition, List<FieldView> fields) {
        public RulebookBaseSchemaView {
            edition = edition == null || edition.isBlank() ? "DND_5E_2014" : edition;
            fields = List.copyOf(fields);
        }

        public static RulebookBaseSchemaView from(List<FieldView> fields) {
            return new RulebookBaseSchemaView("DND_5E_2014", fields.stream()
                    .filter(field -> "RULEBOOK".equalsIgnoreCase(field.sourceType())
                            || "TEMPLATE".equalsIgnoreCase(field.sourceType())).toList());
        }
    }

    public enum StorybookExtractionState {
        NO_PROPOSALS, PROPOSALS_AVAILABLE, EXTRACTION_FAILED, INSUFFICIENT_EVIDENCE,
        EXTRACTION_PARTIAL_AWAITING_CONFIRMATION, EXTRACTION_PARTIAL_CONFIRMED, EXTRACTION_MIXED
    }

    public record StorybookProposalView(String proposalId, String key, String label, String description,
                                        SourceDocument sourceDocument, String sourceQuote,
                                        List<SourceEvidence> evidence, String decisionState,
                                        String readinessState) {
        public StorybookProposalView {
            proposalId = Objects.requireNonNull(proposalId, "proposal id must not be null");
            key = Objects.requireNonNull(key, "proposal key must not be null");
            label = label == null ? "" : label;
            description = description == null ? "" : description;
            sourceQuote = sourceQuote == null ? "" : sourceQuote;
            evidence = List.copyOf(evidence);
            // 036-1 has no decision command or persistence boundary yet; the safe read-model
            // default is UNDECIDED rather than inferring a decision from diagnostics or text.
            decisionState = decisionState == null ? "UNDECIDED" : decisionState;
            readinessState = readinessState == null ? "READY" : readinessState;
        }

        /** Identity is tied to the grounded source revision and field key, never extracted text. */
        public static String stableId(String knowledgeDocumentId, long extractionVersion, String fieldKey) {
            return java.util.UUID.nameUUIDFromBytes((knowledgeDocumentId + "|" + extractionVersion + "|" + fieldKey)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        }


        public record SourceDocument(String knowledgeDocumentId, String originalFilename, long extractionVersion) {}
        public record SourceEvidence(String locator, String excerpt) {
            public SourceEvidence {
                locator = locator == null ? "" : locator;
                excerpt = excerpt == null ? "" : excerpt;
            }
        }
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
