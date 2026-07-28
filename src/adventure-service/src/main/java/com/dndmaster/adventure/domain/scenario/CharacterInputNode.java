package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

/** Immutable character-input tree node. Identity is its stable dotted path. */
public record CharacterInputNode(
        String id,
        String parentId,
        String key,
        String label,
        InputMode inputMode,
        String value,
        List<String> options,
        List<String> suggestions,
        CharacterInputNodeStatus status,
        boolean allowUserAddChild,
        List<ScenarioSourceReference> sourceEvidence,
        String confidence,
        String sourceQuote,
        List<String> diagnostics,
        List<CharacterInputNode> children) {
    public CharacterInputNode {
        id = requireText(id, "node id");
        key = requireText(key, "node key");
        label = requireText(label, "node label");
        inputMode = Objects.requireNonNull(inputMode, "input mode must not be null");
        options = List.copyOf(Objects.requireNonNull(options, "options must not be null"));
        suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions must not be null"));
        status = Objects.requireNonNull(status, "node status must not be null");
        sourceEvidence = List.copyOf(Objects.requireNonNull(sourceEvidence, "source evidence must not be null"));
        confidence = requireText(confidence, "confidence");
        sourceQuote = Objects.requireNonNull(sourceQuote, "source quote must not be null");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
        if (parentId != null && (parentId.isBlank() || !id.startsWith(parentId + "."))) {
            throw new IllegalArgumentException("node parent must be the node path prefix");
        }
        if (inputMode == InputMode.FREE_TEXT && !options.isEmpty()) {
            throw new IllegalArgumentException("free-text node cannot have options");
        }
    }

    public CharacterInputNode withValue(String nextValue) {
        if (nextValue == null || nextValue.isBlank()) {
            throw new IllegalArgumentException("node value must not be blank");
        }
        List<String> requested = inputMode == InputMode.MULTI_SELECT
                ? java.util.Arrays.stream(nextValue.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList()
                : List.of(nextValue);
        if (requested.isEmpty() || (!options.isEmpty() && requested.stream().anyMatch(item -> !options.contains(item)))) {
            throw new IllegalArgumentException("value is not a node option: " + nextValue);
        }
        return new CharacterInputNode(id, parentId, key, label, inputMode, String.join(",", requested), options,
                suggestions, CharacterInputNodeStatus.REVIEWED, allowUserAddChild, sourceEvidence, confidence,
                sourceQuote, List.of(), children);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
