package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.StoryMapBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MapDefinitionCompiler {
    private static final Pattern VALUE = Pattern.compile("(?i)([a-z]+)=(?:\"([^\"]+)\"|([^\\s]+))");
    private static final Pattern SPATIAL_FEATURE = Pattern.compile(
            "(?i)\\bFEATURE\\s+id=([^\\s]+)\\s+type=([^\\s]+)\\s+required=(true|false)(?:\\s+(?:cells|resolutionUnitId|rule|difficulty|mode|diceExpression|modifier|triggers|duration|removal|overlap|repeatable)=[^\\s]+)*");
    private static final Pattern FEATURE_ATTRIBUTE = Pattern.compile("(?i)(cells|resolutionUnitId|rule|difficulty|mode|diceExpression|modifier|triggers|duration|removal|overlap|repeatable)=([^\\s]+)");

    Compilation compile(ScenarioSourceBundle bundle, List<ResolutionExtractionPort.SourceExcerpt> excerpts) {
        List<MapDefinition> result = new ArrayList<>();
        for (ScenarioBundleDocumentSelection document : bundle.currentRevision().documents()) {
            if (document.role() != ScenarioBundleDocumentRole.MAP) continue;
            List<ResolutionExtractionPort.SourceExcerpt> scoped = excerpts.stream()
                    .filter(Objects::nonNull)
                    .filter(e -> e.documentId().equals(document.knowledgeDocumentId()))
                    .toList();
            if (scoped.stream().anyMatch(e -> e.extractionVersion() != document.extractionVersion())) {
                throw new IllegalArgumentException("map source excerpt is outside locked bundle extraction version");
            }
            for (ResolutionExtractionPort.SourceExcerpt excerpt : scoped) {
                if (excerpt.locator() == null || !excerpt.locator().startsWith("asset:")) continue;
                String text = excerpt.text() == null ? "" : excerpt.text();
                if (!text.matches("(?is).*\\bMAP\\b.*")) continue;
                String asset = value(text, "asset", document.originalFilename());
                double confidence = decimal(value(text, "confidence", "0"));
                MapSafetyStatus safety = safety(value(text, "safety", "UNSAFE"));
                MapSourceReference source = new MapSourceReference(document.knowledgeDocumentId(), document.extractionVersion(), excerpt.locator(),
                        Long.toString(bundle.currentRevision().revision()));
                result.add(new MapDefinition(UUID.nameUUIDFromBytes((document.knowledgeDocumentId().value() + ":" + document.extractionVersion() + ":" + excerpt.locator()).getBytes()),
                        asset, value(text, "image", asset), new MapDefinition.MapGrid(decimal(value(text, "originx", "0")), decimal(value(text, "originy", "0")),
                                decimal(value(text, "grid", "1")), decimal(value(text, "rotation", "0")), value(text, "distance", "5ft")),
                        values(text, "walls"), values(text, "doors"), values(text, "obstacles"),
                        source, confidence, safety, spatialFeatures(text, source)));
            }
        }
        List<StoryMapBinding> bindings = new ArrayList<>();
        for (ResolutionExtractionPort.SourceExcerpt excerpt : excerpts) {
            String text = excerpt.text() == null ? "" : excerpt.text();
            if (!text.matches("(?is).*\\bMAP_BINDING\\b.*")) continue;
            String asset = value(text, "asset", "");
            MapDefinition target = result.stream().filter(map -> map.assetId().equals(asset)).findFirst().orElse(null);
            if (target != null) bindings.add(new StoryMapBinding(value(text, "stage", ""), value(text, "location", ""), value(text, "condition", ""), target.id()));
        }
        return new Compilation(List.copyOf(result), List.copyOf(bindings));
    }

    private static String value(String text, String key, String fallback) {
        Matcher matcher = VALUE.matcher(text);
        while (matcher.find()) if (matcher.group(1).equalsIgnoreCase(key)) return matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        return fallback;
    }
    private static List<String> values(String text, String key) {
        String value = value(text, key, "");
        return splitValues(value);
    }

    private static List<String> splitValues(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\|"));
    }

    private static List<MapDefinition.SpatialFeatureRequirement> spatialFeatures(String text, MapSourceReference source) {
        List<MapDefinition.SpatialFeatureRequirement> requirements = new ArrayList<>();
        Matcher features = SPATIAL_FEATURE.matcher(text);
        String evidenceReference = "document:" + source.knowledgeDocumentId().value() + ":"
                + source.extractionVersion() + ":" + source.locator();
        while (features.find()) {
            Map<String, String> attributes = new java.util.HashMap<>();
            Matcher values = FEATURE_ATTRIBUTE.matcher(features.group());
            while (values.find()) attributes.put(values.group(1).toLowerCase(Locale.ROOT), values.group(2));
            Integer difficulty = attributes.containsKey("difficulty") ? integer(attributes.get("difficulty")) : null;
            int modifier = attributes.containsKey("modifier") ? integer(attributes.get("modifier")) : 0;
            List<String> triggers = attributes.getOrDefault("triggers", "").isBlank()
                    ? List.of() : List.of(attributes.get("triggers").split("[|,]"));
            int duration = attributes.containsKey("duration") ? integer(attributes.get("duration")) : -1;
            boolean overlap = Boolean.parseBoolean(attributes.getOrDefault("overlap", "false"));
            boolean repeatable = Boolean.parseBoolean(attributes.getOrDefault("repeatable", "false"));
            requirements.add(new MapDefinition.SpatialFeatureRequirement(
                    UUID.fromString(features.group(1)), features.group(2), Boolean.parseBoolean(features.group(3)),
                    List.of(evidenceReference), splitValues(attributes.getOrDefault("cells", "")),
                    attributes.getOrDefault("rule", ""), difficulty, attributes.getOrDefault("mode", ""), triggers, duration,
                    attributes.getOrDefault("removal", ""), overlap, attributes.getOrDefault("resolutionunitid", ""), repeatable,
                    attributes.getOrDefault("diceexpression", "1d20"), modifier));
        }
        return List.copyOf(requirements);
    }

    private static Integer integer(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("spatial feature difficulty must be an integer", exception);
        }
    }
    private static double decimal(String value) { try { return Double.parseDouble(value.replace(",", ".")); } catch (NumberFormatException e) { return 0; } }
    private static MapSafetyStatus safety(String value) { try { return MapSafetyStatus.valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return MapSafetyStatus.UNSAFE; } }
    record Compilation(List<MapDefinition> maps, List<StoryMapBinding> bindings) {}
}
