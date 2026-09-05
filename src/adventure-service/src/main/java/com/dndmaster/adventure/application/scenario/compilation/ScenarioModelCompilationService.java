package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioCompilationInputSnapshot;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelCompilationPolicy;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
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
        Objects.requireNonNull(input, "compilation input is required");
        List<ResolutionExtractionPort.SourceExcerpt> source = excerpts == null ? List.of() : excerpts.stream()
                .filter(Objects::nonNull).filter(ResolutionExtractionPort.SourceExcerpt::isPublishedEvidence).toList();
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
        ScenarioModel model = new ScenarioModel(1, List.of(), List.of(), objectives, List.of(), List.of(), List.of(),
                resolutions, startingSituation);
        return ScenarioModelCompilationPolicy.evaluate(input, model);
    }

    private static ScenarioModelElement element(String id, String type, String value,
            ResolutionExtractionPort.SourceExcerpt excerpt) {
        return new ScenarioModelElement(id, type, Map.of("value", value, "source", "PRIMARY"),
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(
                        excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())));
    }
}
