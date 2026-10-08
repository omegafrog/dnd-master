package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommitGate;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Ensures a prepared map accepts the party's entry position before turn persistence. */
public final class PreparedMapEntryCommitGate implements RuntimeTurnCommitGate {
    private final CombatMapViewPort mapView;
    private final CombatMapPreparationPort mapPreparation;

    public PreparedMapEntryCommitGate(CombatMapViewPort mapView, CombatMapPreparationPort mapPreparation) {
        this.mapView = Objects.requireNonNull(mapView);
        this.mapPreparation = Objects.requireNonNull(mapPreparation);
    }

    @Override
    public void beforeCommit(Adventure adventure, RuntimeTurn turn) {
        String destinationScene = adventure.currentContext().currentScene();
        boolean sceneChanged = turn != null && !blank(destinationScene).isBlank()
                && !destinationScene.equalsIgnoreCase(blank(turn.context().currentScene()));
        boolean enteredMap = turn != null && (turn.plan().mapEntryRequested() || turn.plan().combatStartRequested());
        if ((!enteredMap && !sceneChanged)
                || !mapView.hasPreparedMap(adventure.id().value(), adventure.ownerPlayerId().value())) return;
        var situation = adventure.currentSituation();
        UUID playerTokenId = adventure.party().stream().findFirst()
                .map(AdventurePartyMember::characterSheetId)
                .map(sheet -> UUID.nameUUIDFromBytes(("player-" + sheet.value()).getBytes(StandardCharsets.UTF_8)))
                .orElse(null);
        String firstNarration = situation.firstNarration();
        StringBuilder evidence = new StringBuilder("FIRST_NARRATION=").append(blank(firstNarration));
        if (turn != null) {
            if (blank(firstNarration).isBlank()) evidence.replace("FIRST_NARRATION=".length(), evidence.length(), blank(turn.narration()));
            evidence.append("\nPLAYER_ACTION=").append(blank(turn.action()))
                    .append("\nGM_JUDGMENT=").append(blank(turn.plan().judgment()))
                    .append("\nGM_NARRATION=").append(blank(turn.narration()));
            turn.plan().citedEvidence().stream()
                    .filter(item -> item.evidenceType() == RuntimeEvidenceType.STORYBOOK)
                    .map(com.dndmaster.adventure.application.runtime.RuntimeEvidence::excerpt)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .forEach(value -> evidence.append("\nSTORYBOOK_EVIDENCE=").append(value));
        }
        String location = sceneChanged && !enteredMap ? destinationScene : situation.location();
        var context = new CombatMapPreparationPort.ActivationContext(playerTokenId, situation.situationId(),
                situation.revision(), adventure.turnIndex(), destinationScene, location, null, null, evidence.toString());
        mapPreparation.activatePrepared(adventure.id(), adventure.ownerPlayerId().value(), adventure.ruleSetId(), 1, context);
    }

    private static String blank(String value) { return value == null ? "" : value; }
}
