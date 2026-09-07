package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.runtime.OpeningSourceContextSearchPort;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Adapts the published Scenario Package into the minimum first-stage artifact request. */
public final class ScenarioPackageStageArtifactAdapter implements StorybookEvidenceLookupPort,
        StageBackboneGenerationPort, StageDetailedGenerationPort {
    private static final String FIRST_STAGE_ID = "first-stage";
    private final ScenarioPackageRepository packages;
    private final ScenarioBundleRepository bundles;
    private final OpeningSourceContextSearchPort openingSearch;

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages) {
        this(packages, null, null);
    }

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages,
            ScenarioBundleRepository bundles, OpeningSourceContextSearchPort openingSearch) {
        this.packages = packages;
        this.bundles = bundles;
        this.openingSearch = openingSearch;
    }

    @Override
    public List<ScenarioSourceReference> lookup(UUID scenarioPackageId) {
        if (bundles != null && openingSearch != null) {
            ScenarioPackage scenarioPackage = loadPackage(scenarioPackageId);
            var bundle = bundles.findById(scenarioPackage.bundleId())
                    .orElseThrow(() -> new IllegalStateException("scenario source bundle not found"));
            List<OpeningSourceContextSearchPort.Result> results = openingSearch.search(
                    new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(bundle.ownerPlayerId().value()), scenarioPackage);
            if (!results.isEmpty()) {
                OpeningSourceContextSearchPort.Result selected = results.stream()
                        .max(java.util.Comparator.comparingDouble(OpeningSourceContextSearchPort.Result::score))
                        .orElseThrow();
                return List.of(new ScenarioSourceReference(
                        selected.knowledgeDocumentId(), selected.extractionVersion(), selected.locator()));
            }
            return List.of();
        }
        return elements(loadPackage(scenarioPackageId)).flatMap(element -> element.sourceRefs().stream()).distinct().toList();
    }

    @Override
    public boolean isScenarioBacked(UUID scenarioPackageId) {
        return !lookup(scenarioPackageId).isEmpty();
    }

    @Override
    public StageBackbone generate(StageBackboneGenerationPort.Request request) {
        ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
        String coreProblem = coreProblem(scenarioPackage);
        String funnel = funnelMeaning(scenarioPackage);
        return new StageBackbone(request.scenarioPackageId(), 1,
                List.of(new StageBackboneEntry(FIRST_STAGE_ID, 1, "opening", coreProblem, funnel, request.evidence())),
                request.evidence());
    }

    @Override
    public DetailedStage generate(StageDetailedGenerationPort.Request request) {
        ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
        String coreProblem = coreProblem(scenarioPackage);
        String revelationId = scenarioPackage.scenarioModel().revelations().stream()
                .findFirst().map(ScenarioModelElement::elementId).orElse("first-stage-purpose");
        List<ScenarioSourceReference> evidence = request.evidence();
        return new DetailedStage(request.scenarioPackageId(), request.backboneRevision(), request.stageId(), 1,
                coreProblem, List.of(new RevelationDefinition(revelationId, true, evidence)),
                new ThreatDefinition(coreProblem, evidence), new PressureDefinition("pressure", coreProblem, evidence),
                new FunnelDefinition("funnel", funnelMeaning(scenarioPackage), List.of(revelationId), List.of(), evidence),
                List.of(new SituationDefinition("opening-situation", List.of(StageIntent.REVELATION), List.of(revelationId),
                        List.of(), List.of(), List.of(), List.of(), true, evidence)), List.of(), evidence);
    }

    private ScenarioPackage loadPackage(UUID scenarioPackageId) {
        return packages.findById(scenarioPackageId).orElseThrow(() -> new IllegalStateException("scenario package not found"));
    }

    private static String coreProblem(ScenarioPackage scenarioPackage) {
        return text(scenarioPackage.scenarioModel().objectives(), scenarioPackage.scenarioModel().startingSituation());
    }

    private static String funnelMeaning(ScenarioPackage scenarioPackage) {
        return text(scenarioPackage.scenarioModel().resolutionCriteria(), coreProblem(scenarioPackage));
    }

    private static String text(List<ScenarioModelElement> elements, String fallback) {
        return elements.stream().findFirst().map(element -> String.valueOf(element.attributes().getOrDefault("value",
                element.attributes().getOrDefault("description", element.elementId())))).filter(value -> !value.isBlank())
                .orElse(fallback == null || fallback.isBlank() ? "모험을 계속 진행한다" : fallback);
    }

    private static Stream<ScenarioModelElement> elements(ScenarioPackage scenarioPackage) {
        var model = scenarioPackage.scenarioModel();
        if (model == null) return Stream.empty();
        return Stream.of(model.actors(), model.locations(), model.objectives(), model.revelations(), model.encounters(),
                model.relationships(), model.resolutionCriteria()).flatMap(List::stream);
    }
}
