package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioCompilationInputSnapshot;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelCompilationPolicy;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds the lockable model projection from extracted Storybook evidence. */
public final class ScenarioModelCompilationService {
    public ScenarioModelCompilationPolicy.Evaluation compile(
            ScenarioCompilationInputSnapshot input,
            List<ScenarioResolutionUnit> resolutionUnits,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts) {
        return compile(input, resolutionUnits, excerpts, ScenarioModel.empty());
    }

    public ScenarioModelCompilationPolicy.Evaluation compile(
            ScenarioCompilationInputSnapshot input,
            List<ScenarioResolutionUnit> resolutionUnits,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts,
            ScenarioModel extractedModel) {
        Objects.requireNonNull(input, "compilation input is required");
        Objects.requireNonNull(extractedModel, "extracted model is required");
        java.util.Set<String> storybookSources = input.storybooks().stream()
                .map(storybook -> storybook.documentId() + ":" + storybook.extractionVersion())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<ResolutionExtractionPort.SourceExcerpt> source = excerpts == null ? List.of() : excerpts.stream()
                .filter(Objects::nonNull).filter(ResolutionExtractionPort.SourceExcerpt::isPublishedEvidence)
                .filter(excerpt -> "STORYBOOK".equalsIgnoreCase(excerpt.documentType()))
                .filter(excerpt -> storybookSources.contains(excerpt.documentId().value() + ":" + excerpt.extractionVersion()))
                .toList();
        ScenarioResolutionUnit firstResolution = resolutionUnits == null ? null : resolutionUnits.stream()
                .filter(unit -> unit != null && unit.status() == ResolutionStatus.COMPLETE).findFirst().orElse(null);
        ResolutionExtractionPort.SourceExcerpt objectiveExcerpt = source.stream()
                .filter(excerpt -> excerpt.text().matches("(?is).*(?:\\bobjective\\b|\\bgoal\\b|\\bmission\\b|목표|임무).*"))
                .findFirst().orElse(null);
        String objectiveText = objectiveExcerpt == null ? "" : objectiveExcerpt.text().trim();
        List<ScenarioModelElement> objectives = objectiveText.isBlank() ? List.of() : List.of(
                element("objective-1", "objective", objectiveText, objectiveExcerpt));
        List<ScenarioModelElement> resolutions = firstResolution == null ? List.of() : List.of(
                new ScenarioModelElement("resolution-1", "resolution", Map.of(
                        "kind", String.valueOf(firstResolution.kind()),
                        "description", firstResolution.sourceQuote()), firstResolution.sourceRefs()));
        String startingSituation = source.stream().findFirst().map(ResolutionExtractionPort.SourceExcerpt::text).orElse("");
        EncounterExtraction encounterExtraction = verifiedEncounters(extractedModel, source);
        ScenarioModel model = new ScenarioModel(1, List.of(), List.of(), objectives, List.of(), encounterExtraction.valid(), List.of(),
                resolutions, startingSituation);
        var evaluation = ScenarioModelCompilationPolicy.evaluate(input, model);
        if (encounterExtraction.invalidCount() == 0) return evaluation;
        List<com.dndmaster.adventure.domain.scenario.ScenarioCompilationDiagnostic> diagnostics =
                new java.util.ArrayList<>(evaluation.diagnostics());
        diagnostics.add(com.dndmaster.adventure.domain.scenario.ScenarioCompilationDiagnostic.blocking(
                "COMBAT_SCENARIO_SOURCE_INVALID", "a combat encounter could not be linked to published Storybook evidence"));
        return new ScenarioModelCompilationPolicy.Evaluation(ScenarioModelCompilationPolicy.Status.BLOCKED,
                evaluation.model(), diagnostics);
    }

    private static EncounterExtraction verifiedEncounters(ScenarioModel extractedModel,
            List<ResolutionExtractionPort.SourceExcerpt> storybookExcerpts) {
        java.util.Set<String> availableSources = storybookExcerpts.stream()
                .map(excerpt -> sourceKey(excerpt.documentId().value(), excerpt.extractionVersion(), excerpt.locator()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<ScenarioModelElement> candidates = extractedModel.encounters().stream()
                .filter(ScenarioModelCompilationService::isCombatScenario).toList();
        List<ScenarioModelElement> valid = candidates.stream()
                .filter(element -> CombatScenarioDefinition.fromElement(element).isPresent())
                .filter(element -> element.sourceRefs().stream().allMatch(ref -> availableSources.contains(sourceKey(ref))))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(ScenarioModelElement::elementId, element -> element,
                                (first, ignored) -> first, java.util.LinkedHashMap::new),
                        values -> List.copyOf(values.values())));
        return new EncounterExtraction(valid, candidates.size() - valid.size());
    }

    private static boolean isCombatScenario(ScenarioModelElement element) {
        return "combat-scenario".equalsIgnoreCase(element.type()) || "combat_scenario".equalsIgnoreCase(element.type());
    }

    private record EncounterExtraction(List<ScenarioModelElement> valid, int invalidCount) { }

    private static String sourceKey(ScenarioSourceReference ref) {
        return sourceKey(ref.knowledgeDocumentId().value(), ref.extractionVersion(), ref.locator());
    }

    private static String sourceKey(Object documentId, long extractionVersion, String locator) {
        return documentId + ":" + extractionVersion + ":" + locator;
    }

    private static ScenarioModelElement element(String id, String type, String value,
            ResolutionExtractionPort.SourceExcerpt excerpt) {
        return new ScenarioModelElement(id, type, Map.of("value", value, "source", "PRIMARY"),
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(
                        excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())));
    }
}
