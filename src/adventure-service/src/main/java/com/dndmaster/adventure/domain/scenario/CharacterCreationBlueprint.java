package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

    /** Returns stable recursive nodes while retaining flat-field persistence compatibility. */
    public List<CharacterInputNode> roots() {
        Map<String, NodeDraft> nodes = new LinkedHashMap<>();
        Map<String, String> pathIds = new LinkedHashMap<>();
        for (Field field : fields) {
            pathIds.put(field.key(), field.nodeId());
            int separator = field.key().lastIndexOf('.');
            if (separator > 0 && field.parentNodeId() != null) pathIds.put(field.key().substring(0, separator), field.parentNodeId());
        }
        for (Field field : fields) {
            String[] parts = field.key().split("\\.");
            String parentPath = "";
            for (int index = 0; index < parts.length; index++) {
                String path = parentPath.isEmpty() ? parts[index] : parentPath + "." + parts[index];
                String id = pathIds.computeIfAbsent(path, ignored -> UUID.randomUUID().toString());
                String parentId = parentPath.isEmpty() ? null : pathIds.get(parentPath);
                NodeDraft draft = nodes.get(id);
                if (draft == null) {
                    draft = NodeDraft.synthetic(id, parentId, parts[index]);
                    nodes.put(id, draft);
                }
                if (index == parts.length - 1) draft.field = field;
                parentPath = path;
            }
        }
        return nodes.values().stream().filter(node -> node.parentId == null).map(node -> node.toNode(nodes)).toList();
    }

    public CharacterInputNode node(String id) {
        List<CharacterInputNode> tree = roots();
        var direct = flatten(tree).stream().filter(node -> node.id().equals(id)).findFirst();
        if (direct.isPresent()) return direct.get();
        return findByPath(tree, id, "")
                .orElseThrow(() -> new IllegalArgumentException("unknown blueprint node: " + id));
    }

    public CharacterCreationBlueprint resolveNode(String id, String value) {
        CharacterInputNode target = node(id);
        String fieldKey = fields.stream().filter(field -> field.nodeId().equals(target.id())).map(Field::key).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("cannot resolve synthetic node: " + id));
        return resolve(fieldKey, value);
    }

    public CharacterCreationBlueprint addUserInputChild(String parentId, String key, String label) {
        CharacterInputNode parent = node(parentId);
        String parentFieldKey = fields.stream().filter(field -> field.nodeId().equals(parent.id())).map(Field::key).findFirst()
                .orElseGet(() -> fields.stream().filter(field -> field.key().equals(parentId)).map(Field::key).findFirst()
                        .orElseGet(() -> fields.stream().filter(field -> parent.id().equals(field.parentNodeId()))
                                .map(field -> field.key().substring(0, field.key().lastIndexOf('.'))).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("cannot add child to synthetic node"))));
        if (key == null || key.isBlank() || key.contains(".")) throw new IllegalArgumentException("invalid child key");
        String childKey = parentFieldKey + "." + key.trim();
        if (fields.stream().anyMatch(field -> field.key().equals(childKey))) throw new IllegalArgumentException("child already exists");
        Field child = new Field(childKey, List.of(), false, "USER", List.of(), "USER_ADDED", List.of(),
                InputMode.FREE_TEXT, List.of(), "", label, null, UUID.randomUUID().toString(), parent.id());
        List<Field> next = new ArrayList<>(fields);
        next.add(child);
        return new CharacterCreationBlueprint(revision + 1, CharacterCreationBlueprintStatus.NEEDS_REVIEW,
                next, diagnostics);
    }

    private static List<CharacterInputNode> flatten(List<CharacterInputNode> nodes) {
        List<CharacterInputNode> result = new ArrayList<>();
        for (CharacterInputNode node : nodes) { result.add(node); result.addAll(flatten(node.children())); }
        return result;
    }

    private static java.util.Optional<CharacterInputNode> findByPath(List<CharacterInputNode> nodes, String path, String parentPath) {
        for (CharacterInputNode node : nodes) {
            String currentPath = parentPath.isEmpty() ? node.key() : parentPath + "." + node.key();
            if (currentPath.equals(path)) return java.util.Optional.of(node);
            var child = findByPath(node.children(), path, currentPath);
            if (child.isPresent()) return child;
        }
        return java.util.Optional.empty();
    }

    private static final class NodeDraft {
        private final String id;
        private final String parentId;
        private final String key;
        private Field field;

        private NodeDraft(String id, String parentId, String key) { this.id = id; this.parentId = parentId; this.key = key; }
        private static NodeDraft synthetic(String id, String parentId, String key) { return new NodeDraft(id, parentId, key); }

        private CharacterInputNode toNode(Map<String, NodeDraft> nodes) {
            Field value = field;
            List<CharacterInputNode> children = nodes.values().stream().filter(node -> id.equals(node.parentId))
                    .map(node -> node.toNode(nodes)).toList();
            if (value == null) return new CharacterInputNode(id, parentId, key, key, InputMode.FREE_TEXT, null,
                    List.of(), List.of(), CharacterInputNodeStatus.PARTIALLY_EXTRACTED, true, List.of(), "LOW", "",
                    List.of("parent extracted but child definition is missing"), children);
            CharacterInputNodeStatus status = switch (value.inputStatus()) {
                case "USER_ADDED" -> CharacterInputNodeStatus.USER_ADDED;
                case "USER_CONFIRMED" -> CharacterInputNodeStatus.REVIEWED;
                case "MANUAL_INPUT_REQUIRED" -> CharacterInputNodeStatus.PARTIALLY_EXTRACTED;
                default -> CharacterInputNodeStatus.EXTRACTED;
            };
            String selectedValue = value.value();
            List<String> nodeOptions = value.inputMode() == InputMode.FREE_TEXT ? List.of() : value.options();
            return new CharacterInputNode(id, parentId, key, value.label(), value.inputMode(), selectedValue, nodeOptions,
                    value.suggestions(), status, status == CharacterInputNodeStatus.PARTIALLY_EXTRACTED,
                    value.evidence(), status == CharacterInputNodeStatus.PARTIALLY_EXTRACTED ? "LOW" : "HIGH",
                    value.sourceQuote(), value.diagnostics(), children);
        }
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
            List<String> requestedValues = field.inputMode() == InputMode.MULTI_SELECT
                    ? java.util.Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList()
                    : List.of(value);
            if (requestedValues.isEmpty()) throw new IllegalArgumentException("blueprint value must not be blank");
            if (!field.options().isEmpty() && requestedValues.stream().anyMatch(item -> !field.options().contains(item))) {
                throw new IllegalArgumentException("value is not a blueprint option: " + value);
            }
            next.add(new Field(field.key(), field.options(), field.required(), field.sourceType(), field.evidence(),
                    "USER_CONFIRMED", List.of(), field.inputMode(), field.suggestions(), field.sourceQuote(), field.label(),
                    String.join(",", requestedValues), field.nodeId(), field.parentNodeId()));
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
                        List<ScenarioSourceReference> evidence, String inputStatus, List<String> diagnostics,
                        InputMode inputMode, List<String> suggestions, String sourceQuote, String label, String value,
                        String nodeId, String parentNodeId) {
        public Field(String key, List<String> options, boolean required, String sourceType,
                     List<ScenarioSourceReference> evidence, String inputStatus, List<String> diagnostics) {
            this(key, options, required, sourceType, evidence, inputStatus, diagnostics,
                    options.isEmpty() ? InputMode.FREE_TEXT : InputMode.SINGLE_SELECT, List.of(), "", key, null,
                    null, null);
        }

        public Field(String key, List<String> options, boolean required, String sourceType,
                     List<ScenarioSourceReference> evidence, String inputStatus, List<String> diagnostics,
                     InputMode inputMode, List<String> suggestions, String sourceQuote) {
            this(key, options, required, sourceType, evidence, inputStatus, diagnostics, inputMode, suggestions,
                    sourceQuote, key, null, null, null);
        }

        public Field(String key, List<String> options, boolean required, String sourceType,
                     List<ScenarioSourceReference> evidence, String inputStatus, List<String> diagnostics,
                     InputMode inputMode, List<String> suggestions, String sourceQuote, String label) {
            this(key, options, required, sourceType, evidence, inputStatus, diagnostics, inputMode, suggestions,
                    sourceQuote, label, null, null, null);
        }

        public Field {
            key = Objects.requireNonNull(key, "field key must not be null");
            options = List.copyOf(Objects.requireNonNull(options, "options must not be null"));
            sourceType = Objects.requireNonNull(sourceType, "source type must not be null");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
            inputStatus = Objects.requireNonNull(inputStatus, "input status must not be null");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
            inputMode = inputMode == null ? (options.isEmpty() ? InputMode.FREE_TEXT : InputMode.SINGLE_SELECT) : inputMode;
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
            sourceQuote = sourceQuote == null ? "" : sourceQuote;
            label = label == null || label.isBlank() ? key : label;
            value = value == null || value.isBlank() ? null : value;
            nodeId = nodeId == null || nodeId.isBlank() ? UUID.randomUUID().toString() : nodeId;
            if (parentNodeId != null && parentNodeId.isBlank()) parentNodeId = null;
        }
    }
}
