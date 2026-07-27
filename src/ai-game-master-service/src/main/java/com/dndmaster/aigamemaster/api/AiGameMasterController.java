package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.application.ports.AdjudicationModelPort;
import com.dndmaster.aigamemaster.application.ports.MapModelPort;
import com.dndmaster.aigamemaster.application.intent.IntentClassificationModelPort;
import com.dndmaster.aigamemaster.application.rule.*;
import com.dndmaster.aigamemaster.application.scene.NpcOutput;
import com.dndmaster.aigamemaster.application.scene.ScenarioBoundSceneService;
import com.dndmaster.aigamemaster.application.scene.ScenarioRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping
public class AiGameMasterController {
    private final ScenarioBoundSceneService sceneService;
    private final AdjudicationModelPort adjudicationPort;
    private final GroundedRuleAnswerService ruleAnswerService;
    private final MapModelPort mapPort;
    private final IntentClassificationModelPort intentClassificationPort;

    public AiGameMasterController(
            ScenarioBoundSceneService sceneService,
            AdjudicationModelPort adjudicationPort,
            GroundedRuleAnswerService ruleAnswerService,
            MapModelPort mapPort,
            IntentClassificationModelPort intentClassificationPort) {
        this.sceneService = sceneService;
        this.adjudicationPort = adjudicationPort;
        this.ruleAnswerService = ruleAnswerService;
        this.mapPort = mapPort;
        this.intentClassificationPort = intentClassificationPort;
    }

    @PostMapping("/internal/v1/gm/scenes")
    SceneResponse generateScene(@RequestBody SceneRequest request) {
        List<SourceEvidence> evidence = request.evidence().stream()
                .map(e -> new SourceEvidence(e.rulebookId(), e.locator(), e.excerpt()))
                .toList();
        ScenarioRequest scenarioRequest = new ScenarioRequest(
                request.scenarioId(), request.selectedScenario(),
                request.currentContext(), request.ruleSetId(), evidence);
        var output = sceneService.generate(scenarioRequest);
        return new SceneResponse(
                output.scenarioId(), output.ruleSetId(),
                output.scene(), output.npcs(), output.alignment().name());
    }

    @PostMapping("/internal/v1/gm/judgments")
    JudgmentResponse adjudicate(@RequestBody JudgmentRequest request) {
        var input = new AdjudicationModelPort.AdjudicationInput(
                request.action(), request.context(), request.ruleSetId());
        var output = adjudicationPort.adjudicate(input);
        return new JudgmentResponse(output.outcome(), output.ruleBasis());
    }

    @PostMapping("/internal/v1/gm/rule-answers")
    RuleAnswerResponse groundedAnswer(@RequestBody RuleAnswerHttpRequest request) {
        EvidenceStatus status = EvidenceStatus.valueOf(request.evidenceStatus());
        List<SourceEvidence> evidence = request.evidence().stream()
                .map(e -> new SourceEvidence(e.rulebookId(), e.locator(), e.excerpt()))
                .toList();
        RuleAnswerRequest ruleRequest = new RuleAnswerRequest(
                request.ruleSetId(), request.situation(), status, evidence);
        RuleAnswerOutput output = ruleAnswerService.compose(ruleRequest);
        return new RuleAnswerResponse(
                output.conclusion(), output.conclusionCitations(),
                output.candidates(), output.evidenceStatus().name(),
                output.uncertaintyDisclosed());
    }

    @PostMapping("/internal/v1/gm/intent-classifications")
    IntentClassificationResponse classifyIntent(@RequestBody IntentClassificationRequest request) {
        var output = intentClassificationPort.classify(
                new IntentClassificationModelPort.IntentClassificationInput(request.question()));
        return new IntentClassificationResponse(output.intent().name());
    }

    @PostMapping("/internal/v1/gm/maps")
    MapResponse generateMap(@RequestBody MapRequest request) {
        var input = new MapModelPort.MapInput(request.selectedScenario(), request.currentContext());
        var output = mapPort.generate(input);
        return new MapResponse(output.width(), output.height(), output.structuredLayers());
    }

    @PostMapping("/internal/v1/gm/agent-actions")
    AgentActionResponse proposeAgentAction(@RequestBody AgentActionRequest request) {
        if (request == null || request.characterSheetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "character sheet id required");
        }
        String action = request.currentScene() == null || request.currentScene().isBlank()
                ? "Observe the current scene" : "Act from the current scene";
        return new AgentActionResponse(UUID.randomUUID(), UUID.randomUUID(), action);
    }

    public record SceneRequest(
            UUID scenarioId, String selectedScenario, String currentContext,
            UUID ruleSetId, List<EvidenceRef> evidence) {}

    public record EvidenceRef(UUID rulebookId, String locator, String excerpt) {}

    public record SceneResponse(
            UUID scenarioId, UUID ruleSetId,
            String scene, List<NpcOutput> npcs, String alignment) {}

    public record JudgmentRequest(String action, String context, String ruleSetId) {}

    public record JudgmentResponse(String outcome, String ruleBasis) {}

    public record RuleAnswerHttpRequest(
            UUID ruleSetId, String situation, String evidenceStatus,
            List<EvidenceRef> evidence) {}

    public record RuleAnswerResponse(
            String conclusion, List<Citation> conclusionCitations,
            List<RuleCandidate> candidates, String evidenceStatus,
            boolean uncertaintyDisclosed) {}

    public record IntentClassificationRequest(String question) {}

    public record IntentClassificationResponse(String queryIntent) {}

    public record MapRequest(String selectedScenario, String currentContext) {}

    public record MapResponse(int width, int height, String structuredLayers) {}

    public record AgentActionRequest(UUID adventureId, UUID ownerPlayerId, UUID characterSheetId,
                                     String characterName, int level, String currentScene) {}

    public record AgentActionResponse(UUID turnId, UUID commandId, String action) {}
}
