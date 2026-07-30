package com.dndmaster.adventure.application.scenario.blueprint;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;

/** Agent boundary for source-grounded, dynamic character input tags. */
public interface CharacterInputTagExtractionPort {
    List<CharacterInputTagCandidate> extract(Request request);

    record Request(String operationId, List<SourceExcerpt> excerpts,
                   String schemaVersion, String promptVersion, String instruction) {
        public Request(String operationId, List<SourceExcerpt> excerpts,
                       String schemaVersion, String promptVersion) {
            this(operationId, excerpts, schemaVersion, promptVersion, "");
        }
        public Request {
            if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operation id required");
            excerpts = List.copyOf(excerpts == null ? List.of() : excerpts);
            if (schemaVersion == null || schemaVersion.isBlank()) throw new IllegalArgumentException("schema version required");
            if (promptVersion == null || promptVersion.isBlank()) throw new IllegalArgumentException("prompt version required");
            instruction = instruction == null ? "" : instruction.trim();
        }
    }

    record SourceExcerpt(KnowledgeDocumentId documentId, long extractionVersion, String locator, String text) {}

    record CharacterInputTagCandidate(String key, String label, String parentKey, boolean required,
                                      InputMode inputMode, List<String> options, List<String> suggestions,
                                      String confidence, List<ScenarioSourceReference> evidence,
                                      String sourceQuote, String sourceType, List<OptionDetail> optionDetails) {
        public CharacterInputTagCandidate(String key, String label, String parentKey, boolean required,
                                          InputMode inputMode, List<String> options, List<String> suggestions,
                                          String confidence, List<ScenarioSourceReference> evidence,
                                          String sourceQuote, String sourceType) {
            this(key, label, parentKey, required, inputMode, options, suggestions, confidence, evidence,
                    sourceQuote, sourceType, List.of());
        }
        public CharacterInputTagCandidate {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("candidate key required");
            label = label == null || label.isBlank() ? key : label;
            parentKey = parentKey == null || parentKey.isBlank() ? null : parentKey;
            inputMode = inputMode == null ? InputMode.FREE_TEXT : inputMode;
            options = List.copyOf(options == null ? List.of() : options);
            suggestions = List.copyOf(suggestions == null ? List.of() : suggestions);
            confidence = confidence == null || confidence.isBlank() ? "LOW" : confidence.toUpperCase();
            evidence = List.copyOf(evidence == null ? List.of() : evidence);
            sourceQuote = sourceQuote == null ? "" : sourceQuote;
            sourceType = sourceType == null || sourceType.isBlank() ? "RULEBOOK" : sourceType.toUpperCase();
            optionDetails = List.copyOf(optionDetails == null ? List.of() : optionDetails);
            if (inputMode == InputMode.FREE_TEXT && !options.isEmpty()) throw new IllegalArgumentException("free-text candidate cannot have options");
            if (inputMode == InputMode.FREE_TEXT && !optionDetails.isEmpty()) throw new IllegalArgumentException("free-text candidate cannot have option details");
            for (OptionDetail detail : optionDetails) {
                if (!options.contains(detail.value())) throw new IllegalArgumentException("option detail is not a candidate option");
            }
            if (!evidence.isEmpty() && sourceQuote.isBlank()) throw new IllegalArgumentException("evidence candidate requires source quote");
        }

        public record OptionDetail(String value, String label, String description, String sourceQuote,
                                   List<ScenarioSourceReference> evidence) {
            public OptionDetail {
                if (value == null || value.isBlank()) throw new IllegalArgumentException("option value required");
                label = label == null || label.isBlank() ? value : label;
                description = description == null ? "" : description;
                sourceQuote = sourceQuote == null ? "" : sourceQuote;
                evidence = List.copyOf(evidence == null ? List.of() : evidence);
                if (!evidence.isEmpty() && sourceQuote.isBlank()) throw new IllegalArgumentException("option evidence requires source quote");
            }
        }
    }
}
