package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.combat.CombatAimPreviewPolicy;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Rechecks a map-selected ranged attack against the locked definition and current server-owned state. */
public final class CombatRangedAttackValidator {
    private final CombatEncounterRepository encounters;
    private final ScenarioPackageRepository packages;
    private final CombatMapViewPort maps;

    public CombatRangedAttackValidator(CombatEncounterRepository encounters, ScenarioPackageRepository packages,
            CombatMapViewPort maps) {
        this.encounters = encounters;
        this.packages = packages;
        this.maps = maps;
    }

    public void validate(Adventure adventure, UUID ownerId, UUID actorId, long expectedEncounterVersion,
            UUID actorTokenId, UUID targetParticipantId, int targetX, int targetY, String definitionId,
            long definitionVersion, long expectedMapVersion) {
        CombatEncounter encounter = encounters.findActive(adventure.id().value())
                .orElseThrow(() -> conflict("COMBAT_NOT_ACTIVE"));
        if (encounter.version() != expectedEncounterVersion) throw conflict("ENCOUNTER_VERSION_CHANGED expected="
                + expectedEncounterVersion + " actual=" + encounter.version());
        if (!encounter.currentParticipantId().equals(actorId)
                || encounter.currentParticipant().controller() != com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.PLAYER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AIM_TOKEN_NOT_CURRENT_ACTOR");
        }

        var review = packages.findSpellDefinitionPreparationBySession(adventure.sessionId().value())
                .filter(value -> value.locked() && value.ownerId().equals(ownerId))
                .orElseThrow(() -> conflict("ACTION_DEFINITIONS_NOT_LOCKED"));
        if (review.revision() != definitionVersion) throw conflict("DEFINITION_VERSION_CHANGED expected="
                + definitionVersion + " actual=" + review.revision());
        StructuredSpellDefinition definition = review.definitions().stream()
                .filter(value -> value.id().equals(definitionId) && value.active()
                        && value.reviewStatus() == StructuredSpellDefinition.ReviewStatus.APPROVED
                        && value.actionKind() == StructuredSpellDefinition.ActionKind.RANGED_ATTACK
                        && value.rangeProfile() != null && value.rangeProfile().supported()
                        && value.targetingProfile() != null && value.targetingProfile().supported())
                .findFirst().orElseThrow(() -> conflict("ACTION_DEFINITION_UNAVAILABLE"));

        var member = adventure.party().stream().filter(value -> value.characterSheetId().value().equals(actorId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "AIM_ACTOR_NOT_IN_PARTY"));
        var view = maps.playerView(adventure.id().value(), ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMBAT_MAP_NOT_FOUND"));
        if (view.version() != expectedMapVersion) throw conflict("MAP_VERSION_CHANGED expected="
                + expectedMapVersion + " actual=" + view.version());
        if (view.grid() == null) throw conflict("MAP_GRID_UNAVAILABLE");
        var mappedMember = CombatMapPlayerTokenResolver.resolve(adventure, ownerId, actorTokenId, maps);
        if (!mappedMember.characterSheetId().equals(member.characterSheetId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AIM_TOKEN_NOT_CURRENT_ACTOR");
        }
        var actorToken = view.tokens().stream().filter(value -> value.id().equals(actorTokenId)
                && "PLAYER".equals(value.type())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "AIM_TOKEN_NOT_PUBLIC"));

        var target = encounter.participants().stream().filter(value -> value.participantId().equals(targetParticipantId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "TARGET_NOT_IN_ENCOUNTER"));
        if (target.controller() != com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI
                || target.statBlock() == null || target.isDefeated()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RANGED_TARGET_NOT_ATTACKABLE");
        }
        CombatAimPreviewPolicy.Cell targetCell = new CombatAimPreviewPolicy.Cell(targetX, targetY);
        if (!currentlyVisible(view, targetCell)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TARGET_CELL_NOT_PUBLIC");
        List<CombatMapViewPort.Token> targetTokens = view.tokens().stream()
                .filter(value -> value.x() == targetX && value.y() == targetY)
                .filter(value -> "ENEMY".equals(value.type()) || "BOSS".equals(value.type()))
                .toList();
        if (targetTokens.size() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TARGET_CELL_PARTICIPANT_MISMATCH");
        long activeEnemyParticipants = encounter.participants().stream()
                .filter(value -> value.controller() == com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI
                        && value.statBlock() != null && !value.isDefeated()).count();
        long visibleEnemyTokens = view.tokens().stream()
                .filter(value -> "ENEMY".equals(value.type()) || "BOSS".equals(value.type())).count();
        boolean uniqueTargetAssociation = activeEnemyParticipants == 1 && visibleEnemyTokens == 1;
        boolean directTargetAssociation = targetTokens.getFirst().id().equals(targetParticipantId);
        if (!uniqueTargetAssociation && !directTargetAssociation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TARGET_CELL_PARTICIPANT_MISMATCH");
        }

        var preview = CombatAimPreviewPolicy.preview(definition,
                new CombatAimPreviewPolicy.Cell(actorToken.x(), actorToken.y()), actorToken.elevationFeet(),
                targetCell, targetTokens.getFirst().elevationFeet(), view.grid().distanceUnit(), view.grid().width(),
                view.grid().height(), blockers(view), boundaries(view));
        if (!preview.supported()) throw conflict("AIM_DEFINITION_UNSUPPORTED");
        if (!preview.inRange()) throw new ResponseStatusException(HttpStatus.CONFLICT, "AIM_OUT_OF_RANGE");
        if (!preview.clearPath()) throw new ResponseStatusException(HttpStatus.CONFLICT, "AIM_PATH_BLOCKED");
    }

    private static boolean currentlyVisible(CombatMapViewPort.View view, CombatAimPreviewPolicy.Cell target) {
        return view.current() != null && view.current().stream()
                .anyMatch(cell -> cell.x() == target.x() && cell.y() == target.y());
    }

    private static List<CombatAimPreviewPolicy.Cell> blockers(CombatMapViewPort.View view) {
        List<CombatAimPreviewPolicy.Cell> result = new ArrayList<>();
        if (view.obstacles() != null) view.obstacles().stream()
                .map(cell -> new CombatAimPreviewPolicy.Cell(cell.x(), cell.y())).forEach(result::add);
        if (view.doors() != null) view.doors().stream().filter(door -> !door.open())
                .map(door -> new CombatAimPreviewPolicy.Cell(door.x(), door.y())).forEach(result::add);
        return List.copyOf(result);
    }

    private static List<CombatAimPreviewPolicy.Boundary> boundaries(CombatMapViewPort.View view) {
        List<CombatAimPreviewPolicy.Boundary> result = new ArrayList<>();
        if (view.layers() == null) return List.of();
        for (var layer : view.layers()) {
            if (layer == null || !"MAP_BOUNDARIES".equals(layer.type()) || layer.value() == null || layer.value().isBlank()) continue;
            for (String item : layer.value().split(";")) {
                String[] parts = item.split(",");
                if (parts.length != 5) throw conflict("MAP_BOUNDARY_DATA_INVALID");
                String orientation = parts[2].trim().toUpperCase(java.util.Locale.ROOT);
                String kind = parts[3].trim().toUpperCase(java.util.Locale.ROOT);
                if (!(orientation.equals("HORIZONTAL") || orientation.equals("VERTICAL"))
                        || !(kind.equals("WALL") || kind.equals("DOOR"))) throw conflict("MAP_BOUNDARY_DATA_INVALID");
                try {
                    result.add(new CombatAimPreviewPolicy.Boundary(Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()), orientation, kind,
                            Boolean.parseBoolean(parts[4].trim())));
                } catch (RuntimeException failure) { throw conflict("MAP_BOUNDARY_DATA_INVALID"); }
            }
        }
        return List.copyOf(result);
    }

    private static ResponseStatusException conflict(String code) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code);
    }
}
