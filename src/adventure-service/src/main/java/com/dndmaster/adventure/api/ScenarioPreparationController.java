package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.scenario.preparation.PlayPreparationView;
import com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionsView;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioPreparationApplicationService;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ScenarioPreparationController {
    private final ScenarioPreparationApplicationService service;
    private final AuthenticatedPlayerResolver playerResolver;

    public ScenarioPreparationController(
            ScenarioPreparationApplicationService service,
            AuthenticatedPlayerResolver playerResolver) {
        this.service = service;
        this.playerResolver = playerResolver;
    }

    @GetMapping("/scenario-packages/{scenarioPackageId}/play-preparation")
    PlayPreparationView readPlayPreparation(@PathVariable UUID scenarioPackageId) {
        return service.read(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()));
    }

    @PostMapping({
            "/scenario-packages/{scenarioPackageId}/character-blueprint/draft",
            "/scenario-packages/{scenarioPackageId}/character-blueprint"})
    com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView generateBlueprintDraft(
            @PathVariable UUID scenarioPackageId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "DND_5E_2014") String edition,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID catalogRulebookId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") long catalogExtractionVersion) {
        if (catalogRulebookId != null) {
            return service.generateBlueprintDraft(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()), edition,
                    catalogRulebookId, catalogExtractionVersion);
        }
        return service.generateBlueprintDraft(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()), edition);
    }

    @GetMapping("/runtime-options")
    RuntimeOptionsView readRuntimeOptions() {
        return service.runtimeOptions(new OwnerPlayerId(playerResolver.playerId()));
    }

    @PostMapping("/scenario-packages/{scenarioPackageId}/character-blueprint/resolve")
    com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint resolveBlueprint(
            @PathVariable UUID scenarioPackageId, @RequestBody BlueprintResolutionRequest request) {
        return service.resolveBlueprint(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()),
                request.expectedRevision(), request.fieldKey(), request.value());
    }

    @PostMapping("/scenario-packages/{scenarioPackageId}/character-blueprint/children")
    com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint addBlueprintChild(
            @PathVariable UUID scenarioPackageId, @RequestBody AddChildRequest request) {
        return service.addBlueprintChild(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()),
                request.expectedRevision(), request.parentId(), request.key(), request.label());
    }

    @PostMapping("/scenario-packages/{scenarioPackageId}/character-blueprint/options")
    com.dndmaster.adventure.domain.scenario.CharacterCreationBlueprint addBlueprintOption(
            @PathVariable UUID scenarioPackageId, @RequestBody AddOptionRequest request) {
        return service.addBlueprintOption(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()),
                request.expectedRevision(), request.fieldKey(), request.option());
    }

    @PostMapping("/scenario-packages/{scenarioPackageId}/character-blueprint/proposals/{proposalId}/use")
    com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView useStorybookProposal(
            @PathVariable UUID scenarioPackageId, @PathVariable String proposalId,
            @RequestBody ProposalDecisionRequest request) {
        return service.useStorybookProposal(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()),
                request.expectedRevision(), proposalId);
    }

    @PostMapping("/scenario-packages/{scenarioPackageId}/character-blueprint/proposals/{proposalId}/exclude")
    com.dndmaster.adventure.application.scenario.preparation.CharacterCreationBlueprintView excludeStorybookProposal(
            @PathVariable UUID scenarioPackageId, @PathVariable String proposalId,
            @RequestBody ProposalDecisionRequest request) {
        return service.excludeStorybookProposal(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()),
                request.expectedRevision(), proposalId);
    }

    @PostMapping("/scenario-packages/{scenarioPackageId}/character-blueprint/publish")
    com.dndmaster.adventure.application.scenario.preparation.BlueprintPublicationResult publishBlueprint(@PathVariable UUID scenarioPackageId) {
        return service.publishBlueprint(scenarioPackageId, new OwnerPlayerId(playerResolver.playerId()));
    }

    record BlueprintResolutionRequest(long expectedRevision, String fieldKey, String value) {}
    record AddChildRequest(long expectedRevision, String parentId, String key, String label) {}
    record AddOptionRequest(long expectedRevision, String fieldKey, String option) {}
    record ProposalDecisionRequest(long expectedRevision) {}
}
