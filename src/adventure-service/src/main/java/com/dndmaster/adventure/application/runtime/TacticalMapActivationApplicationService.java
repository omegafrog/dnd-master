package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TacticalMapActivationApplicationService {
    private final ScenarioPackageRepository packages;
    private final TacticalMapActivationPolicy policy;
    private final TacticalMapPreparationPort preparation;

    public TacticalMapActivationApplicationService(ScenarioPackageRepository packages,
            TacticalMapPreparationPort preparation) {
        this.packages = Objects.requireNonNull(packages, "package repository must not be null");
        this.policy = new TacticalMapActivationPolicy();
        this.preparation = Objects.requireNonNull(preparation, "map preparation port must not be null");
    }

    public Activation activate(UUID packageId, UUID adventureId, UUID ownerPlayerId,
            String stage, String location, String condition) {
        var scenarioPackage = packages.findById(packageId).orElseThrow(() -> new IllegalArgumentException("scenario package not found"));
        var decision = policy.decide(scenarioPackage, stage, location, condition);
        if (decision.map().isEmpty()) return new Activation(Optional.empty(), decision.textFallback());
        MapDefinition definition = decision.map().orElseThrow();
        return new Activation(Optional.of(preparation.prepare(adventureId, ownerPlayerId, definition)), false);
    }

    public Activation activateDefinition(UUID packageId, UUID adventureId, UUID ownerPlayerId, UUID mapDefinitionId) {
        return activateDefinition(packageId, adventureId, ownerPlayerId, null, mapDefinitionId);
    }

    public Activation activateDefinition(UUID packageId, UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, UUID mapDefinitionId) {
        return activateDefinition(packageId, adventureId, ownerPlayerId, ruleSetId, mapDefinitionId, 0, 0);
    }

    public Activation activateDefinition(UUID packageId, UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, UUID mapDefinitionId,
            int playerSpawnX, int playerSpawnY) {
        return activateDefinition(packageId, adventureId, ownerPlayerId, ruleSetId, mapDefinitionId, null, playerSpawnX, playerSpawnY);
    }

    public Activation activateDefinition(UUID packageId, UUID adventureId, UUID ownerPlayerId, UUID ruleSetId, UUID mapDefinitionId,
            TacticalScenePlan tacticalScenePlan, int playerSpawnX, int playerSpawnY) {
        if (tacticalScenePlan != null && !tacticalScenePlan.readyForActivation()) {
            throw new IllegalStateException("tactical scene plan must be ready before activation");
        }
        var scenarioPackage = packages.findById(packageId).orElseThrow(() -> new IllegalArgumentException("scenario package not found"));
        MapDefinition definition = scenarioPackage.mapDefinitions().stream().filter(map -> map.id().equals(mapDefinitionId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("map definition not found in scenario package"));
        if (!definition.autoActivatable()) throw new IllegalStateException("map definition is not safe to activate");
        return new Activation(Optional.of(tacticalScenePlan != null
                ? preparation.prepare(adventureId, ownerPlayerId, ruleSetId, definition, tacticalScenePlan, playerSpawnX, playerSpawnY)
                : ruleSetId == null
                        ? preparation.prepare(adventureId, ownerPlayerId, definition)
                        : preparation.prepare(adventureId, ownerPlayerId, ruleSetId, definition, playerSpawnX, playerSpawnY)), false);
    }

    public record Activation(Optional<UUID> combatMapId, boolean textFallback) {
        public Activation { combatMapId = Objects.requireNonNull(combatMapId, "combat map id must not be null"); }
    }
}
