package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.TurnResources;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.Objects;

/** Adventure Runtime authorization that reserves and commits a spatial action resource. */
public final class CombatEncounterSpatialActionAuthorization implements SpatialActionAuthorizationPort {
    private final CombatEncounterRepository encounters;
    private final CombatActionOperationRepository operations;

    public CombatEncounterSpatialActionAuthorization(CombatEncounterRepository encounters,
            CombatActionOperationRepository operations) {
        this.encounters = Objects.requireNonNull(encounters);
        this.operations = Objects.requireNonNull(operations);
    }

    @Override
    public void authorize(SpatialActionAuthorization command) {
        if (!command.action().equals("OBSERVE") && !command.action().equals("INTERACT")) {
            throw new IllegalArgumentException("unsupported spatial action");
        }
        if (!command.cost().equals(TurnResourceCost.actionOnly())) {
            throw new IllegalStateException("spatial action cost is not authorized");
        }
        CombatActionOperation existing = operations.findByCommandId(command.commandId()).orElse(null);
        if (existing != null) {
            existing.requireSame(command.fingerprint());
            if (existing.status() == CombatActionOperation.Status.COMMITTED) return;
        }
        CombatEncounter encounter = encounters.findActive(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("ACTIVE_COMBAT_REQUIRED"));
        if (encounter.status() != CombatEncounter.Status.ACTIVE) {
            throw new IllegalStateException("ACTIVE_COMBAT_REQUIRED");
        }
        if (encounter.currentParticipant().controller()
                != com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.PLAYER) {
            throw new IllegalStateException("PLAYER_TURN_REQUIRED");
        }
        if (existing == null && operations.hasPendingForEncounter(encounter.encounterId())) {
            throw new IllegalStateException("ACTION_ALREADY_IN_PROGRESS");
        }
        TurnResources.Reservation reservation = encounter.reserveAction(command.actorId(), command.cost(), encounter.version());
        CombatEncounter committed = encounter.commitAction(command.actorId(), reservation);
        encounters.save(committed, encounter.version());

        CombatActionOperation operation = existing == null
                ? new CombatActionOperation(command.commandId(), command.fingerprint(), encounter.encounterId(),
                        command.actorId(), command.cost(), List.of(new CombatActionStep(
                                "spatial", command.commandId() + ":spatial", CombatActionStep.Status.PENDING)))
                : existing;
        operation.completeStep("spatial");
        operation.committed(new CombatActionResponse(encounter.encounterId(), command.commandId(), committed.version(),
                "SPATIAL_ACTION_COMMITTED", null, command.action(), List.of()));
        operations.save(operation);
    }
}
