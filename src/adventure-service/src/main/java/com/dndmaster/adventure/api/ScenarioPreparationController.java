package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.scenario.preparation.PlayPreparationView;
import com.dndmaster.adventure.application.scenario.preparation.RuntimeOptionsView;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioPreparationApplicationService;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/runtime-options")
    RuntimeOptionsView readRuntimeOptions() {
        return service.runtimeOptions(new OwnerPlayerId(playerResolver.playerId()));
    }
}
