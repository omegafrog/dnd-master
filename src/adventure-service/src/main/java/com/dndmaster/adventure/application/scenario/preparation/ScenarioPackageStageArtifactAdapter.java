package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationProvenance;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import com.dndmaster.adventure.application.runtime.GmProviderBindingRepository;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Adapts the published Scenario Package into the minimum first-stage artifact request. */
public final class ScenarioPackageStageArtifactAdapter implements StorybookEvidenceLookupPort,
        StageBackboneGenerationPort, StageDetailedGenerationPort {
    private static final String FIRST_STAGE_ID = "first-stage";
    private final ScenarioPackageRepository packages;
    private final InitialSituationGmPort gm;
    private final GmProviderBindingRepository bindings;
    private final AdventureSessionRepository sessions;
    private final ObjectMapper mapper;

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages) {
        this(packages, null, null, null, null);
    }

    public ScenarioPackageStageArtifactAdapter(ScenarioPackageRepository packages, InitialSituationGmPort gm,
            GmProviderBindingRepository bindings, AdventureSessionRepository sessions, ObjectMapper mapper) {
        this.packages = java.util.Objects.requireNonNull(packages, "scenario package repository must not be null");
        this.gm = gm;
        this.bindings = bindings;
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Override
    public List<ScenarioSourceReference> lookup(UUID scenarioPackageId) {
        return elements(loadPackage(scenarioPackageId)).flatMap(element -> element.sourceRefs().stream()).distinct().toList();
    }

    @Override
    public boolean isScenarioBacked(UUID scenarioPackageId) {
        return !lookup(scenarioPackageId).isEmpty();
    }

    @Override
    public java.util.Set<UUID> providedMapIds(UUID scenarioPackageId) {
        return loadPackage(scenarioPackageId).mapDefinitions().stream().map(com.dndmaster.adventure.domain.scenario.MapDefinition::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public StageBackbone generate(StageBackboneGenerationPort.Request request) {
        if (gm != null) {
            ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
            JsonNode generated = generate(request.sessionId(), request.adventureId().value(),
                    request.scenarioPackageId(), "backbone", backbonePrompt(scenarioPackage, request.evidence()));
            List<StageBackboneEntry> stages = mapper.convertValue(generated.path("stages"),
                    mapper.getTypeFactory().constructCollectionType(List.class, StageBackboneEntry.class));
            List<ScenarioSourceReference> refs = references(generated.path("sourceRefs"));
            return new StageBackbone(request.scenarioPackageId(), 1, stages, refs);
        }
        ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
        String coreProblem = openingProblem(scenarioPackage);
        String funnel = openingGoal(scenarioPackage);
        return new StageBackbone(request.scenarioPackageId(), 1,
                List.of(new StageBackboneEntry(FIRST_STAGE_ID, 1, "opening", coreProblem, funnel, request.evidence())),
                request.evidence());
    }

    @Override
    public DetailedStage generate(StageDetailedGenerationPort.Request request) {
        if (gm != null) {
            ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
            JsonNode generated = generate(request.sessionId(), request.adventureId().value(),
                    request.scenarioPackageId(), "stage:" + request.stageId(),
                    detailedPrompt(scenarioPackage, request.stageId(), request.evidence()));
            var content = generated.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("scenarioPackageId", request.scenarioPackageId().toString());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("backboneRevision", request.backboneRevision());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("stageId", request.stageId());
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("revision", 1);
            return mapper.convertValue(content, DetailedStage.class);
        }
        ScenarioPackage scenarioPackage = loadPackage(request.scenarioPackageId());
        String coreProblem = openingProblem(scenarioPackage);
        String revelationId = scenarioPackage.scenarioModel().revelations().stream()
                .findFirst().map(ScenarioModelElement::elementId).orElse("first-stage-purpose");
        List<ScenarioSourceReference> evidence = request.evidence();
        return new DetailedStage(request.scenarioPackageId(), request.backboneRevision(), request.stageId(), 1,
                coreProblem, List.of(new RevelationDefinition(revelationId, true, evidence)),
                new ThreatDefinition(openingThreat(scenarioPackage), evidence),
                new PressureDefinition("pressure", openingGoal(scenarioPackage), evidence),
                new FunnelDefinition("funnel", openingGoal(scenarioPackage), List.of(revelationId), List.of(), evidence),
                List.of(new SituationDefinition("opening-situation", List.of(StageIntent.REVELATION), List.of(revelationId),
                        List.of(), List.of(), List.of(), List.of(), true, evidence),
                        fallbackCandidate("candidate-a", revelationId, evidence),
                        fallbackCandidate("candidate-b", revelationId, evidence)), List.of(), evidence);
    }

    private SituationDefinition fallbackCandidate(String id, String revelationId, List<ScenarioSourceReference> evidence) {
        return new SituationDefinition(id, List.of(StageIntent.REVELATION), List.of(revelationId),
                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                evidence.isEmpty() ? SituationProvenance.GM_SUPPLEMENTED : SituationProvenance.SOURCE_EXTRACTED,
                null, evidence);
    }

    private JsonNode generate(SessionId sessionId, UUID adventureId, UUID packageId, String task, String taskPrompt) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("adventure session not found"));
        var binding = bindings.current(sessionId.value()).orElseThrow(() -> new IllegalStateException("GM provider selection not found"));
        String prompt = "ROLE=INITIAL_SITUATION_PREPARATION\nTASK=" + task + "\n" + taskPrompt
                + "\nOUTPUT_CONTRACT=Return exactly one JSON object matching the requested fields. No markdown.";
        return gm.generate(new InitialSituationGmPort.Request(session.ownerPlayerId().value(), sessionId.value(),
                binding.selection().endpointId(), binding.selection().provider(), binding.selection().model(),
                binding.selection().reasoning(), "initial-situation:" + adventureId + ":" + packageId + ":" + task,
                prompt));
    }

    private String backbonePrompt(ScenarioPackage scenarioPackage, List<ScenarioSourceReference> evidence) {
        return "Create the adventure story backbone from this compiled story model and source evidence. "
                + "Include every story-essential required situation and only prerequisites explicit in the source. "
                + "Add as many intermediate candidate stages as the story needs; do not use a fixed count or impose unstated order. "
                + "Each stage needs stageId, contiguous order, role, coreProblem, funnelSummary, and sourceRefs. "
                + "Return stages and sourceRefs.\nSCENARIO_MODEL=" + json(scenarioPackage.scenarioModel())
                + "\nSOURCE_QUOTES=" + sourceQuotes(scenarioPackage) + "\nALLOWED_SOURCE_REFS=" + json(evidence);
    }

    private String detailedPrompt(ScenarioPackage scenarioPackage, String stageId, List<ScenarioSourceReference> evidence) {
        return "Prepare the situation definitions and supporting details for stage " + stageId + " from this compiled story model. "
                + "Include required situations needed to preserve the source story and multiple intermediate candidates when supported by the story. "
                + "Do not force a fixed candidate count, add unstated prerequisite relations, or make optional ingredients mandatory. "
                + "For every situation include required, prerequisiteSituationIds, provenance (SOURCE_EXTRACTED or GM_SUPPLEMENTED), "
                + "sourceRefs (exact allowed references for extracted claims; empty for GM supplements), and mapDefinitionId "
                + "(one supplied map UUID or null). Return a complete DetailedStage JSON object except package/revision/stage identifiers. "
                + "Use only these map IDs: " + json(scenarioPackage.mapDefinitions().stream().map(m -> m.id()).toList())
                + "\nSCENARIO_MODEL=" + json(scenarioPackage.scenarioModel()) + "\nSOURCE_QUOTES=" + sourceQuotes(scenarioPackage)
                + "\nALLOWED_SOURCE_REFS=" + json(evidence);
    }

    private List<ScenarioSourceReference> references(JsonNode node) {
        return mapper.convertValue(node, mapper.getTypeFactory().constructCollectionType(List.class, ScenarioSourceReference.class));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("initial situation prompt serialization failed", failure); }
    }

    private static List<String> sourceQuotes(ScenarioPackage scenarioPackage) {
        return scenarioPackage.units().stream().filter(unit -> !unit.sourceQuote().isBlank())
                .map(unit -> unit.sourceQuote() + " [" + unit.sourceRefs() + "]").distinct().toList();
    }

    private ScenarioPackage loadPackage(UUID scenarioPackageId) {
        return packages.findById(scenarioPackageId).orElseThrow(() -> new IllegalStateException("scenario package not found"));
    }

    private static String openingProblem(ScenarioPackage scenarioPackage) {
        return text(scenarioPackage.scenarioModel().objectives(), scenarioPackage.scenarioModel().startingSituation());
    }

    private static String openingThreat(ScenarioPackage scenarioPackage) {
        var objectives = scenarioPackage.scenarioModel().objectives();
        var objectiveRefs = objectives.isEmpty() ? List.<ScenarioSourceReference>of() : objectives.get(0).sourceRefs();
        return scenarioPackage.scenarioModel().combatScenarios().stream()
                .sorted(java.util.Comparator.comparing((com.dndmaster.adventure.domain.scenario.CombatScenarioDefinition encounter) ->
                        encounter.sourceRefs().stream().noneMatch(objectiveRefs::contains)))
                .findFirst()
                .map(encounter -> "시작 장면과 연결된 위협: " + encounter.displayName() + " (" + encounter.location() + ")")
                .orElse("시작 장면의 문제와 관련된 위협은 아직 확인되지 않았다");
    }

    private static String openingGoal(ScenarioPackage scenarioPackage) {
        boolean hasQuestGiver = scenarioPackage.scenarioModel().actors().stream().anyMatch(actor ->
                actor.type().equalsIgnoreCase("quest-giver")
                        || String.valueOf(actor.attributes().getOrDefault("role", "")).equalsIgnoreCase("quest-giver"));
        return hasQuestGiver
                ? "플레이어에게 당면한 의뢰가 제시되고, 수락 여부와 다음 행동은 플레이어가 정한다"
                : "플레이어는 현재 장면의 문제와 위협을 파악하고, 다음 행동을 직접 정한다";
    }

    private static String text(List<ScenarioModelElement> elements, String fallback) {
        return elements.stream().findFirst().map(element -> String.valueOf(element.attributes().getOrDefault("value",
                element.attributes().getOrDefault("description", element.elementId())))).filter(value -> !value.isBlank())
                .orElse(fallback == null || fallback.isBlank() ? "모험의 시작 장면에서 해결할 문제가 아직 드러나지 않았다" : fallback);
    }

    private static Stream<ScenarioModelElement> elements(ScenarioPackage scenarioPackage) {
        var model = scenarioPackage.scenarioModel();
        if (model == null) return Stream.empty();
        return Stream.of(model.actors(), model.locations(), model.objectives(), model.revelations(), model.encounters(),
                model.relationships(), model.resolutionCriteria()).flatMap(List::stream);
    }
}
