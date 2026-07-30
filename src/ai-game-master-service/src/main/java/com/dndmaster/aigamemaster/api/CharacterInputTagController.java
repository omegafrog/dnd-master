package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.CharacterTagCompletionPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public final class CharacterInputTagController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CharacterInputTagController.class);
    private final CharacterTagCompletionPort adapter;
    private final ObjectMapper objectMapper;
    public CharacterInputTagController(CharacterTagCompletionPort adapter, ObjectMapper objectMapper) { this.adapter = adapter; this.objectMapper = objectMapper; }

    @PostMapping("/internal/v1/gm/character-input-tags")
    Response extract(@RequestBody Request request) {
        if (request == null || request.excerpts() == null) return new Response(List.of());
        String prompt = buildPrompt(request);
        boolean focused = request.instruction() != null && !request.instruction().isBlank();
        long startedAt = System.nanoTime();
        LOGGER.info("character_tag_extract_started operationId={} focused={} excerpts={} promptChars={}",
                request.operationId(), focused, request.excerpts().size(), prompt.length());
        try {
            List<Candidate> candidates = groundAgainstExcerpts(
                    parseModel(request.operationId(), adapter.complete(request.operationId(), prompt), true), request.excerpts());
            LOGGER.info("character_tag_extract_finished operationId={} focused={} candidates={} elapsedMs={}",
                    request.operationId(), focused, candidates.size(), (System.nanoTime() - startedAt) / 1_000_000);
            return new Response(candidates);
        } catch (RuntimeException exception) {
            LOGGER.warn("character_tag_extract_failed operationId={} focused={} elapsedMs={} reason={}",
                    request.operationId(), focused, (System.nanoTime() - startedAt) / 1_000_000, reason(exception));
            throw exception;
        }
    }

    static String buildPrompt(Request request) {
        String excerpts = request.excerpts().stream()
                .map(CharacterInputTagController::formatExcerpt)
                .collect(Collectors.joining("\n\n"));
        boolean focused = request.instruction() != null && !request.instruction().isBlank();
        String schema = "Each array item requires key as a string and options as an array of strings. "
                + "inputMode, label, parentKey, required, confidence, sourceType, evidence, sourceQuote, and optionDetails are optional. "
                + "A non-empty options list means a selectable field; an empty options list means free text. "
                + "Include only options explicitly visible in the excerpts. Options must be values the player can choose; never emit headings, field names, book titles, or generic nouns as options. ";
        return "Extract only source-grounded character input tags. Return one JSON array only; do not explain your answer or output reasoning. "
                + (focused ? "Task-specific instruction: " + request.instruction() + " " : "")
                + schema
                + "Do not invent fields, values, or evidence. Source excerpts:\n" + excerpts;
    }

    private static String formatExcerpt(Excerpt excerpt) {
        String text = excerpt.text() == null ? "" : excerpt.text();
        return "[documentId=" + excerpt.documentId()
                + ", extractionVersion=" + excerpt.extractionVersion()
                + ", locator=" + excerpt.locator() + "]\n"
                + text.substring(0, Math.min(text.length(), 1500));
    }

    List<Candidate> parseModel(String text) {
        return parseModel("test-or-unknown", text, false);
    }

    List<Candidate> parseModel(String operationId, String text, boolean requireOptionDetails) {
        String response = text == null ? "" : text.trim();
        Map<String, Integer> rejected = new LinkedHashMap<>();
        try {
            String json = extractJsonArray(response);
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) throw new IllegalArgumentException("AI character tag response must be an array");
            List<Candidate> result = new ArrayList<>();
            for (JsonNode node : root) {
                try {
                    List<String> extractedOptions = strings(node.get("options"));
                    if (extractedOptions.isEmpty() && node.path("optionDetails").isObject()) extractedOptions = strings(node.path("optionDetails").get("options"));
                    List<String> options = extractedOptions;
                    String key = node.path("key").asText("");
                    if (key.isBlank()) key = required(node, "label");
                    String mode = node.path("inputMode").asText("");
                    if (mode.isBlank() || !List.of("FREE_TEXT", "SINGLE_SELECT", "MULTI_SELECT").contains(mode)) {
                        mode = options.isEmpty() ? "FREE_TEXT" : "SINGLE_SELECT";
                    }
                    InputMode.valueOf(mode);
                    if ("FREE_TEXT".equals(mode) && !options.isEmpty()) throw new IllegalArgumentException("free-text options");
                    String confidence = confidence(node.get("confidence"));
                    if (!List.of("HIGH", "MEDIUM", "LOW").contains(confidence)) throw new IllegalArgumentException("unsupported confidence");
                    String sourceType = node.path("sourceType").asText("RULEBOOK");
                    if (!List.of("RULEBOOK", "STORYBOOK", "HANDOUT").contains(sourceType)) sourceType = "RULEBOOK";
                    List<SourceRef> evidence = refs(node.get("evidence"));
                    String quote = node.path("sourceQuote").asText("");
                    List<OptionDetail> optionDetails = optionDetails(node.get("optionDetails"));
                    if ("FREE_TEXT".equals(mode) && !optionDetails.isEmpty()) throw new IllegalArgumentException("free-text option details");
                    if (optionDetails.stream().anyMatch(detail -> !options.contains(detail.value()))) {
                        throw new IllegalArgumentException("invalid option detail");
                    }
                    if (requireOptionDetails && !"FREE_TEXT".equals(mode) && !options.isEmpty()) {
                        optionDetails = completeOptionDetails(options, optionDetails, quote, evidence);
                    }
                    result.add(new Candidate(key, node.path("label").asText(key), nullable(node, "parentKey"), node.path("required").asBoolean(false), mode, options, optionDetails, strings(node.get("suggestions")), confidence, quote, evidence, sourceType));
                } catch (RuntimeException invalidCandidate) {
                    rejected.merge(reason(invalidCandidate), 1, Integer::sum);
                }
            }
            LOGGER.info("character_tag_parse operationId={} responseChars={} firstChar={} jsonRoot={} candidates={} accepted={} rejected={}",
                    operationId, response.length(), firstChar(response), root.isArray() ? "array" : root.getNodeType(),
                    root.size(), result.size(), rejected);
            return List.copyOf(result);
        } catch (Exception malformed) {
            // A draft remains useful for review even when the local model emits
            // malformed JSON; the compiler will mark all required fields manual.
            LOGGER.warn("character_tag_parse_failed operationId={} responseChars={} firstChar={} hasArrayMarkers={} reason={}",
                    operationId, response.length(), firstChar(response), response.contains("[") && response.contains("]"), reason(malformed));
            return List.of();
        }
    }

    private static List<Candidate> groundAgainstExcerpts(List<Candidate> candidates, List<Excerpt> excerpts) {
        return candidates.stream().map(candidate -> {
            List<SourceRef> evidence = candidate.evidence().isEmpty() ? inferredEvidence(candidate, excerpts) : candidate.evidence();
            if (evidence.isEmpty()) return null;
            List<String> options = candidate.options().stream()
                    .filter(option -> isSelectableOption(option, candidate))
                    .toList();
            if (!candidate.inputMode().equals("FREE_TEXT") && options.isEmpty()) return null;
            String quote = candidate.sourceQuote().isBlank() && !candidate.options().isEmpty()
                    ? options.getFirst() : candidate.sourceQuote();
            List<OptionDetail> details = candidate.optionDetails().stream().map(detail -> new OptionDetail(detail.value(), detail.label(),
                    detail.description(), detail.sourceQuote().isBlank()
                            ? (quote.isBlank() ? detail.value() : quote)
                            : detail.sourceQuote(),
                    detail.evidence().isEmpty() ? evidence : detail.evidence())).toList();
            if (candidate.inputMode().equals("FREE_TEXT")) details = List.of();
            else details = completeOptionDetails(options, details.stream().filter(detail -> options.contains(detail.value())).toList(), quote, evidence);
            return new Candidate(candidate.key(), candidate.label(), candidate.parentKey(), candidate.required(), candidate.inputMode(),
                    options, details, candidate.suggestions(), candidate.confidence(), quote, evidence, candidate.sourceType());
        }).filter(java.util.Objects::nonNull).toList();
    }

    private static boolean isSelectableOption(String option, Candidate candidate) {
        String value = option == null ? "" : option.strip();
        return !value.isBlank() && !value.equalsIgnoreCase(candidate.key()) && !value.equalsIgnoreCase(candidate.label());
    }

    private static List<SourceRef> inferredEvidence(Candidate candidate, List<Excerpt> excerpts) {
        return excerpts.stream().filter(excerpt -> {
            String text = excerpt.text() == null ? "" : excerpt.text();
            if (!candidate.sourceQuote().isBlank() && text.contains(candidate.sourceQuote())) return true;
            if (candidate.inputMode().equals("FREE_TEXT")) return false;
            return !candidate.options().isEmpty() && candidate.options().stream().allMatch(text::contains);
        }).map(excerpt -> new SourceRef(excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())).toList();
    }

    private static String firstChar(String value) { return value.isEmpty() ? "EMPTY" : String.valueOf(value.charAt(0)); }
    private static String reason(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        String normalized = message.replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 80));
    }

    private static String extractJsonArray(String response) {
        String value = response == null ? "" : response.trim();
        if (value.startsWith("{") && value.endsWith("}")) {
            int objectStart = value.indexOf("\"candidates\"");
            if (objectStart < 0) objectStart = value.indexOf("\"tags\"");
            if (objectStart >= 0) {
                int arrayStart = value.indexOf('[', objectStart), arrayEnd = value.lastIndexOf(']');
                if (arrayStart >= 0 && arrayEnd >= arrayStart) return value.substring(arrayStart, arrayEnd + 1);
            }
            // Ollama JSON mode may serialize a single candidate as an object even
            // when the prompt requests an array. Treat that object as one item.
            return "[" + value + "]";
        }
        int start = value.indexOf('['), end = value.lastIndexOf(']');
        if (start >= 0 && end >= start) return value.substring(start, end + 1);
        // Keep the blueprint flow reviewable when the local model emits an
        // unusable completion; the compiler supplies explicit manual fields.
        return "[]";
    }
    private static String required(JsonNode node, String name) { String value = node.path(name).asText(""); if (value.isBlank()) throw new IllegalArgumentException(name + " missing"); return value; }
    private static String confidence(JsonNode node) {
        if (node != null && node.isNumber()) return node.asDouble() >= .85 ? "HIGH" : node.asDouble() >= .5 ? "MEDIUM" : "LOW";
        return node == null ? "LOW" : node.asText("LOW");
    }
    private static String nullable(JsonNode node, String name) { JsonNode value = node.get(name); return value == null || value.isNull() ? null : value.asText(); }
    private static List<String> strings(JsonNode node) { if (node == null || !node.isArray()) return List.of(); LinkedHashSet<String> result = new LinkedHashSet<>(); node.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText()); }); return List.copyOf(result); }
    private static List<OptionDetail> optionDetails(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<OptionDetail> result = new ArrayList<>();
        node.forEach(value -> {
            try { result.add(new OptionDetail(required(value, "value"), value.path("label").asText(),
                    value.path("description").asText(), required(value, "sourceQuote"), refs(value.get("evidence")))); }
            catch (RuntimeException ignored) { }
        });
        return List.copyOf(result);
    }
    private static List<OptionDetail> completeOptionDetails(List<String> options, List<OptionDetail> details,
                                                            String fieldQuote, List<SourceRef> fieldEvidence) {
        Map<String, OptionDetail> byValue = new LinkedHashMap<>();
        details.forEach(detail -> byValue.put(detail.value(), detail));
        for (String option : options) {
            byValue.putIfAbsent(option, new OptionDetail(option, option,
                    "Source-grounded selectable value.", fieldQuote, fieldEvidence));
        }
        return List.copyOf(byValue.values());
    }
    private static List<SourceRef> refs(JsonNode node) { if (node == null || !node.isArray()) return List.of(); List<SourceRef> result = new ArrayList<>(); node.forEach(value -> { try { result.add(new SourceRef(UUID.fromString(required(value, "documentId")), value.path("extractionVersion").asLong(), required(value, "locator"))); } catch (RuntimeException ignored) { } }); return List.copyOf(result); }
    public record Request(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion,
                           String instruction) {
        public Request(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion) {
            this(operationId, excerpts, schemaVersion, promptVersion, "");
        }
    }
    public record Excerpt(UUID documentId, long extractionVersion, String locator, String text) {}
    public record Response(List<Candidate> candidates) {}
    public record Candidate(String key, String label, String parentKey, boolean required, String inputMode, List<String> options, List<OptionDetail> optionDetails, List<String> suggestions, String confidence, String sourceQuote, List<SourceRef> evidence, String sourceType) {}
    public record OptionDetail(String value, String label, String description, String sourceQuote, List<SourceRef> evidence) {}
    public record SourceRef(UUID documentId, long extractionVersion, String locator) {}
    private enum InputMode { FREE_TEXT, SINGLE_SELECT, MULTI_SELECT }
}
