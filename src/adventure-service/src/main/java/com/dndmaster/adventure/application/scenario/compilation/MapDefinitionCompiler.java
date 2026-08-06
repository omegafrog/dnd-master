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
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MapDefinitionCompiler {
    private static final Pattern VALUE = Pattern.compile("(?i)([a-z]+)=([^\\s]+)");

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
                String text = excerpt.text() == null ? "" : excerpt.text();
                if (!text.matches("(?is).*\\bMAP\\b.*")) continue;
                String asset = value(text, "asset", document.originalFilename());
                double confidence = decimal(value(text, "confidence", "0"));
                MapSafetyStatus safety = safety(value(text, "safety", "UNSAFE"));
                result.add(new MapDefinition(UUID.nameUUIDFromBytes((document.knowledgeDocumentId().value() + ":" + document.extractionVersion() + ":" + excerpt.locator()).getBytes()),
                        asset, value(text, "image", asset), new MapDefinition.MapGrid(decimal(value(text, "originx", "0")), decimal(value(text, "originy", "0")),
                                decimal(value(text, "grid", "1")), decimal(value(text, "rotation", "0")), value(text, "distance", "5ft")),
                        values(text, "walls"), values(text, "doors"), values(text, "obstacles"),
                        new MapSourceReference(document.knowledgeDocumentId(), document.extractionVersion(), excerpt.locator()), confidence, safety));
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
        while (matcher.find()) if (matcher.group(1).equalsIgnoreCase(key)) return matcher.group(2);
        return fallback;
    }
    private static List<String> values(String text, String key) {
        String value = value(text, key, "");
        return value.isBlank() ? List.of() : List.of(value.split("\\|"));
    }
    private static double decimal(String value) { try { return Double.parseDouble(value.replace(",", ".")); } catch (NumberFormatException e) { return 0; } }
    private static MapSafetyStatus safety(String value) { try { return MapSafetyStatus.valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return MapSafetyStatus.UNSAFE; } }
    record Compilation(List<MapDefinition> maps, List<StoryMapBinding> bindings) {}
}
