package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.knowledge.RulebookActionDefinitionLookupPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Reads the exact registered-source definitions from the rulebook service. */
public final class CrossContextHttpRulebookActionDefinitionLookupGateway implements RulebookActionDefinitionLookupPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public CrossContextHttpRulebookActionDefinitionLookupGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client); this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout); this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @Override public List<StructuredSpellDefinition> find(KnowledgeDocumentId documentId, long registrationVersion) {
        try {
            URI uri = baseUri.resolve("internal/v1/rulebooks/" + documentId.value() + "/action-definitions?registrationVersion=" + registrationVersion);
            var builder = HttpRequest.newBuilder(uri).timeout(timeout).GET();
            if (!internalToken.isBlank()) builder.header("X-Internal-Token", internalToken);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("published rulebook action definitions are unavailable: HTTP " + response.statusCode());
            JsonNode root = mapper.readTree(response.body());
            if (!documentId.value().toString().equals(root.path("documentId").asText())
                    || !"COMPLETED".equals(root.path("status").asText()) || !root.path("definitions").isArray())
                throw new IllegalStateException("published rulebook action definition response does not match expected source");
            String sourceVersion = root.path("extractionVersion").asText();
            if (sourceVersion.isBlank()) throw new IllegalStateException("published rulebook action definition source version is missing");
            List<StructuredSpellDefinition> definitions = new ArrayList<>();
            for (JsonNode item : root.path("definitions")) definitions.add(toStructuredDefinition(item, documentId, registrationVersion, sourceVersion));
            return List.copyOf(definitions);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("rulebook action definition lookup was interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("rulebook action definition lookup failed", e);
        }
    }

    private StructuredSpellDefinition toStructuredDefinition(JsonNode item, KnowledgeDocumentId documentId,
            long registrationVersion, String sourceVersion) {
        String id = required(item, "id");
        String kindText = required(item, "actionKind").toUpperCase(Locale.ROOT);
        StructuredSpellDefinition.ActionKind kind = enumValue(StructuredSpellDefinition.ActionKind.class, kindText,
                StructuredSpellDefinition.ActionKind.SPELL);
        JsonNode fields = item.path("fields");
        StructuredSpellDefinition.RangeForm rangeForm = enumValue(StructuredSpellDefinition.RangeForm.class,
                value(fields, "rangeForm"), StructuredSpellDefinition.RangeForm.UNSUPPORTED);
        Integer normalRange = integer(fields, "normalRangeFeet");
        Integer longRange = integer(fields, "longRangeFeet");
        if (rangeForm == StructuredSpellDefinition.RangeForm.SINGLE && normalRange == null
                || rangeForm == StructuredSpellDefinition.RangeForm.NORMAL_AND_LONG
                        && (normalRange == null || longRange == null)) {
            rangeForm = StructuredSpellDefinition.RangeForm.UNSUPPORTED;
            normalRange = null;
            longRange = null;
        }
        StructuredSpellDefinition.RangeProfile range = new StructuredSpellDefinition.RangeProfile(rangeForm,
                normalRange, longRange);
        StructuredSpellDefinition.TargetingProfile targeting = new StructuredSpellDefinition.TargetingProfile(
                enumValue(StructuredSpellDefinition.TargetMode.class, value(fields, "targetMode"), StructuredSpellDefinition.TargetMode.UNSUPPORTED),
                enumValue(StructuredSpellDefinition.OriginMode.class, value(fields, "originMode"), StructuredSpellDefinition.OriginMode.UNSUPPORTED),
                enumValue(StructuredSpellDefinition.EffectShape.class, value(fields, "effectShape"), StructuredSpellDefinition.EffectShape.UNSUPPORTED),
                integer(fields, "lengthFeet"), integer(fields, "widthFeet"), integer(fields, "heightFeet"), integer(fields, "radiusFeet"),
                enumValue(StructuredSpellDefinition.PathBlockingRule.class, value(fields, "pathBlocking"), StructuredSpellDefinition.PathBlockingRule.UNSUPPORTED),
                enumValue(StructuredSpellDefinition.PropagationRule.class, value(fields, "propagation"), StructuredSpellDefinition.PropagationRule.UNSUPPORTED),
                enumValue(StructuredSpellDefinition.VerticalDirection.class, value(fields, "verticalDirection"), null));
        List<String> unsupported = new ArrayList<>();
        if (item.path("unsupportedFields").isArray()) item.path("unsupportedFields").forEach(value -> { if (value.isTextual()) unsupported.add(value.asText()); });
        boolean executable = unsupported.isEmpty() && range.supported() && targeting.supported();
        String rangeArea = nonBlank(fields, "range", "미상(검토 필요)");
        String effect = nonBlank(fields, "damageEffect", nonBlank(fields, "effect", "미상(검토 필요)"));
        return new StructuredSpellDefinition(id, required(item, "name"), documentId.value(), required(item, "sourceLocator"),
                sourceVersion, registrationVersion, nonBlank(fields, "level", "미상(검토 필요)"),
                nonBlank(fields, "castingTime", "미상(검토 필요)"), rangeArea,
                nonBlank(fields, "components", "미상(검토 필요)"), nonBlank(fields, "duration", "미상(검토 필요)"),
                nonBlank(fields, "school", "미상(검토 필요)"), nonBlank(fields, "attackSave", "미상(검토 필요)"),
                effect, List.of(), required(item, "sourceEvidence"), executable,
                StructuredSpellDefinition.ReviewStatus.PENDING, false, kind, range, targeting, true);
    }

    private static String required(JsonNode node, String name) {
        String value = node.path(name).asText("");
        if (value.isBlank()) throw new IllegalStateException("rulebook action definition field is missing: " + name);
        return value;
    }
    private static String value(JsonNode node, String name) { return node.path(name).isTextual() ? node.path(name).asText() : ""; }
    private static String nonBlank(JsonNode node, String name, String fallback) {
        String value = value(node, name);
        return value.isBlank() ? fallback : value;
    }
    private static Integer integer(JsonNode node, String name) { return node.path(name).isIntegralNumber() ? node.path(name).asInt() : null; }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException ignored) { return fallback; }
    }
}
